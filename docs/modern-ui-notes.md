# 应用界面现代化改造笔记（Compose + Material 3）

分支 `feature/modern-ui`，基于 `custom`。**改造已经收口：应用界面全部是 Compose + Material 3，
`androidx.preference` 已经从依赖里去掉。**

范围只限 **应用界面**（`ui/` 下的设置 App）。键盘视图 `ime/`、librime 绑定 `core/`、
主题 yaml 解析 `data/theme/` 一律不动。

分五波做完：

| 波次 | 内容 |
| --- | --- |
| 一 | Compose/M3 地基、主题层、preference 渲染器，打样首页与虚拟键盘页 |
| 二 | 六个设置页（通用、候选窗、剪贴板、主题、高级、开发者） |
| 三 | 三个列表页（方案、用户词典、热词） |
| 四 | 关于、开源许可、用户配置 |
| 五（收口） | 首次引导、日志、剪贴板编辑、三个选择器对话框、加载框；删掉全部老基础设施 |

---

## 1. 依赖与版本

`gradle/libs.versions.toml`：

| 项 | 版本 | 说明 |
| --- | --- | --- |
| `composeBom` | `2026.08.00` | → compose ui / foundation **1.12.0**、material3 **1.4.0** |
| `androidx-activity-compose` | 1.10.1 | 与既有 `activity-ktx` 1.10.1 对齐 |
| `lifecycle`（`runtime-compose` / `viewmodel-compose`） | 2.9.4 | |
| compose 编译器 | Kotlin 自带 `org.jetbrains.kotlin.plugin.compose` 2.3.21 | |

**关于 minSdk**：`androidx.compose.ui` 从 1.10.0 起要求 minSdk 23，所以早先卡在
2025.11.01（compose ui 1.9.5，还支持 minSdk 21）。产品已决定放弃 Android 5/6/7，
`minSdk` 提到 **26**，BOM 随之升到 `2026.08.00`。

注意 BOM 2026.08.00 里 **material3 仍是 1.4.0**（expressive 那一版），跟升级前一样；
真正动的是 compose ui / foundation / runtime，1.9.5 → 1.12.0。所以这次升级没有
M3 组件签名变更要适配，`LargeTopAppBar` / `TopAppBarDefaults` / `Slider` / `AlertDialog`
的用法都保持原样。下次 material3 跳到 1.5 时才需要重新检查这些。

`build.gradle.kts` 里给 spotless 加了 `editorConfigOverride`，让
`ktlint_function_naming_ignore_when_annotated_with = Composable` 生效，否则每个
`@Composable` 的 PascalCase 函数名都会被 ktlint 判错。

### 依赖增减

- **去掉**：`androidx.preference`。
- **保留**：`flexbox`、`bravh`（BaseRecyclerViewAdapterHelper）、`viewpager2`、
  `recyclerview`、`splitties.views.dsl*` —— **这些全是键盘 `ime/` 在用**（候选栏、
  剪贴板面板、符号面板），跟应用界面无关，别删。
- `androidx.appcompat` 也留着：`MainActivity` / `LogActivity` 还是 `AppCompatActivity`
  （要 `AppCompatDelegate.setDefaultNightMode` 来落实 `uiMode` 偏好项），另外几处无障碍
  描述借用了 `androidx.appcompat.R.string.abc_*`。

---

## 2. 导航方案：**继续用 Fragment 导航图，不换 navigation-compose**

**结论：维持现状。** 改造收口时重新评估过，结论是不换。

现状是 `NavigationRoute.createGraph()`（Navigation for Fragments），14 个目的地全部是
宿主为 `ComposeFragment` 的 Compose 屏幕。

### 为什么不换

1. **外部入口是硬约束。** `NavigationRoute` 是 `@Parcelize` 的，键盘通过
   `AppUtils.launchMainToSchemaList()` / `launchMainToKeyboard()` 把路由塞进
   `Intent`（`action = ACTION_RUN` + `MainActivity.EXTRA_SETTINGS_ROUTE`），
   `MainActivity.processIntent()` 收到后 `popBackStack(Main, false)` 再 `navigate(route)`。
   换成 navigation-compose 后 NavController 活在 composition 里，activity 拿不到它，
   得改成「activity 收 Intent → 塞进一个 StateFlow → composition 里 `LaunchedEffect`
   消费」，还要处理 `launchMode="singleTask"` 下 activity 已存在时的重复投递。
   **这条链路的调用方在 `ime/`，本轮不许动**，改了就没法只在应用侧验证。
2. **换了得上真机验证，而现在验证不了。** 本机没有 NDK，`assembleDebug` 跑不了，
   装不了设备。返回栈、进程被杀后的状态恢复、`singleTask` 下的 Intent 重投，恰恰是
   只能在真机上验出来的东西。拿编译通过当质量保证去换导航栈，风险和收益不成比例。
