# 横屏体验笔记（全屏输入框 + `_land` 键盘）

分支 `feature/landscape-input`，基于 `custom`。两件事：横屏时在键盘上方显示输入框，
以及主题里可以给键盘写一份横屏专用布局。

---

## 1. 横屏全屏（抽取）模式

`TrimeInputMethodService.onEvaluateFullscreenMode()` 原来写死 `false`，横屏时键盘盖住
大半个屏幕，用户看不见自己输的是什么。现在横屏默认进安卓标准的全屏模式，framework
会在键盘上方留出「抽取区」（extract area），里面放我们自绘的输入框
`ime/core/ExtractInputUi.kt`。

### 什么时候**不**进全屏

| 条件 | 怎么判断 |
| --- | --- |
| 竖屏 | `resources.configuration.isLandscape()` 为假 |
| 偏好项关掉 | `prefs.keyboard.landscapeFullscreen`（默认**开**） |
| 横屏小窗键盘 | `Context.isFloatingKeyboard()`，小窗本来就不占满屏幕 |
| 编辑器不要抽取视图 | `EditorInfo.IME_FLAG_NO_EXTRACT_UI` |
| 其余 | 交回 `super.onEvaluateFullscreenMode()`，它还会挡掉 `IME_FLAG_NO_FULLSCREEN` 和「App 窗口本身是竖的」（分屏 / 自由窗口）这些情况 |

偏好项改了立刻生效：它挂在 `recreateInputViewPrefs` 上，监听器里先
`updateFullscreenMode()` 再 `replaceInputView()`。

### 输入框长什么样

`ExtractInputUi` 是个横向 `LinearLayout`：左边一个 `ExtractEditText`（id 必须是
`android.R.id.inputExtractEditText`，framework 靠这个 id 找它并接管），右边一个动作键。

动作键的文案取 `EditorInfo.actionLabel`，没有就用主题 `enter_labels` 里对应的
（`done`/`go`/`next`/`pre`/`search`/`send`，都没有就 `default`）；编辑器不要动作键时
整个藏掉。点下去照 framework 的 accessory action 走 `performEditorAction`。

**没有**用 framework 的 `inputExtractAccessories` / `inputExtractAction` —— 那两个 id 是
`com.android.internal` 的，应用侧拿不到，所以 `onUpdateExtractingViews()` 也自己覆写了。

配色全部从 `ColorManager` 取，`replaceInputViews()` 里跟着主题/配色变化重刷：

| 位置 | key |
| --- | --- |
| 抽取区底色 | `back_color` |
| 输入框底色 | `text_back_color` |
| 正文 / 光标 | `text_color` |
| 提示文字 | `comment_text_color` |
| 选中区 | `hilited_back_color` |
| 动作键底色 / 文字 | `key_back_color` / `key_text_color` |

描边没有用 `border_color`：很多主题的 `border_color` / `key_border_color` 直接回落到
`back_color`，那样输入框和动作键会跟底色糊成一片，所以固定用正文色压到三成透明度画
1dp 边。

### 一个坑：`setInputView` 把抽取区挤成 0

framework 的窗口是个竖向 `LinearLayout`：`fullscreenArea`（里面就是 `extractArea`）
拿 `height=0 / weight=1`，`inputArea` 是 `wrap_content`。本 fork 原来在 `setInputView()`
里把 `inputArea` 顶成 `MATCH_PARENT`（这样键盘上方那片透明区域可以画按键弹窗，
可触摸范围由 `onComputeInsets()` 收回键盘本身），结果全屏时 `inputArea` 先把整个窗口
高度吃掉，`weight` 分下来是 0 —— 输入框根本画不出来。

所以 `applyInputAreaHeight()` 在全屏时把 `inputArea` 和输入视图改成 `WRAP_CONTENT`，
并把这条链路（root / inputArea / InputView）的 `clipChildren` 关掉，让按键弹窗还能画到
输入框那片区域上去。这个函数在 `setInputView()` 和 `onConfigureWindow()` 两处调用 ——
后者是因为换 App 导致全屏状态变化时 framework 不会重新 `setInputView`。

