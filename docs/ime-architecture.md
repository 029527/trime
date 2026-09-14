# 键盘 UI 的输入状态层

键盘 UI 逐步换成 Jetpack Compose 的第一步：给 UI 一个唯一可读的状态快照 `InputState`，
和一组意图方法 `InputSession`。代码在 `app/src/main/java/com/osfans/trime/ime/session/`。

## 数据流

```
librime (C++)
  │  processKey / selectCandidate / setOption ...（都在 rime-main 线程）
  ▼
Rime.kt ── emitResponse() / librime 通知
  │  1. rimeMessageHandlers：同步更新 Rime 自己的缓存字段
  │     (statusCached / compositionCached / schemaCached / hasMenu / paging)
  │  2. messageFlow_.tryEmit(message)
  ▼
Rime.messageFlow   MutableSharedFlow(extraBufferCapacity = 15, DROP_OLDEST)
  │  收集者：RimeDaemon（应用级）、TrimeInputMethodService、InputView、CandidatesView
  ▼
BaseInputView.setupRimeMessageHandler()
  │  service.lifecycleScope.launch { messageFlow.collect { ... } }
  │  → Dispatchers.Main.immediate，每条消息一次主线程派发
  ▼
InputView.handleRimeMessage(message)
  ├─► DefaultInputSession.onRimeMessage / onComposition  → state: StateFlow<InputState>  → Compose UI
  └─► InputBroadcaster.onXxx → 各 InputBroadcastReceiver（老 View UI，暂不改）

TrimeInputMethodService（主线程回调）
  onStartInputView → InputView.startInput        → session.onStartInput + broadcaster
  onUpdateSelection → InputView.updateSelection  → session.onSelection + broadcaster
  replaceInputViews / startInput → InputView.updateEnterKeyLabel → EnterKeyDisplayDelegate → session.onEnterKey
```

session 的每次喂数据都排在老广播之前，老接收者看到的顺序和以前完全一样。

### 线程

- 所有 `DefaultInputSession.on*` 必须在主线程调用。`messageFlow` 由 rime-main 线程
  （以及 `Rime.showAsciiSwitchTips` 里跑在 `Dispatchers.Default` 上的延时任务）发出，
  但收集发生在 `service.lifecycleScope`，即 `Dispatchers.Main.immediate`，与发送线程无关。
  `startInput` / `updateSelection` / `updateEnterKeyLabel` 都来自 `InputMethodService` 的主线程回调。
- `DefaultInputSession` 在每次更新时检查 `Looper.myLooper() == Looper.getMainLooper()`：
  debug 包直接抛异常，release 包只记日志，免得线程错误把日常用的输入法搞崩。
- UI 读 `state` 不受限制（`StateFlow` 线程安全），意图方法内部自己切到 rime 线程。

### 创建时的初始状态

主题、配色、若干设置变化时 `InputView` 整个重建（`TrimeInputMethodService.replaceInputView`），
新的 session 从空状态开始，而 librime 里可能正有输入。`InputView.init` 调用
`session.restoreFromEngine()`：

- 同步读 `Rime` 的缓存：`statusCached`、`compositionCached`、`hasMenu`、`paging`，
  schema 和 options 从 status 推出。`statusCached.schemaId` 为空说明引擎还没回过话，
  这时不猜 schema / options（`StatusProto` 的默认值是 ascii 模式，会误导）。
- `Rime` 没有缓存候选列表。有候选时在 service 的协程里补拉前 16 个
  （`getCandidates(0, 16)`，和 JNI 每次响应里带的 bulk 列表同样长）；拉回来之前如果已经到了
  新的引擎更新，就丢掉这次结果。补拉得到的 `highlighted` 固定为 0。
- `PopupCandidatesMode.ALWAYS_SHOW` 下悬浮候选窗接管了组合和候选，composition / candidates 保持空，不补拉。

注意 `Rime` 的缓存字段不是 `@Volatile`，由 rime-main 写、主线程读，理论上可能读到稍旧的值；
下一次引擎响应会覆盖，老代码（`KeyboardWindow`、`Key`、`InputView.broadcastKeyAppearanceUpdate`）一直这么读。

## 一次按键发出哪些消息

`Rime.processKeyInner` → `processRimeKey`（librime 处理按键）→ `emitResponse()`。