3. **收益不大。** 想要的转场动画 `util/NavController.kt` 的 `navigateWithAnim`
   已经给了；每屏一个 `ComposeView` 的开销在设置 App 这种量级上无所谓。

### 什么时候值得换

同时满足这两条再说：能上真机验证；并且允许一起改 `ime/` 里的
`AppUtils.launchMainTo*`（比如改成传字符串 route 而不是 Parcelable）。
到那时要改的是 `NavigationRoute`、`MainActivity.processIntent`、
`ComposeFragment`/`PreferenceDelegateComposeFragment` 这三处，页面本身不用动。

### 顶栏由谁画

**全部由 Compose 画。** `MainActivity` 已经没有 XML toolbar 了：
`res/layout/activity_main.xml` 只剩 `FragmentContainerView` 和 `TestInputPanel`，
`res/layout/toolbar.xml` 已删除。`MainActivity` 也不再按目的地切 chrome
（原来的 `NavigationRoute.composeDestinations` / `isComposeDestination()` 已删除）。

insets 现在只有 `TestInputPanel`（activity 布局里的 View）需要手动处理，
它要同时避开导航栏和输入法；Compose 页面自己用 `safeDrawing` 处理。

> 如果将来又加了一个**不是** Compose 的目的地，得把这套按目的地切 chrome 的判断加回来。

---

## 3. 目录与命名约定

```
ui/theme/                     主题层（只给 App 界面用，跟键盘的 yaml 主题无关）
  Color.kt / Theme.kt / Type.kt / Shape.kt

ui/compose/                   可复用的 Compose 地基
  ComposeFragment.kt          Compose 屏幕的 Fragment 宿主基类
  TrimeScreen.kt              统一 chrome：Large TopAppBar + 折叠 + edge-to-edge
                              + contextual（多选）模式
  preference/
    PreferenceItems.kt        低层列表项与对话框（PreferenceRow / Switch / Slider /
                              SingleChoiceDialog / NoticeDialog / LoadingDialog /
                              withLoadingState / …）
    PreferenceDelegateScreen.kt  吃 PreferenceDelegateUi 模型的渲染器 + Fragment 基类

ui/main/MainScreen.kt         首页的可组合函数
ui/main/MainFragment.kt       首页的宿主（只有十几行）
ui/main/settings/list/ListScreen.kt   三个列表页共用的骨架（FAB + snackbar）
```

命名约定：

- 页面的可组合函数叫 `XxxScreen`，**放在与旧 Fragment 同一个包**，文件名 `XxxScreen.kt`。
- 宿主类沿用旧的 Fragment 类名（`XxxFragment`），这样 `NavigationRoute` 不用改。
- 只在一个页面里用的可组合函数写成 `private`，多页面复用的才提到 `ui/compose/`。
- 可复用的列表项后缀统一是 `...PreferenceItem`。

---

## 4. 主题层怎么用

```kotlin
TrimeTheme { /* content */ }
```

`ComposeFragment` 已经帮你包好了，页面里**不要再包一层**；独立 activity
（`SetupActivity` / `LogActivity` / `ClipEditActivity`）自己在 `setContent` 里包。

- **动态取色**：Android 12+（API 31）走 `dynamicLightColorScheme` /
  `dynamicDarkColorScheme`（Material You 跟随壁纸）；低版本回退到 `Color.kt` 里那套
  以 Rime 强调色 `#009BD1` 为种子的冷蓝品牌色。
- **深浅色**：`isSystemInDarkTheme()` 读 activity 的 configuration。
  `MainActivity` / `LogActivity` 是 `AppCompatActivity`，`AppCompatDelegate
  .setDefaultNightMode()` 会把 `uiMode` 偏好项（AUTO/LIGHT/DARK）落到 configuration 上，
  所以**这个偏好项在设置 App 里自动生效**。
  `SetupActivity` / `ClipEditActivity` 不是 AppCompat，跟随系统深浅色
  （引导页在用户还没设过任何东西的时候跑，无所谓；剪贴板编辑窗的
  `Theme.DialogTheme` 本来就有 `values-night` 版本，两边一致）。
- **系统栏图标颜色**：`TrimeTheme` 里的 `SideEffect` 按深浅色设置
  `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars`；
  `ClipEditActivity` 是悬浮窗口，传 `applySystemBarAppearance = false` 关掉。

### `TrimeScreen`

```kotlin
TrimeScreen(
    title = stringResource(R.string.xxx),
    onNavigateUp = { navigateUp() },
    actions = { TopBarIconButton(R.drawable.ic_x, desc, onClick) },
) { padding ->
    LazyColumn(contentPadding = padding) { /* ... */ }
}
```

`padding` 一定要传给滚动容器的 `contentPadding`，**别当 `Modifier.padding` 用**，
否则内容不会从大标题底下滚过去，edge-to-edge 也就白做了。

**contextual（多选）模式**：`contextual = true` 时换成 M3 的 contextual 顶栏——
钉住不折叠的小顶栏、`secondaryContainer` 容器色、导航图标变成 ✕。
页面照旧把「退出多选」的回调传给 `onNavigateUp` 就行：