`onComputeInsets()` 不用改：抽取视图显示时 framework 走的是自己那条
`TOUCHABLE_INSETS_FRAME` 分支，压根不调 `onComputeInsets`。

---

## 2. 主题约定：`_land` 后缀 = 横屏专用键盘

**主题作者要知道的就这一条：`preset_keyboards` 里如果存在「某个键盘名 + `_land`」，
横屏时会自动改用它。**

```yaml
preset_keyboards:
  t9:            # 竖屏用这个
    width: 33
    height: 60
    keys: [...]
  t9_land:       # 横屏自动换成这个，比如左边多一块数字小键盘
    width: 10
    height: 44
    keys: [...]
```

- 没写 `_land` 的键盘行为完全不变，老主题不受影响。
- `select:` 切键盘（比如按「符號」切到 `symbols`）也走同一套解析：横屏下有
  `symbols_land` 就用 `symbols_land`。
- 旋转屏幕会重建输入视图并重新解析一次，所以转回竖屏自动换回原来那份。
- 判断用的是屏幕的**真实方向**，跟按键上的 `hide_in_landscape` 一致；「启用横屏模式」
  那个偏好项管的是分屏/键高，不影响套哪一份布局。
- 主题里原有的 `landscape_keyboard:` 字段优先级更高，写了就先用它。

### 写 `_land` 布局时的限制（照着截图排键盘之前先看这段）

**宽度：`width` 是百分比，一行加起来 100。** 没有「第几行」这种字段，排版全靠宽度累加：
从头往后铺，累计宽度超过 100 就自动换行（或者攒够 `columns` 个可点击的键，默认 30，
基本碰不到）。所以想要「左边三列数字 + 右边七列字母」，就是把这一行的 10 个键
按顺序写在一起，每个 `width: 10`：

```yaml
    keys:
    - {click: 'KP_7'}   # 用键盘级的 width
    - {click: 'KP_8'}
    ...
    - {click: space, width: 40}   # 单个键可以自己覆盖
```

单个键写 `width:` 覆盖键盘级的 `width:`；键盘级没写、键上也没写时才回落到主题的
`key_width`。

**高度：`height` 只是行与行之间的比例，不是绝对高度。** 键盘总高由
`keyboard_height`（键盘级，横屏用 `keyboard_height_land`）决定，没写就用主题
`style/keyboard_height` / `keyboard_height_land`（单位 dp）。排完行以后所有行会
**等比缩放塞进这个总高**：

```
每行高 = height_i / Σheight × keyboard_height
```

也就是说 **多加一行不会把键盘撑高，只会把每行压扁**。九宫格 4 行改成 5 行的话，
要么接受每行矮 20%，要么在 `t9_land` 里把 `keyboard_height_land` 调大。
行数本身没有上限。

**其它几条：**

- `_land` 键盘是独立条目，`ascii_keyboard:` / `lock:` / `ascii_mode:` / `columns:` 这些
  要自己写一遍，不会从同名的竖屏键盘继承（注意 `ascii_mode` 不写默认是 1）。
  `import_preset:` 可以整份复制另一个键盘，但它是**整体替换**不是合并，写了
  `import_preset` 同一条目里的其它字段会被丢掉。
- `hide_in_landscape: true` 在 `_land` 键盘里必然成立（横屏才会用到它），所以别在
  `_land` 的键上写这个标记，写了等于那个键永远不出现。
- 如果偏好项「启用横屏模式」开着，横屏键盘会按 `landscape_split_percent`（或偏好项
  「分屏空格占比」）在中间劈开一道缝。`_land` 布局要是自己就排满了，把键盘级的
  `landscape_split_percent` 留空、并注意这个偏好项默认是「从不」。

### 实现在哪

唯一入口是 `ime/keyboard/KeyboardWindow.kt` 的 `evalKeyboard()`（`.default` / `.ascii` /
`select:` 的名字、旋转后的重建全都经过它），末尾调
`landscapeVariantOf()`（同文件）做后缀替换。后缀常量是
`KeyboardWindow.LANDSCAPE_SUFFIX`。