1. **librime 通知**（`rime_jni.cc` 的 `notificationHandler`）在 `processRimeKey` 内部
   **同步**触发（`Service::Notify` 在调用线程里直接回调），所以总在响应之前：
   - `OptionMessage`（`option` 通知，如 Shift 切 ascii_mode）。`Rime` 的处理器会先刷新
     `statusCached`，ascii_mode 时还会先发一条提示用的 `CompositionMessage("En")`，再发 `OptionMessage` 本身。
   - `SchemaMessage`（`schema` 通知，切方案）。
   - `DeployMessage` 来自部署线程，和按键无关。
2. **`emitResponse()`**：一次 JNI 调用 `getRimeResponse(pagingMode)` 取回 commit / context /
   status / 候选，然后按固定顺序发：

   | # | 消息 | 说明 |
   |---|------|------|
   | 1 | `CommitTextMessage` | **总是发**，没有上屏文字时 `text == null` |
   | 2 | `InlinePreeditMessage` | 行内预编辑文本（service 用） |
   | 3 | `CompositionMessage` | 行内预编辑模式 `COMPOSING_TEXT` 下是空 composition |
   | (3') | `CompositionMessage` | 仅当 composition 为空且 ascii 提示文本变了：ascii 提示 |
   | 4 | `BulkCandidatesMessage` 或 `PagedCandidatesMessage` | 非分页模式 bulk（前 16 个），分页模式 paged（整页菜单） |
   | (4') | `SchemaMessage` | `Rime` 处理 status 时发现 schemaId 变了，在 status 之前补发 |
   | 5 | `StatusMessage` | **总是最后一条** |
   | 6 | `KeyMessage` | 仅 `processKey` 且 librime 没处理该键时，在 status 之后 |

   `selectCandidate`、`deleteCandidate`、`changeCandidatePage`、`moveCursorPos`、
   `commitComposition`、`clearComposition`、`setCandidatePagingMode`、`simulateKeySequence`
   都走同一个 `emitResponse()`，顺序相同。
3. **响应之外**单独出现的消息：
   - `setRuntimeOption()`（如候选栏上的中英切换按钮）只有 `OptionMessage`，**没有** status。
   - ascii 提示 1 秒后的恢复：单独一条 `CompositionMessage`，从 `Dispatchers.Default` 发出。

分页模式 = `InputDeviceManager.useCandidatesView`：物理键盘，或 `ALWAYS_SHOW`。
虚拟键盘的普通模式下 `InputView` 只会收到 bulk。

### `StatusMessage` 与 `onInputStatusUpdate`

每次响应都发 `StatusMessage`，但 `InputView` 从来不调用 `InputBroadcaster.onInputStatusUpdate`，
也没有任何接收者实现它 —— 这个广播是死的。老代码需要 status 时都直接读 `rime.run { statusCached }`。
session 把 status 收进了 `InputState.status`，并在只有 `OptionMessage` 的情况下按选项名
（`ascii_mode` / `full_shape` / `simplification` / `traditional` / `ascii_punct`，见 librime `RimeGetStatus`）
修补对应的标志位。

## 原子性

每条消息是一次独立的主线程派发，Compose 可能在两次派发之间出帧：如果按消息逐条更新，
UI 会看到"拼音已更新、候选还是上一次"的中间帧。

`DefaultInputSession` 按一次引擎响应整体更新：

- `CommitTextMessage` 打开一个响应，此后的引擎字段（composition、candidates、status、schema、
  options、hasMenu、paging）写进暂存副本；`StatusMessage` 关闭响应，一次性发布。
- 不在响应里的消息（librime 通知、ascii 提示及其恢复）立即生效。
- 编辑框相关（`onStartInput`、`onSelection`、`onEnterKey`）不属于引擎，从不被暂存，
  响应发布时也不会被暂存副本覆盖。
- 响应已打开时又来一条 `CommitTextMessage`，先发布已打开的那个。只有 status 被流丢掉时才会这样：
  `DROP_OLDEST` 丢的是最旧的，status 是一次响应里最新的，丢了它意味着开头的 commit 也已经丢了，
  所以实际上几乎遇不到，这条只是兜底，保证暂存副本不会永远卡住。

这样做风险低，是因为开闭标记都是 `Rime.emitResponse` 里无条件发出的消息，
不需要改 `Rime.kt` 或 JNI，老广播的顺序也没有动。

没有做到的：

- **消息丢失**：`messageFlow` 缓冲 15 条、`DROP_OLDEST`，一次响应 5 条。主线程卡顿时连续按键
  会丢掉中间的响应，session 和老接收者一样看不到它们；下一次完整响应到达后状态自愈。
  要彻底解决得让 `Rime` 把一次响应作为一条消息发出（改 `Rime.kt` 和所有收集者），留给后续阶段。
- **跨数据源**：老代码从 `Rime` 缓存读到的值可能已经领先于 session（缓存在 rime-main 上先于消息更新）。
  新 UI 只读 `InputState` 就不会有这种不一致。

UI 端的建议：只从 `state` 读，一帧里用同一个 `InputState` 值渲染 composition 和候选，
不要分别 `collect` 再各自存一份；需要更多候选时用 `loadCandidates()`，
并以 `state.candidates` 变化作为失效信号。

## `InputState` 字段

| 字段 | 来源 | 说明 |
|------|------|------|
| `composition` | `CompositionMessage`（经 `InputView` 过滤） | 组合栏显示的内容；行内预编辑或 `ALWAYS_SHOW` 时为空；ascii 提示期间是提示文本 |
| `candidates` | `BulkCandidatesMessage` | 前 16 个，`total == -1` 表示可能还有；分页模式下恒为空 |
| `status` | `StatusMessage`，`OptionMessage` 修补 | |
| `schema` | `SchemaMessage` | 引擎回话前为 null |
| `options` | `OptionMessage`，初始值来自 status | |
| `editor` | `onStartInput` / `onSelection` | `EditorInfo` 的拷贝 |
| `hasMenu` | bulk 或 paged 候选非空 | 分页模式下也有效，对应 `Rime.hasMenu` |
| `paging` | `Candidates.Paged.hasPrevPage` | 仅分页模式；bulk 模式恒为 false |
| `enterKey` | `EnterKeyDisplayDelegate` | `label`、`isPrimaryAction` |
| `isComposing`（计算属性） | `status.isComposing` | 不看 composition，因为它在行内预编辑时是空的 |

## 老接收者迁移到 session 的建议顺序

本阶段不迁移任何接收者。`ime/candidates`、`ime/composition`、`ime/bar` 由 Compose 候选栏 / 组合栏的工作替换。
其余接收者按依赖从少到多：

1. **`KeyboardWindow.onKeyAppearanceUpdate`** 以及 `InputView.broadcastKeyAppearanceUpdate`：
   改成观察 `state` 的 `(isComposing, hasMenu, paging)`，去掉 `lastAppearanceState` 和对 `Rime` 缓存的读取。
   顺带 `Key.kt` / `KeyAction.kt` 里读 `hasMenu`、`statusCached` 的地方可以从 session 取。
   注意 `Rime.paging` 在从分页切回 bulk 时不会复位，session 的 `paging` 会，行为上是修 bug。
2. **`KeyboardView` 的回车键**：现在直接读 `EnterKeyDisplayDelegate.keyLabel / isPrimaryAction`，
   改为读 `state.enterKey`；`onEnterKeyLabelUpdate` 目前没有接收者，可以连同广播方法一起删掉。
3. **`KeyboardWindow.onSelectionUpdate / onStartInput`**（自动大写）：改读 `state.editor` 和 `state.status.isAsciiMode`。
4. **`KeyboardWindow.onRimeSchemaUpdated / onRimeOptionUpdated`**：切键盘布局，改为观察 `state.schema` / `state.options`
   的变化。要保留"每次通知都触发"的语义（同值重复通知也切），需要确认是否依赖这一点再改。
5. **`SwitchOptionWindow.onRimeSchemaUpdated / onRimeOptionUpdated`**：列表内容还要读 `schemaCached.switches`
   （需要打开 schema 配置），可以先观察 `state.schema` / `state.options` 触发刷新，配置读取保留。
6. **`KeyboardWindow` 的九宫格拼音列**（`onCandidateListUpdate` / `onCompositionUpdate`）：依赖 `getRawInput()`，
   可以改为观察 `state`，候选变化时再异步查询。
7. 全部迁完后删除 `InputBroadcaster` 里的引擎相关方法（`onStartInput` 到 `onInputStatusUpdate`），
   只留窗口相关的 `onWindowAttached / onWindowDetached`（它们与引擎状态无关，不属于 `InputState`）。
   `onInputStatusUpdate` 现在就可以删。

长期看，`Rime.emitResponse` 可以直接发一条"响应"消息（`RimeResponse` 本来就是一次 JNI 取回的整体），
届时 session 的开闭标记逻辑可以删掉，也顺便解决 `messageFlow` 丢消息造成的中间态。