```kotlin
ListScreen(
    title = if (selecting) stringResource(R.string.n_selected, n) else stringResource(R.string.schemata),
    onNavigateUp = if (selecting) ({ exitSelection() }) else onNavigateUp,
    contextual = selecting,
    actions = { /* 多选时是删除，平时是编辑 */ },
)
```

两种顶栏的 `scrollBehavior` 都是无条件创建的（避免切换时丢状态），退出 contextual
时会把大标题的折叠状态复位。需要完全自定义导航图标的，传 `navigationIcon`。

> 三个列表页都走 `ListScreen` → `TrimeScreen`，所以都能用；目前只有**方案列表页**
> 真的有多选模式，用户词典和热词页没有。

---

## 5. Compose 版 preference 渲染器

Trime 的设置项不是 XML 定义的，是 `data/prefs/` 里的代码模型：
`PreferenceDelegateOwner` 里 `switch(...) / int(...) / list(...) / enum(...)` 这些函数
同时注册一个 `PreferenceDelegate<T>`（存取）和一个 `PreferenceDelegateUi`（界面描述）。

`ui/compose/preference/PreferenceDelegateScreen.kt` 把这份描述直接渲染成 M3 列表项：

| `PreferenceDelegateUi` | Compose 表现 |
| --- | --- |
| `Switch` | 整行可点 + M3 `Switch` |
| `SeekBarInt` | 行内 `Slider` + 右侧数值气泡；点气泡开精确输入对话框（带「默认」按钮） |
| `EditTextInt` | 行 + 数字输入对话框，按 min/max 夹紧 |
| `EditText` | 行 + 文本输入对话框 |
| `StringList` / `UniversalStringList` | 行显示当前项 + M3 单选对话框；当前值不在候选里时显示「未设置」（`R.string.not_set`） |
| `StringLike` | 纯行；点击行为由 `clickHandlers()` / `suspendClickHandlers()` 按 key 注入 |

**存储格式没有任何改动。** 读写一律通过既有的 `PreferenceDelegate.getValue()/setValue()`，
SharedPreferences 的 key、类型、序列化方式全部照旧。

`PreferenceDelegateUi` 里那个 `createUi(context)`（渲染成 androidx `Preference`）
已经删掉了，跟着删掉的还有它的类型参数 `<T : Preference>`；**模型定义全部保留**。

`enableUiOn`（依赖关系）保持旧行为——**置灰而不是隐藏**。实现方式：页面持有一个
`revision` 计数，注册 `PreferenceDelegateProvider.OnChangeListener`，任何一项变化就 +1，
所有行重新求值 `isEnabled()` 并重读自己的值。页面由多个 provider 拼成时，
同一个监听挂在**每个** provider 上，所以跨 owner 的页面置灰照样刷新。

### 分段和跨 owner 的页面（`PreferencePage`）

`AppPrefs` 里的 owner 是按**谁在读**分的组（`prefs.keyboard` 是键盘视图读的一堆，
IME 按组监听 `prefs.candidates`），不是用户找设置的方式。所以页面不再等于 owner：
`data/prefs/PreferencePage.kt` 描述一页 = 若干带标题的段，每段列出 key，
渲染时在传进来的几个 provider 里按 key 找行（`resolve()`，找不到直接抛异常）。

```kotlin
class KeyboardUiSettingsFragment :
    PreferenceDelegateComposeFragment(
        SettingsPages.KeyboardUi, // 段和 key 的清单
        AppPrefs.defaultInstance().keyboard,
        AppPrefs.defaultInstance().candidates,
        AppPrefs.defaultInstance().advanced,
    )
```

- **行搬页不搬 owner**：key、`PreferenceDelegate`、`prefs.keyboard.xxx` 这些调用点、
  IME 的监听（`recreateInputViewPrefs`、`prefs.candidates` 组监听）一个都不动。
- 有 `enableUiOn` 依赖的行必须和它依赖的行**在同一页**（`SettingsPagesTest` 检查）。
- 段标题为 0 就不画标题；只传一个 provider 的老构造函数照旧是「整个 owner、声明顺序、不分段」。
- 手写页面要画某一个模型行（比如语音页的「首选语音输入法」），用
  `PreferenceDelegateRow(provider, key)`，样子和对话框跟生成的页面一样。

### 设置的信息架构（2026-09）

首页三张卡，每页只属于一张。哪一行在哪一页以 `ui/main/settings/SettingsPages.kt` 为准（语音输入、配置两页手写），
`SettingsPagesTest` 保证每行恰好出现在一页、key 没改。「└」是从上一页的一行进去的子页面。

| 卡 | 页（路由） | 段 → 行 |
| --- | --- | --- |
| 输入 | 常规（`General`） | 打字：嵌入式预编辑、预编辑区插入符号、中英切换提示、横屏方案 · 系统集成：内嵌自动填充建议、启动器图标 |
| 输入 | 按键与手势（`VirtualKeyboard`） | 触摸：扩大按键区域、长按、连按间隔、双击 · 滑动：滑动距离、速度、步长 · Ctrl 快捷键：`hook_ctrl_*` · Shift 锁定：`hook_shift_*` |
| 输入 | 按键反馈（`KeyFeedback`） | 声音：按键音、音量、自定义音效 · 振动：按下/抬起/重复、效果、时长、强度 · 朗读：按键/上屏朗读 |
| 输入 | 剪贴板（`Clipboard`） | 历史记录：记录历史、上限 · 粘贴：剪贴板提示、提示超时、粘贴后返回 · 规则：去重规则、过滤规则 |
| 输入 | 语音输入（`VoiceInput`，手写） | 启用、录音权限、首选语音输入法 · 听写：自动停止、最长录音 · 标点：句号、标点规则、示例 · 大模型纠错：隐私提示、开关、字数阈值 · 服务：识别服务 ›、纠错服务 › · 识别词库：文件位置、重新加载、生成示例 |
| 输入 | └ 识别服务（`VoiceRecognitionService`，手写） | 识别：提供方、识别模型、云端热词表 ID · 火山凭证：鉴权方式、密钥、清空凭证 |
| 输入 | └ 纠错服务（`VoiceCorrectionService`，手写） | 接口：服务商、服务地址、模型、API Key · 请求：提示词、测试连接 |
| 外观 | 配色（`Theme`） | 模式：跟随壁纸、深浅色、导航栏背景 · 微调：暖度、亮度、不透明度，之后是页脚「试一试」「恢复配色默认值」 |
| 外观 | 键盘界面（`KeyboardUi`） | 按键与输入栏：隐藏输入栏/符号/提示、按键气泡 · 候选窗口：显示候选词窗口、布局、位置 · 横屏：键盘上方输入框、横屏模式、分割空格 · 横屏小窗：小窗、尺寸、宽、高、边距 · 布局边距：刘海区、忽略手势区 |
| 数据 | 方案、用户词典、热词 | 列表页。右下角按钮带文字：「启用方案」「从文件恢复」「添加热词」；方案页顶栏「移除方案」进多选 |
| 数据 | 配置（`Profile`，手写） | 存储：存储模式、数据目录 · 同步：定期后台同步、间隔、立即同步 · Git 配置仓库：部署前拉取、仓库与账号 ›、立即拉取并部署 · 维护：浏览数据目录、恢复默认设置 |
| 数据 | └ 仓库与账号（`GitRepository`，手写） | 仓库地址、分支、用户名、令牌 |

开发者、关于在首页右上角菜单；开发者页是「语音输入（调试）」（只有 debug 包）· 日志（实时日志、清空日志）。
原来的「候选窗口」「高级」页和它们的路由已删掉（没有深链指向它们）。
深链：键盘「…」面板的「配色」格 → `Theme`，「键盘界面」格 → `KeyboardUi`
（`AppUtils.launchMainToKeyboard`）。`VirtualKeyboard` 这个名字是历史遗留，
路由对象按类名打进 PendingIntent，**别改名**，要换页面内容就换 fragment。

### 二级页的整理原则

用户反馈「分不清哪块是设置、哪块是配置」，一级菜单分好了，二级页里还混着。每一页都按下面几条排，加新设置时照着放：

1. **分段**：超过 4 行左右的页面必须分段；段标题不重复页标题；不要「其他」这种只有一行的兜底段，给那一行找个真正的归处。
   `SettingsPagesTest` 检查渲染器页面：多于 4 行时每段有标题、每段至少两行、段标题 ≠ 页标题。
2. **页内顺序**：总开关 → 日常行为 → 微调（时长、阈值、滑块）→ **服务配置**（账号、密钥、地址、模型、云端 ID）
   → 数据 / 文件 → 维护和破坏性操作（清空凭证、恢复默认、清空日志）放最后。
3. **行为和配置不同段**：行为开关和服务配置不放在同一段。配置多的挪进子页面，主页面只留一行带摘要的入口
   （语音输入 → 识别服务 / 纠错服务，配置 → 仓库与账号）；语音页把两个入口放进单独的「服务」段。
   子页面入口**不随功能开关置灰**：先填好配置再打开开关是正常用法。
4. **子项跟着开关**：依赖某个开关的行紧跟在它下面，开关关着就置灰（渲染器用 `enableUiOn`，手写页面自己传 `enabled`）。
   `SettingsPagesTest` 检查依赖组在同一页、连续、开关在最前。
5. **同类不同对象分开**：比如「Ctrl 快捷键」和「Shift 锁定」，剪贴板的「历史记录」「粘贴」「规则」，配色的「模式」「微调」。
6. **摘要显示当前状态**：「API Key 已配置 · 流式识别 2.0」「DeepSeek · deepseek-chat」「DeepSeek · 未配置」
   「gitcode.com/you/config · main」。**摘要里绝不出现密钥**，连打码后的样子也不放：只说配没配；
   Git 地址只显示主机和路径，`user:token@` 不显示。
7. **存储不动**：偏好 key 一个都不改，owner 也不换；`SettingsPagesTest` 保证每一行恰好出现在一页
   （渲染器页面 + 已知的手写行）。

2026-09 这一轮的取舍：

- 「显示候选词窗口」和布局、位置放在一起（键盘界面 › 候选窗口），窗口关掉时布局、位置置灰。
- 「预编辑区使用插入符号」挪到常规 › 打字，挨着「嵌入式预编辑」：它改的是正在输入的编码怎么显示，不是键盘长什么样。
- 「显示应用图标」原来独占「其他」段，现在和「内嵌自动填充建议」组成「系统集成」：两行都是跟系统（启动器、自动填充）打交道。
- 「横屏时在键盘上方显示输入框」从常规挪到键盘界面 › 横屏：它决定横屏的布局，跟分割键盘、小窗是一类。
  分割空格比例在横屏模式为「从不」时置灰，小窗的尺寸、宽高、边距在小窗关着时置灰。
- 云端热词表 ID 是火山控制台里的 ID，算服务配置，放识别服务子页；词库文件是数据，留在语音主页面最后。
- 纠错的「少于多少字不纠错」是行为微调，留在主页面开关下面；隐私提示（「超过下面字数的文字会发出去」）跟着它留在主页面。
- 首选语音输入法（语音键切到别的输入法）是行为设置，放在语音页开头，挨着「启用语音输入」。

### 迁一个设置页要写多少代码

绝大多数情况只要一个类：

```kotlin
class ClipboardSettingsFragment :
    PreferenceDelegateComposeFragment(AppPrefs.defaultInstance().clipboard)
```

标题按这个顺序取：构造参数 `titleRes`（`PreferencePage` 页取 `page.title`）→
只有一个 provider 时的 `PreferenceDelegateOwner.title` → 导航图的 `label`。

三个可覆写的槽：

| 槽 | 用途 |
| --- | --- |
| `clickHandlers(): Map<String, () -> Unit>` | 给某个 `StringLike` 行挂点击行为 |
| `suspendClickHandlers(): Map<String, suspend () -> Unit>` | 同上，但可以挂起；在 composition 的协程作用域里启动，页面离开时取消 |
| `Footer()` | 在模型渲染的行后面追加内容（在 LazyColumn 里，只有滚到才会组合） |
| `Dialogs()` | 对话框等**不占布局空间**的东西，画在整个屏幕旁边而不是列表里 |

例（主题设置页）：

```kotlin
private var picker by mutableStateOf<Picker?>(null)

@Composable override fun clickHandlers() = mapOf(
    ThemePrefs.SELECTED_THEME to { picker = Picker.THEME },
)

@Composable override fun Dialogs() {
    if (picker == Picker.THEME) ThemePickerDialog.ThemeSelectionDialog { picker = null }
}
```

需要完全手写的页面（首页、Profile、关于），用 `TrimeScreen` +
`PreferenceRow` / `PreferenceCard` / `PreferenceCategoryHeader` 这些积木自己拼。

---

## 6. 三个选择器对话框：两套前端，一份逻辑

`ThemePickerDialog` / `ColorPickerDialog` / `SoundEffectPickerDialog` 做的事不只是写一个
偏好项，它们还要叫 `ThemeManager` / `ColorManager` / `SoundEffectManager` 去加载。

它们各自有**两个前端**，共用同一份选中逻辑：

- `build(scope, context, afterConfirm): android.app.AlertDialog`
  —— **键盘在用**（`ime/keyboard/CommonKeyboardActionListener`、
  `ime/switches/SwitchOptionWindow` 通过 `TrimeInputMethodService.showDialog(Dialog)`
  把它弹在输入法窗口上）。输入法那边没有 composition 可以承载 Compose 对话框，
  所以这条平台路径**必须留着**，签名也不能动。
- `ThemeSelectionDialog(onDismiss)` / `ColorSelectionDialog` / `SoundEffectSelectionDialog`
  —— 设置 App 用的 M3 版，挂在 `Dialogs()` 槽上。

主题那个是挂起的（要枚举主题文件），Compose 版在枚举完之前什么都不画，
等于旧实现「`build()` 挂起返回后才 `show()`」的效果。

## 6.1 加载框

`ui/common/withLoadingDialog` + `ProgressBarDialogIndeterminate` 已删除，换成
`ui/compose/preference/PreferenceItems.kt` 里的：

```kotlin
// 页面持有 loading 状态
withLoadingState({ loading = it }) { /* 干活 */ }
// 屏幕里
if (loading) LoadingDialog(R.string.hot_word_deploying)
```

阈值仍是 200ms（快活不闪对话框），`finally` + `NonCancellable` 保证一定复位。
热词页和用户配置页都用它。

---

## 7. 已迁移页面总表

**全部迁完。** 应用界面里已经没有 `androidx.preference` 页面、没有 XML 布局的页面
（只剩 `activity_main.xml` 这个容器）。

| 页面 | 路由 / 入口 | 实现 |
| --- | --- | --- |
| 首页 | `Main` | `ui/main/MainScreen.kt`（手写） |
| 常规 | `General` | `PreferenceDelegateComposeFragment` + `PreferencePage`（跨 4 个 owner） |
| 按键与手势 | `VirtualKeyboard` | 同上（`PreferencePage`） |
| 按键反馈 | `KeyFeedback` | 同上 + `Dialogs()`（音效选择器） |
| 键盘界面 | `KeyboardUi` | 同上（跨 3 个 owner） |
| 配色 | `Theme` | `PreferenceDelegateComposeFragment` + `PreferencePage` + `Footer()` |
| 剪贴板 | `Clipboard` | `PreferenceDelegateComposeFragment` + `PreferencePage` |
| 语音输入 | `VoiceInput` | `VoiceInputScreen.kt`（手写） |
| 识别服务 | `VoiceRecognitionService` | `voice/VoiceRecognitionServiceScreen.kt`（手写，fragment 同文件） |
| 纠错服务 | `VoiceCorrectionService` | `voice/VoiceCorrectionServiceScreen.kt`（手写，fragment 同文件） |
| 开发者 | `Developer` | `DeveloperScreen.kt`（手写） |
| 方案列表 | `SchemaList` | `SchemaListScreen.kt`（`ListScreen` + contextual 多选） |
| 用户词典 | `UserDict` | `UserDictListScreen.kt` |
| 热词 | `HotWords` | `HotWordListScreen.kt` |
| 用户配置 | `Profile` | `ProfileScreen.kt`（手写，最重） |
| 仓库与账号 | `GitRepository` | `GitRepositoryScreen.kt`（手写，fragment 同文件） |
| 关于 | `About` | `AboutScreen.kt` |
| 开源许可 | `License` | `LicenseScreen.kt` |
| 首次引导 | `SetupActivity` | `ui/setup/SetupScreen.kt`（`HorizontalPager`） |
| 日志 | `LogActivity` | `ui/main/log/LogScreen.kt` |
| 剪贴板编辑 | `ClipEditActivity` | 同文件内的 `ClipEditContent` |

### 被删掉的老基础设施

| 文件 | 说明 |
| --- | --- |
| `ui/common/PaddingPreferenceFragment.kt` | androidx.preference 页面基类 |
| `ui/common/OnItemChangedListener.kt` | 老列表页的回调接口 |
| `ui/common/ProgressBarDialogIndeterminate.kt` | → `LoadingDialog` + `withLoadingState` |
| `ui/main/settings/ProgressFragment.kt` | 没人继承了 |
| `ui/main/settings/DialogSeekBarPreference.kt` | → 渲染器里的 `SliderPreferenceItem` |
| `ui/main/settings/EditTextIntPreference.kt` | → 渲染器里的 `IntInputDialog` |
| `data/prefs/PreferenceDelegateFragment.kt` | → `PreferenceDelegateComposeFragment` |
| `PreferenceDelegateUi.createUi()` | 只删渲染函数，模型定义保留 |
| `PreferenceDelegateProvider.createUi()` / `PreferenceDelegateOwner.createUi()` | 同上 |
| `util/PreferenceScreen.kt` | androidx.preference 的扩展 |
| `util/Bundle.kt` | 只有 `SetupFragment` 在用 |
| `ui/main/log/LogView.kt` / `LogAdapter.kt` | → `LogScreen` |
| `ui/setup/SetupFragment.kt` | → `SetupScreen` |
| `res/layout/toolbar.xml` / `activity_setup.xml` / `fragment_setup.xml` / `activity_log.xml` / `activity_clip_edit.xml` | |
| `MainViewModel.toolbarTitle` / `topOptionsMenu` 及配套 | XML toolbar 的遗留 |
| `NavigationRoute.composeDestinations` / `isComposeDestination()` | 所有目的地都是 Compose 了 |
| `attrs.xml` 里的 `DialogSeekBarPreferenceAttrs` / `FolderPickerPreferenceAttrs` | |
| `colors.xml` 里的 `toolbarForegroundColor` | |

### 一处要小心的替换

`TrimeApplication` 原来用 `androidx.preference.PreferenceManager.getDefaultSharedPreferences()`
拿全局 SharedPreferences——**那就是用户全部设置的存储**。去掉 androidx.preference 之后
换成了 `util/SharedPreferences.kt` 里的 `Context.defaultSharedPreferences`，
文件名和 mode 照抄 androidx 的实现：

```kotlin
getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
```

**改这两个值中任何一个都等于把用户配置弄丢。**

---

## 8. 后续 agent 必须遵守的约定

1. **加一个新页面 = 新增 `XxxScreen.kt` + 一个 `ComposeFragment` 宿主 +
   在 `NavigationRoute.createGraph()` 里登记路由。**
2. **不要改 `data/prefs/` 的数据模型**。`PreferenceDelegate` / `AppPrefs` 的 key、
   默认值、序列化方式是用户设置的存储格式，改了就是丢用户配置。要加设置项，
   照 `PreferenceDelegateOwner` 现有的写法加，Compose 渲染器会自动认。
3. **不要包第二层 `TrimeTheme`**（`ComposeFragment` 已经包好了）。
4. **不要在 Compose 页面里自己画顶栏**，用 `TrimeScreen`；也不要自己算 insets，
   用它给的 `padding`。
5. **不要动 `ime/`、`core/`、`data/theme/`。** 尤其注意 `ime/` 大量使用
   splitties view DSL、flexbox、bravh、viewpager2，这些依赖不能删。
6. **不要动 `.github/workflows/`、签名配置、版本号、`minSdk`。**
7. 每完成一块就提交一次，中文 commit message，末尾带
   `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`。
8. 交付前必须 `./gradlew spotlessApply` 再 `./gradlew :app:compileDebugKotlin` 通过。
   本机没有 NDK，**跑不了 `assembleDebug`，也装不了设备**，这是唯一的验证手段。
9. 新增字符串资源要同时补 `values-zh-rCN` 和 `values-zh-rTW`。
   本轮新增的是 `exit_selection`（退出多选）和 `not_set`（未设置）。

---

## 9. 已知遗留 / 没验证过的东西

**下面这些全都只过了编译，没有在真机上跑过一次。** 优先按这个顺序上真机验：

1. **首次引导页**（`SetupScreen`）。它是新用户第一眼看到的界面，而且逻辑最绕：
   离开去系统设置开输入法再回来时靠 `onWindowFocusChanged` bump `revision`
   重新求值三步的 `isDone()`；存储模式没定之前不能左右滑；
   选目录成功/失败的两条分支；未完成时的提醒通知。
   还有一个**已知的细微差异**：旧实现在选目录成功后会强制显示「跳过」按钮，
   新实现是按 `updateButtons()` 那套规则推导（全部完成时不显示跳过）。
2. **日志页**（`LogScreen`）。`LazyColumn` 套在 `Modifier.horizontalScroll` 里，
   横向宽度取的是**可见项**的最大宽度，滚动时可能会抖——旧的
   `HorizontalScrollView` + `RecyclerView` 有同样的问题，但表现未必一样。
   另外自动跟随到底、崩溃模式下的三种按钮可见性都要看一眼。
3. **剪贴板编辑窗**（`ClipEditActivity`）。它是 `Theme.DialogTheme` 的悬浮窗口，
   `windowSoftInputMode="stateAlwaysVisible|adjustPan"`，Compose 内容在这种窗口里
   怎么测量高度、`FocusRequester` + `keyboard.show()` 能不能真的把键盘弹出来，
   都得实测。它是从键盘里拉起来的，路径特殊。
4. **`Context.defaultSharedPreferences`**。装一个旧版本、改几个设置、再装新版本，
   确认设置还在。这条最要命。
5. **三个选择器对话框的键盘那条路径**没有改过一行，但它们内部的加载逻辑被抽成了
   私有函数，值得在键盘里点一次「切换主题 / 配色 / 音效」确认没坏。
6. **contextual 顶栏**在方案列表页进出多选时的动画（大顶栏 ↔ 小顶栏切换会让
   列表 contentPadding 跳变，这是 M3 本身的行为，但观感要看一眼）。

其他遗留：

- **Predictive back 没做。** 它需要在 `AndroidManifest.xml` 的 `<application>` 上开
  `android:enableOnBackInvokedCallback="true"`，而这是**进程级**开关，Trime 是输入法，
  `InputMethodService` 在 API 33+ 也会受它影响（返回键怎么收起键盘）。装不了 NDK、
  跑不了真机，没法验证输入法那半边不出问题，所以**有意没开**。
- `MainActivity` 上的 `WindowInsetsAnimationCompat.Callback` 用的是
  `DISPATCH_MODE_STOP`，输入法弹出的动画 insets 不会往 Compose 子树传。
  目前 Compose 页面里的文本输入都在对话框里，影响不大；
  以后如果 Compose 页面要跟 IME 动画联动，得改成 `DISPATCH_MODE_CONTINUE_ON_SUBTREE`。
- 渲染器目前是**平铺列表**，因为 `PreferenceDelegateUi` 模型里没有分组信息。
  想要分组的话，可以给模型加一个可选的 group 标签，或者在页面里用
  `header`/`footer` + `PreferenceCategoryHeader` 手动分。
- `values/themes.xml` 里的 `Theme.TrimeAppTheme` 还是 AppCompat 主题。现在它只提供
  窗口背景和 `AppCompatActivity` 需要的那点东西；因为 `MainActivity` / `LogActivity`
  还是 `AppCompatActivity`（为了 `setDefaultNightMode`），暂时不能删。
- 首页顶栏两处有意的行为变化（第一波就有）：不再显示「返回」箭头（旧实现按下去是
  `moveTaskToBack`，即最小化），不再显示副标题 slogan（M3 `LargeTopAppBar` 没有
  subtitle 槽位）。系统返回键行为不变，slogan 挪到了关于页。

## 10. 本机怎么跑起来（不用 NDK）

本项目要用 NDK 编 librime，本机没装，但**不编 native 也能出 APK**：
`build-logic` 的 `NativeBaseConventionPlugin` 会在 `app/prebuilt` 存在时改用预编译的 JNI 库，
完全不配置 CMake。所以：

```bash
# 1. 从 CI 出的 APK 里取 .so（也可以用任何一个已有的 fork 版 APK）
gh release download custom-latest -R 029527/trime -D /tmp/apk
unzip -o /tmp/apk/*.apk 'lib/*' -d /tmp/apkx
mkdir -p app/prebuilt && cp -R /tmp/apkx/lib/arm64-v8a app/prebuilt/
rm -f app/prebuilt/arm64-v8a/libandroidx.graphics.path.so   # 这个来自依赖的 aar，别重复打进去

# 2. 子模块（assets 和 OpenCC 数据要用）
git submodule update --init --depth 1

# 3. 编译并装到模拟器
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
BUILD_ABI=arm64-v8a ./gradlew :app:assembleDebug     # 约 30 秒
adb install -r app/build/outputs/apk/debug/*.apk
```

`app/prebuilt/` 已在 `.gitignore` 里。模拟器用 `system-images;android-36;default;arm64-v8a`
建 AVD，`emulator -avd <name> -no-window -gpu swiftshader_indirect` 起无头实例即可。
**改 UI 必须实际跑一遍**：2026-09-09 的 `ComposeFragment` 递归崩溃（见下）编译完全正常，
只有真跑起来才会暴露。

### 已经踩过的坑：`AbstractComposeView.Content()` 同名陷阱

`ComposeFragment` 原来写成 `ComposeView(ctx).apply { setContent { TrimeTheme { Content() } } }`。
`apply` 的 receiver 是 ComposeView，而 `AbstractComposeView` 自己就有一个 `@Composable Content()`，
于是 `Content()` 绑到了**视图的**那个而不是 fragment 的抽象方法 —— 视图不断重新组合自己，
每个 Compose 页面第一帧就 `StackOverflowError`。编译器不会警告（两个签名都合法）。
现在写成局部变量 + `this@ComposeFragment.Content()`，别再改回 `apply`。

## 11. 运行时配色微调（暖度 / 亮度 / 不透明度）

原来键盘配色的暖白滤镜是在电脑上生成主题 yaml 时烘焙进颜色里的
（trime-config 的 `tools/gen_ios_theme.py`，三个旋钮 `WARM` / `DIM` / `ALPHA`），
改一次颜色要「改脚本 → 重新生成 → 同步到手机 → 部署」。现在这层滤镜搬到了 App 运行时：

* 算法在 `data/theme/ColorTint.kt`，和生成脚本的 `gains()` / `warm()` / `translucent()` 一一对应
  （按通道相乘、纯黑不动、深色配色加整数补偿、只有底色类的 key 才套 alpha）。全是纯函数，
  基准色值有单元测试 `ColorTintTest`。
* 挂载点是 `ColorManager` 的 `resolveColor()`（以及 `parseDrawable()` 的纯色分支 ——
  键面和键盘底是当作 `GradientDrawable` 画的，不走 `resolveColor`，漏了这条路就只有文字会变色）。
  两条路都要拿着**主题里的原始字符串**判断，因为「写没写 alpha」决定了要不要套不透明度，
  解析成 int 之后就分不出 `0xD1D3D9` 和 `0xFFD1D3D9` 了。
* 三个滑块在键盘样式页（`ThemePrefs.tintWarm` / `tintDim` / `tintAlpha`，默认 26 / 92 / 90），
  底下一行「恢复配色默认值」。`ColorManager` 直接监听这三个 key，清缓存后 `fireChange()`，
  `TrimeInputMethodService` 已有的 `onColorChangeListener` 会重建输入视图：**改完立刻生效，
  不用部署、不用重启输入法**。

### 迁移：主题 yaml 必须换成「未烘焙」的

现在手机上那份 `ios.trime.yaml` 里的颜色**已经烘焙了 26 / 92 / 90**，App 再套一遍就是滤镜叠滤镜。
切过去时要在 trime-config 那边把 `gen_ios_theme.py` 的旋钮设成中性值
（`WARM = 0.0`、`DIM = 1.0`、`ALPHA = 1.0`）重新生成主题，App 这边默认的 26 / 92 / 90 才刚好
等价于现在的观感。App 侧的滤镜在 `0 / 100 / 100` 时输出恒等于输入，所以两边不会互相绑死。
