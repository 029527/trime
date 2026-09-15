# 键盘设计系统

Trime fork 的键盘外观分成两半：

- **固定的一半写在代码里**（`ime/compose/theme/ImeTokens.kt`）：尺寸、间距、圆角、字号梯度、候选栏和预编辑区的样式、
  按键提示的位置和大小、按下反馈。主题改不了这些，所以换哪套配色键盘都不会变难用。
- **可变的一半在内置主题里**（`data/theme/builtin/`）：配色（浅色 / 深色两套，或跟随壁纸取色）、键盘布局。
- **字体内置**：思源黑体（Noto Sans SC 可变字体），键盘和 App 共用，见 §0.3。

外观是**现代 Android / Material** 风格；键位沿用 iOS 主题 + 默认主题效率的合体方案（§4），这次改外观**没有动任何键位**
（`BuiltinKeyboardsSnapshotTest` 钉住每个键）。§0 是设计语言，§1 是当初定键位时的调研，留作键位取舍的依据，
其中对 iOS 外观的描述是历史。每个数都带理由；想改数值，先改理由。

---

## 0. 设计语言：现代 Android

参考 Gboard 和 Material 3 Expressive：按键是适度圆角的矩形，没有描边和投影；层级靠色调区分；
唯一的强调色留给回车；按下是色调变化（state layer），不是换色也不是下沉。

### 0.1 颜色角色

所有配色都由 `KeyboardColorRoles`（`data/theme/builtin/KeyboardColorRoles.kt`）的九个角色生成，
主题颜色键怎么对应角色**只在那个文件里写一次**（KDoc 里有完整对照表）。固定配色和壁纸取色走同一张表，所以层级永远一致。

| 角色 | 用在哪 | 浅色（zinc） | 深色（zinc） | 跟随壁纸时的 Material 角色 |
|---|---|---|---|---|
| `surface` | 键盘底、候选栏、面板底 | `E8E8EC` | `18181B` | 浅 surfaceContainerHigh / 深 surfaceContainerLowest |
| `key` | 字母键、空格、气泡、剪贴板条目 | `FFFFFF` | `3F3F46` | 浅 surfaceContainerLowest / 深 surfaceBright |
| `functionKey` | 功能键、首选候选的胶囊 | `D4D4D8` | `323238` | 浅 secondaryContainer / 深 surfaceContainerHigh |
| `preedit` | 预编辑条 | `F4F4F5` | `27272A` | 浅 surfaceContainerLow / 深 surfaceContainerHigh |
| `onSurface` | 键面文字、候选 | `18181B` | `FAFAFA` | onSurface |
| `onSurfaceVariant` | 注释、提示 | `71717A` | `A1A1AA` | onSurfaceVariant |
| `onFunctionKey` | 功能键文字 | `18181B` | `FAFAFA` | 浅 onSecondaryContainer / 深 onSurface |
| `accent` | 回车键、开着的开关键、长按小键盘的焦点格、听写的胶囊和听写中的麦克风键 | `18181B` | `FAFAFA` | primary |
| `onAccent` | 强调色上的文字和图标 | `FAFAFA` | `18181B` | onPrimary |

- **按下色不是角色**：对应的 on 色以 12% 叠在底色上（Material 的 pressed state layer），
  `hilited_key_back_color`、`hilited_off_key_back_color`、`hilited_enter_key_action_back_color` 都这么算。
- 固定配色取 shadcn zinc 灰阶，和 App 设置页（`ui/theme/Color.kt`）同一套；强调色是「背景的反色」，
  浅色下近黑、深色下近白，和设置页主按钮一致，不引入品牌色。
- 对比度由 `KeyboardColorRolesTest` 检查：键面文字、功能键文字、回车文字 ≥ 4.5:1，注释 ≥ 3:1。
- 配色微调滑块（暖度 / 亮度 / 不透明度）照常叠加在最终颜色上，壁纸取色也一样。
- **跟随壁纸取色**（Android 12+）：`ColorManager` 用 `dynamicLightColorScheme` / `dynamicDarkColorScheme` 取系统调色板，
  经 `KeyboardColorRoles.fromMaterial()` 变成角色（浅色和深色取不同的 surface 角色，见上表最后一列和 KDoc），深浅由系统 uiMode 决定。
  输入法在 `onConfigurationChanged`、`onCreateInputView`、`onWindowShown` 时调用 `ColorManager.refreshWallpaperColors()`，
  颜色真的变了才重建键盘。

### 0.2 形状

| 部件 | 形状 | 理由 |
|---|---|---|
| 按键 | 圆角 10dp（横屏 8dp），无描边、无投影 | 比 iOS 的 6dp 软，又不至于在 34dp 宽的键上变成药丸 |
| 回车键 | 胶囊（圆角 = 键身高度一半），强调色 | 全键盘唯一的强调色和唯一的胶囊，一眼找到 |
| 首选候选、符号面板当前标签 | 胶囊，`functionKey` 色 | Material 的 chip / 选中态 |
| 按键气泡 | 圆角 14dp，3dp 投影 | 浮在 App 上，比键更圆 |
| 长按小键盘 | 圆角 16dp，焦点格圆角 10dp、强调色 | 焦点格就是「要打出的那个」，和回车同色 |
| 工具栏按钮按下 | 圆形色块 | Material 图标按钮 |
| 开关面板图标底块 | 圆角 16dp | Material 快捷设置块 |
| 键盘窗口 | 贴底时直角铺满；浮动键盘四角 16dp | Gboard 贴底不做圆角；浮动时是一张卡片 |

### 0.3 字重

一个可变字体（`SourceHanSans`，字重轴 350–700）给所有地方用，角色之间只差字重，**不用伪粗体**。
CJK 笔画在同字重下比拉丁字母显细，所以整体比 Roboto 默认的 400 / 500 略重一档。

键盘（`ImeTokens.*Weight`）：

| 角色 | 字重 | 用在哪 |
|---|---|---|
| `keyTextWeight` | 500 | 字母、数字、单字符键、九宫格字母组、符号面板单字 |
| `keyLabelWeight` | 550 | 功能键文字（`123` `换行` `ZH` `空格`）、符号面板底栏键 |
| `keySymbolWeight` | 400 | 右上角提示、底部提示 |
| `candidateWeight` / `candidateHighlightWeight` | 450 / 500 | 候选 / 高亮的首选 |
| `candidateCommentWeight` | 400 | 注释、拼音 |
| `preeditWeight` | 400 | 预编辑 |
| `popupWeight` | 500 | 气泡、长按小键盘 |
| `panelTitleWeight` | 600 | 面板标签栏标题：符号分类、剪贴板 / 收藏、面板窗口标题、开关首字 |
| `panelLabelWeight` | 500 | 编辑面板、开关名、菜单、分词块、工具栏文字按钮 |
| `panelBodyWeight` | 400 | 剪贴板条目、剪贴板建议、空列表提示 |

App（`ui/theme/Type.kt`）：页面大标题 / TopAppBar（display、headline）600，分组标题（title）600，
列表主文字（bodyLarge）500，次要说明（bodyMedium / bodySmall）400，按钮和分组表头（labelLarge）600，小标签 500。

缺字回退：字体里没有的字形（emoji、扩展 B 区以后的生僻字）走系统 `sans-serif` 回退链，emoji 是系统彩色 emoji。

### 0.4 图标

- Material Icons **Outlined** 风格，全部集中在 `ime/compose/theme/ImeIcons.kt`。按键和工具栏在布局里仍写 `ic@名字`
  （Community Material 的名字，改外观不改布局），`ImeIcons.vector()` 把名字映射到 Material 图标；没映射的名字仍画原来的图标。
- Material Icons 没有键盘 Shift，`ImeIcons.Shift` 是按 Outlined 描边规格画的空心箭头。
- 键盘画布里用 `rememberVectorPainter` 的 painter 画：每个图标光栅化一次缓存，着色 `ColorFilter` 按颜色缓存，每帧不分配。
- 图标尺寸按 Material 图标自带的留白算：按键 24dp（字形约 20dp），右上角图标提示 13dp，工具栏 24dp，编辑面板方向键 30dp。

> 过渡期：按键已经由 Compose 画布（`ime/compose/keyboard/KeyboardCanvas.kt`）按本文的 token 绘制，yaml 里和按键尺寸有关的字段
> 按 §3.1 处理；候选栏、预编辑区等其余部分还以各自的迁移进度为准。用户主题（trime-config 的 `tools/gen_ios_theme.py`）
> 继续按本文的值写 yaml，新旧渲染观感不跳。

---

## 1. 调研：两个主题在真机上的样子

对比用模拟器（1080×2400，420dpi，411×914dp），方案是用户在用的薄荷小鹤双拼。截图按
`before-default-*`（内置默认主题）、`before-ios-*`（改造前的 iOS 主题）、`hybrid-*`（本文方案）命名，
后缀是 `屏向-深浅色-键盘层`，例如 `hybrid-portrait-light-zh.png`。

一个容易误会的事实：**薄荷方案的 alphabet 全是字母，Trime 给它选的是 `qwerty`（预設26鍵），不是 40 键的 `default`**。
40 键布局只在方案 id 或 alphabet 对不上 `qwerty*` 时才出现。所以下面「默认主题」指的是用户实际会看到的 26 键版，
40 键版的数字行单独讨论。

### 1.1 默认主题好用在哪

| # | 类别 | 具体键位 | 为什么好用 |
|---|---|---|---|
| D1 | 效率 | 每个字母键都有长按 / 上滑符号：`q`–`p` 长按 `! @ # $ % ^ & * ( )`、上滑 `1`–`0` | 数字和常用符号不切层，一次长按到手 |
| D2 | 可发现性 | 长按内容**印在键面上**（`全選` `剪下` `複製` `貼上` `時間` 等小字） | 不用读文档就知道 a 长按是全选 |
| D3 | 效率 | `a` 全选、`x` `c` `v` 剪切 / 复制 / 粘贴 | 编辑操作不用长按文本、不用等系统菜单 |
| D4 | 效率 | 主键盘上直接有 `,` `.` `/`，中文态自动变 `，` `。` | 中文里最常用的两个标点零切换 |
| D5 | 容错 | 退格 15%、回车 15%、Shift 15%，比字母键宽一半 | 最常按的功能键最好按，盲按不容易落到 `m` 上 |
| D6 | 效率 | `中文` 键长按出方案菜单，`符號` 键长按出数字键盘，`Enter` 长按上屏编码 | 熟手捷径，不占键面 |
| D7 | 容错 | 键距 1dp，几乎没有缝 | 触摸区大（不过 fork 里键距画成内边距，本来就算触摸区，见 §2.3） |
| D8 | 效率 | 40 键版有独立数字行 | 打数字零成本；代价是键盘高一行 |

### 1.2 iOS 主题简洁在哪

| # | 类别 | 具体键位 / 样式 | 为什么简洁 |
|---|---|---|---|
| I1 | 层次 | 字母键白、功能键灰（浅色 `FFFFFF` / `ACB1BA`），按下功能键变白 | 一眼分出「打字的键」和「控制的键」，默认主题所有键同色 |
| I2 | 留白 | 横纵键距 7 / 8dp、圆角 5dp，键身 45dp | 每个键是独立的块，视线不乱 |
| I3 | 字号 | 字母 23sp，功能键文字 16sp，提示 9sp 且极淡 | 键面只剩字母，信息层级清楚 |
| I4 | 布局 | 26 键 iOS 标准位：第二行左右各留半键，底部 `123` `☻` `空格` `ZH` `换行` | iPhone 用户的手指记忆直接迁移 |
| I5 | 布局 | 最底下一行只有地球（切方案）和麦克风，键面透明 | 高频的「切换」不跟打字抢位置 |
| I6 | 候选栏 | 52dp 高、首选是紧贴文字的圆角色块、与键盘同底色 | 候选栏不像一条独立的工具栏，整体一块 |
| I7 | 状态 | `ZH` / `EN` 明写当前语言，深浅色跟随系统，回车在「搜索/发送」时变蓝 | 状态可见；默认主题**不跟随系统深色**（配色没有 `dark_scheme`，深色模式下仍是浅色键盘） |
| I8 | 键面 | Shift / 退格 / 地球 / 麦克风用空心图标，`Shift` `退格` 不写字 | 比汉字标签干净，中英文键盘通用 |

### 1.3 iOS 主题的短板（也就是默认主题赢的地方）

- **打一个 `，` 要三下**：`123` → `，` → 回到字母。中文里逗号句号是最高频的字符。（D4 赢）
- **数字靠长按第一行，但提示色 `B4B7BD` 叠在白键上对比度不到 2:1**，基本看不见，等于没有提示。（D1、D2 赢）
- **完全没有编辑入口**：全选 / 剪切 / 复制 / 粘贴只能靠系统长按文本菜单。（D3 赢）
- 退格 13.5% 加两边 1.5% 空隙，有效宽度比默认主题小。（D5 赢）
- 注释 11sp 是候选（20sp）的 55%，薄荷用注释显示错音提示和带调拼音，手机上读不清。
- 中文键盘上的 Shift 基本是死的（Shift 不下发给 librime，见 trime-config `docs/macos-ime-research.md` B4），但占了 13.5% 的位置。

### 1.4 冲突与取舍

| 冲突 | 默认主题 | iOS 主题 | 取舍 |
|---|---|---|---|
| 数字 | 独立数字行（40 键）或长按 | 第一行长按，提示看不见 | **不加数字行，第一行上滑 / 长按出数字，提示放右上角、50% 字色看得见**。加一行会把键压扁或把键盘加高 53dp；iOS 手指记忆里没有数字行 |
| 键面提示 | 每个键印一个符号或两个汉字 | 几乎不印 | **只印单个字符或图标，右上角，10sp、50% 字色**。两个汉字的提示（`全選`）改成图标。印了才可发现，但不能跟字母抢视线 |
| `，` `。` | 主键盘上 | 在 `123` 层 | **放在空格两边，跟字母键同色**。代价：空格从 44% 缩到 34%（约 138dp，仍是最大的键）；emoji 键挪到最底下一行 |
| 编辑命令 | a / x / c / v 长按，键面写字 | 无 | **保留在 a / x / c / v 长按，键面画图标**，位置和默认主题一致；只给长按不给上滑——误上滑剪掉整段的代价太大 |
| 光标移动 | h j k l 长按方向、s d 长按行首行尾 | 空格滑动移光标 | **只留空格滑动**。方向键长按一次移一格，不如空格拖动；把 hjkl 的长按让给标点 |
| 退格宽度 | 15% | 13.5% + 两侧空隙 | **15%，去掉空隙**。键距本来就把它和 `m` 分开了 |
| 功能键明暗 | 全同色 | 字母白 / 功能灰 | **iOS 的两级**，标点键算字母级 |
| 功能键长按 | 有（方案菜单、数字键盘） | 无 | **保留，但不印提示**。熟手捷径，印出来就又脏了 |
| 键距与圆角 | 1dp / 8dp | 7–8dp / 5dp | **6 / 8dp、6dp**（理由见 §2.3） |

---

## 2. 固定部分

数值对应 `ImeTokens.Portrait` / `ImeTokens.Landscape`。「View 过渡」一列是现在 yaml 里对应的字段，
主题生成脚本按这一列写。

### 2.1 候选栏

| Token | 竖屏 | 横屏 | View 过渡 | 理由 |
|---|---|---|---|---|
| `candidateTextSize` | 20sp | 18sp | `candidate_text_size` | 候选是读得最多的东西，和字母键（22sp）同一视觉重量、略小一级。横屏只有 32dp 高，放不下 20sp 加色块内边距 |
| `candidateCommentTextSize` | 14sp | 12sp | `comment_text_size` | 候选的 70%。注释里是错音提示和带调拼音，是有用信息；Mac 上是 87%，手机屏小取 70%。原来的 55%（11sp）读不清 |
| `candidateHorizontalPadding` | 12dp | 10dp | `candidate_padding` | 两个候选之间 24dp 空白，手指点得开；再大一页少放一个候选 |
| `candidateCommentGap` | 2dp | 2dp | —（View 固定 1dp） | 注释紧跟候选，读成一个整体 |
| `candidateHighlightPaddingHorizontal` / `candidateHighlightPaddingVertical` | 8 / 4dp | 6 / 3dp | `hilited_candidate_padding` | 首选胶囊**紧贴文字**：上下 4dp（字号的 20%），左右多给一点，胶囊两头的弧线不压笔画。两个候选之间的空白仍是 2 × `candidateHorizontalPadding` |
| `candidateHighlightCornerRadius` | 50dp | 50dp | `candidate_corner_radius` | 大于色块高度一半，画出来就是胶囊，不随字号变 |
| `candidateBarHeight` | 48dp + 注释行 | 32dp | `candidate_view_height` 52 + `comment_height` | Compose 版候选行 48dp（Material 最小触摸目标），不再单独给注释留高度。View 过渡期保持 52dp，避免键盘高度跳变 |

首选的形状：**实心胶囊 + 正常字色 + 字重 500**（其余候选 450），不用描边、不用下划线。胶囊颜色是 `functionKey` 角色
（`hilited_candidate_back_color`），深浅色只换颜色不换形状。

### 2.2 预编辑区

| Token | 竖屏 | 横屏 | View 过渡 | 理由 |
|---|---|---|---|---|
| `preeditTextSize` | 16sp | 14sp | `preedit/foreground/font_size` | 比候选小一级：拼音是辅助信息 |
| `preeditT9TextSize` | 13sp | 12sp | —（View 用注释字号） | 九宫格的拼音提示和注释同级 |
| `preeditHorizontalPadding` | 8dp | 6dp | `preedit/horizontal_padding` | 和候选栏对齐到同一左边距附近 |
| `preeditVerticalPadding` | 2dp | 1dp | — | 贴着键盘顶，不额外加高 |
| `preeditCornerRadius` | 12dp | 10dp | `preedit/top_*_radius` | Material 的小卡片圆角，比按键圆一档 |

预编辑区不透明（View 过渡 `preedit/alpha: 1.0`）：半透明的拼音条压在 App 文字上读不清。
默认是内嵌到目标输入框（`inlinePreeditMode`），这个区域只在关掉内嵌时出现。

### 2.3 按键

| Token | 竖屏 | 横屏 | View 过渡 | 理由 |
|---|---|---|---|---|
| `keyRowHeight` | 53dp | 40dp | `key_height`、`keyboard_height` | 键身 = 53 − 8 = 45dp，高于 44dp 触摸下限。横屏 4 行 × 40 = 160dp，屏高 400dp 的 40% |
| `keyCornerRadius` | 10dp | 8dp | `round_corner` | Material 的适度圆角：比 iOS 的 6dp 软；再大在 34dp 宽的键上就成了药丸。横屏键身只有 34dp 高，降一档 |
| `keyHorizontalGap` | 6dp | 6dp | `horizontal_gap` | 键距在 fork 里画成按键视图的内边距（`KeyboardView.kt`），**仍算触摸区**，所以缩小键距不增加命中率，只让键身变宽。6dp 足够让每个键成为独立的块 |
| `keyVerticalGap` | 8dp | 6dp | `vertical_gap` | 比横向略大：手指纵向误差比横向大，视觉上行间也需要更清楚 |
| `keyboardHorizontalPadding` | 3dp | 40dp | `keyboard_padding`、`keyboard_padding_land` | 竖屏贴边；横屏两侧各让 40dp，否则键被拉成扁条 |
| `keyboardCornerRadius` | 16dp | 16dp | `keyboard_corner_radius` | **只用于浮动键盘**（四角）。贴底的键盘直角铺满，和 Gboard 一致 |
| `keyTextSize` | 22sp | 20sp | `key_text_size` | 单字符键：字母、`，` `。`、数字。23sp 时 `m` `w` 在 34dp 宽的键里太满 |
| `keyLabelTextSize` | 16sp | 14sp | `label_text_size`、`key_long_text_size` | 功能键文字（`123` `空格` `换行` `ZH`）。比字母小两级，明确是「控制」 |
| `keyLetterGroupTextSize` | 20sp | 18sp | 九宫格按键上的 `key_text_size: 20` | 九宫格的 `ABC` `PQRS` 是打字的键，不是控制键；用功能键的 16sp 会和 `分词` `重输` 分不出主次。比单字母小一级，因为一个键上要放四个字母 |
| `keyIconSize` | 24dp | 22dp | 按键 `key_text_size` | Material 图标在 24dp 框里留白，字形约 20dp，和字母同高 |
| `keySymbolIconSize` | 13dp | 12dp | — | 右上角的图标提示（全选 / 剪切 / 复制 / 粘贴），比文字提示略大，补回图标自带的留白 |
| `keySymbolTextSize` | 10sp | 9sp | `symbol_text_size` | 提示是字母的一半不到。9sp 在 420dpi 上笔画发虚 |
| `keySymbolInsetTop` / `keySymbolInsetEnd` | 3dp / 5dp | 2dp / 4dp | `key_symbol_offset_y: 1` / `key_symbol_offset_x: 10` | **右上角**。放顶部正中会和字母的上沿挤在一条竖线上；右上角是 Gboard / 搜狗的通用位置，手指记忆通用 |
| `keySymbolAlpha` | 0.5 | 0.5 | 配色 `key_symbol_color` | 字色 × 50%：白键黑字上约 `86868B`（对比度 3.6:1），扫一眼找得到、永远不比字母抢眼。原来的 `B4B7BD`（1.9:1）等于没有 |

**提示内容只允许一个字符或一个图标**。两个汉字的提示在 34dp 宽的键上会挤到字母上方正中，右上角放不下。

**键面字号按内容自动分档**，不看按键上写的 `key_text_size`：`ic@` 图标 → `keyIconSize`；一个字符（按 Unicode 码位数）→ `keyTextSize`；
会上屏字符的键（点击动作是可打印按键码）上的一串拉丁字母 → `keyLetterGroupTextSize`；其余文字 → `keyLabelTextSize`。
`label_symbol: ' '` 这种空白提示不画。

**底部提示**（按键的 `hint`）和右上角提示同一套字号和透明度，水平居中，离键身底边 `keySymbolInsetTop`，多行时向上排。
iOS 主题没有用到它；一个键同时有右上角提示和底部提示时，两者分在上下两端，不会重叠。

#### 功能键与字母键的明暗层次

两级，不多不少：

| 级别 | 哪些键 | 颜色来源 |
|---|---|---|
| 字母级（亮） | 字母、数字、`，` `。` 等**上屏字符的键**、空格 | `key_back_color` |
| 功能级（暗） | Shift、退格、`123` / `ABC` / `#+=`、`ZH` / `EN` | `func_key_back_color`（或 `off_key_back_color`），即 `functionKey` 角色 |
| 强调 | 回车 | `enter_key_action_*`，即 `accent` 角色，胶囊形 |
| 透明 | 最底下一行的方案 / 表情 / 剪贴板 / 编辑 / 麦克风 | 透明底，只画图标 |

回车**总是**强调色，不只是输入框要求「搜索 / 发送 / 前往 / 完成」时：它是全键盘唯一的强调色。
深浅色下功能键都比字母键「低一级」：浅色是比白键深的灰，深色是介于键盘底和字母键之间的灰。

#### 按下反馈

| 键 | 反馈 | 理由 |
|---|---|---|
| 字母级 | 键上方弹出气泡：`keyPreviewWidth` 44dp × `keyPreviewHeight` 56dp，字 30sp（横屏 48dp 高、26sp）；上滑时气泡里换成上滑要打的字符 | 手指盖住了键面，气泡是唯一能确认按到哪个键的方式；上滑前能看到结果，敢滑 |
| 功能级 | 不弹气泡，按住时叠一层 12% 的按下色（`hilited_off_key_back_color`）；`keyPressedFunctionSwap` 为 false | 功能键没有「打出了什么」可以预览，色调变化就够了；Material 的按下反馈是 state layer，不是换色 |
| 全部 | 振动由用户设置决定（fork 的「振动效果」选项） | 不属于外观 |

View 过渡期：`key_press_offset_*` 在 fork 里解析了但**没有被绘制使用**，不要依赖它做下沉效果。

**按下色由配色表达，代码不强制**：按键画布取 `hilited_off_key_back_color` 或按键上写的 `hilited_key_back_color`，
由 `KeyboardColorRoles` 统一按 state layer 算好。编辑面板和符号面板底栏这类自己画功能键的地方读 `keyPressedFunctionSwap`：
为 false 时用 `ImeColors.highlightedFunctionKeyBack`。

### 2.4 横屏和竖屏的差异

| 项 | 竖屏 | 横屏 | 理由 |
|---|---|---|---|
| 最底下的方案 / 表情 / 剪贴板 / 编辑 / 麦克风行 | 有 | 隐藏（`hide_in_landscape`） | 屏高只有 400dp，这一行让给键盘；emoji 从 `123` 上滑的「更多」面板进；麦克风和中英切换挪进最后一行，剪贴板和编辑面板横屏没有入口 |
| 键盘高度 | 4 行 × 53 + 44 = 256dp | 4 行 × 40 = 160dp | 所有键盘统一，切符号层高度不跳 |
| 两侧留白 | 3dp | 40dp | 防止键被拉成扁条 |
| 候选栏 | 52dp（过渡期） | 32dp，关掉内嵌预编辑时上方再加 14dp 拼音行 | 横屏候选只能一行 |
| 字号 | 候选 20 / 注释 14 / 字母 22 | 候选 18 / 注释 12 / 字母 20 | 行高从 53 降到 40，字号跟着降一级，不按比例降（按比例会低于 16sp） |
| 九宫格 | `t9` | `t9_land`（左侧多一块数字小键盘）；悬浮键盘仍用 `t9` 等竖屏布局 | 布局由主题决定，不属于固定部分 |
| 长按小键盘 | 格高 48dp、字 24sp | 格高 40dp、字 22sp | 跟着行高降；格宽不变，仍和气泡同宽 |

### 2.5 弹出层和面板

按键以外的 Compose 部件也从 `ImeTokens` 取尺寸，字段名带区域前缀。圆角、键距、字号梯度能复用按键组的（`keyCornerRadius`、
`keyLabelTextSize` 等）就直接复用，下面只列各区域自己的。除了长按小键盘的两项和编辑面板（单独列了横屏），横竖屏同值。

**按键气泡和长按小键盘**（`popup*`）。Material 的浮层：`key` 色圆角块带低投影，贴在键的上方；长按小键盘的焦点格用强调色。气泡本身的尺寸和字号见 §2.3「按下反馈」。

| Token | 竖屏 | 横屏 | 理由 |
|---|---|---|---|
| `popupAnchorGap` | 2dp | 2dp | 气泡 / 小键盘底边离键身顶边的距离 |
| `popupPreviewCornerRadius` | 14dp | 14dp | 比按键（10dp）圆：气泡更高，且浮在 App 上 |
| `popupShadowElevation` | 3dp | 3dp | 刚好把气泡从同色的键上托起来 |
| `popupKeyboardCellWidth` | 44dp | 44dp | 和气泡同宽，按住的键看起来一样 |
| `popupKeyboardCellHeight` | 48dp | 40dp | |
| `popupKeyboardTextSize` | 24sp | 22sp | |
| `popupKeyboardPadding` | 4dp | 4dp | |
| `popupKeyboardCornerRadius` | 16dp | 16dp | |
| `popupKeyboardHighlightCornerRadius` | 10dp | 10dp | 焦点格的强调色块，和按键同圆角 |

**符号面板**（`symbol*`）。格子长得像按键：单字符用 `keyTextSize`，更长的用 `keyLabelTextSize`。

| Token | 值 | 理由 |
|---|---|---|
| `symbolCellMinWidth` | 48dp | 数据源没给格宽时的最窄单格，放得下一个 22sp emoji 加键距 |
| `symbolLongTextHorizontalPadding` | 4dp | 长文本离键身左右边的距离；小到三个字的标签还能放进一格 |
| `symbolSideBarWidth` | 64dp | 功能键栏在网格左右两侧时的列宽 |

**列表面板**（`panel*`）：剪贴板、方案开关、分词窗口共用。

| Token | 值 | 用途 |
|---|---|---|
| `panelListPadding` / `panelCellSpacing` | 4dp / 6dp | 列表外边距 / 条目间距 |
| `panelEntryTextSize` | 15sp | 剪贴板条目文字 |
| `panelEntryPaddingHorizontal` / `panelEntryPaddingVertical` | 10dp / 8dp | 剪贴板条目内边距 |
| `panelEntryMaxLines` | 4 | 条目最多显示几行；超长的剪贴内容先截断再排版 |
| `panelEntryPinSize` / `panelEntryPinAlpha` | 12dp / 0.3 | 置顶图钉 |
| `panelSwitchCellMinWidth` | 88dp | 开关网格最窄列宽：竖屏手机四列 |
| `panelSwitchCellHeight` / `panelSwitchTileSize` / `panelSwitchIconSize` | 96dp / 48dp / 24dp | 开关格、图标底块、图标 |
| `panelSwitchTileCornerRadius` | 16dp | 图标底块圆角，Material 快捷设置块 |
| `panelSwitchGlyphTextSize` / `panelSwitchLabelTextSize` | 20sp / 12sp | 无图标时的首字 / 下方标签 |
| `panelSegmentPaddingHorizontal` / `panelSegmentPaddingVertical` | 8dp / 4dp | 分词块内边距 |
| `panelSegmentMargin` | 4dp | 分词块四周外边距 |
| `panelSegmentEdgeScrollZone` / `panelSegmentEdgeScrollStep` | 10dp / 12dp | 拖到离上下边这么近时滚动，每次滚这么多 |
| `panelBarIconSize` / `panelDisabledAlpha` | 24dp / 0.38 | 面板顶栏图标，禁用时的透明度 |
| `panelMenuTextSize` | 16sp | 长按菜单 |
| `panelEmptyHintTextSize` / `panelEmptyHintAlpha` | 14sp / 0.6 | 空列表提示 |

**输入栏**（`bar*`）：键盘上方那一条和展开的候选网格。数值沿用 View 版输入栏，换成 Compose 时高度和布局不跳。

| Token | 值 | 用途 |
|---|---|---|
| `barUnrollButtonWidth` | 40dp | 候选行末尾的展开按钮 |
| `barIconButtonPadding` / `barIconButtonCornerRadius` | 4dp / 20dp | 纯图标按钮（收起键盘、展开、返回）的内边距和按下色块圆角（圆形） |
| `barClipboardIconSize` / `barClipboardSpacing` | 20dp / 4dp | 剪贴板建议的图标和间距 |
| `barClipboardMaxTextWidth` | 220dp | 剪贴板建议文字最宽 |
| `barClipboardVerticalMargin` / `barClipboardCornerRadius` | 4dp / 20dp | 建议块离栏上下边的距离、圆角（胶囊） |
| `barClipboardPreviewLength` | 42 | 剪贴板建议只取前 42 个字符，其余测量前就截掉 |
| `barInlinePinnedHorizontalMargin` | 10dp | 自动填充固定项的左右外边距 |
| `barTabSpacing` | 8dp | 面板标题栏里返回键、标题、面板自带栏之间 |
| `barUnrolledItemMinWidth` | 40dp | 展开候选网格里单个候选的最窄宽度 |

**编辑面板**（`editPanel*`）：底行编辑按钮打开的面板。格子就是按键，圆角、键距、明暗层次都用按键组的：
方向键是字母级（`keyBack`，按下 `highlightedKeyBack`），其余是功能级（`functionKeyBack`，按下按 `keyPressedFunctionSwap` 换亮色），
「选择」打开时用强调色（`accentBack` / `accentText`，即配色里的 `enter_key_action_*`，和回车键变蓝同一种颜色）。
行高不设 token：四行平分窗口高度（竖屏跟键盘同高 256dp，横屏 160dp）。

```
┌────┬──────┬────┬──────┐
│    │  ↑   │    │ 全选 │
│    ├──────┤    ├──────┤
│ ←  │ 选择 │ →  │ 剪切 │
│    ├──────┤    ├──────┤
│    │  ↓   │    │ 复制 │
├────┴──┬───┴────┼──────┤
│ 行首  │  行尾  │ 粘贴 │
└───────┴────────┴──────┘
```

| Token | 竖屏 | 横屏 | 理由 |
|---|---|---|---|
| `editPanelCommandColumnFraction` | 0.28 | 0.28 | 右侧命令列占面板宽度的比例：竖屏约 113dp，放得下图标加两个字 |
| `editPanelCenterColumnWeight` | 1.4 | 1.4 | 中间（↑ 选择 ↓）一列相对左右方向键（各 1）的宽度，「选择」两个字不挤 |
| `editPanelArrowIconSize` | 30dp | 26dp | 方向键只有 Material 的 chevron，字形只占框的一半，所以框要比按键图标（24 / 22dp）大 |
| `editPanelIconSize` | 22dp | 20dp | 带文字的格子里的图标 |
| `editPanelLabelTextSize` | 14sp | 14sp | 比功能键文字（16sp）小一级：图标和文字叠在一格里 |
| `editPanelIconLabelGap` | 2dp | 6dp | 竖排时紧贴；横排时隔开 |
| `editPanelLabelBesideIcon` | false | true | 横屏一行只有 40dp 高，图标和文字叠不下，改成左右并排 |

四个方向键长按连发，节奏和退格一致（`longPressTimeout` / `repeatInterval` 偏好）；行首行尾和命令不连发。

**九宫格拼音列**（`t9Column*`，`ime/compose/t9/T9PinyinColumn.kt`）：九宫格打字时盖在左侧那列键上的「选拼音」列表，
点一个拼音替换当前音节（`T9Assist.applySyllable`）。由 `KeyboardWindow` 用 ComposeView 挂在键盘上。

- **覆盖范围**：从第一行的 `,` 键开始，往下取 x 和宽度都相同的键，所以竖屏 `t9` 盖住 `, . ? !` 四行，
  到按键行为止，**不盖底行**（左下角的方案键照常可点）；横屏 `t9_land` 盖的是九宫格左边那列（宽 13，不是左侧数字小键盘）；
  横屏悬浮键盘用竖屏的 `t9`。
  键盘上没有这样一列（非九宫格布局）就不显示。
- **样式**：格子就是按键——`keyBack` 底、`keyCornerRadius` 圆角、按下 `highlightedKeyBack` / `highlightedKeyText`；
  列表四周内缩半个键距，边缘和下面的键身对齐；列表**不铺底色**：拼音列显示时键盘画布不画被盖住的键
  （`KeyboardRenderState.coveredArea`），露出的就是键盘本身的底，配色微调让底色半透明时也和周围一致。
- **文字**：`keyTextWeight`（500，和九宫格字母组同级：点拼音是在打字，不是控制）、最大 `keyLabelTextSize`，
  放不下时按步缩小到 `t9ColumnMinTextSize`，`zhuang` 在窄键（悬浮键盘里那列约 25dp 宽）上也不截断。
- **滚动**：`LazyColumn`，没有滚动条，关掉了过度滚动效果（`LocalOverscrollFactory provides null`）；
  原始输入一变就回到顶部。宽度不靠 `BoxWithConstraints`，空闲时不重组。

| Token | 竖屏 | 横屏 | 理由 |
|---|---|---|---|
| `t9ColumnItemHeight` | 36dp | 28dp | 比键身（45 / 34dp）矮：竖屏四行键的高度里露出五个多拼音，一眼看出能往下滑 |
| `t9ColumnItemSpacing` | 4dp | 4dp | 比键距小，读成一个列表 |
| `t9ColumnMinTextSize` | 8sp | 8sp | 长拼音最小缩到这里；比右上角提示还小，悬浮键盘的窄列也放得下 `zhuang` |
| `t9ColumnTextHorizontalPadding` | 2dp | 2dp | 左右只留一点，文字不贴圆角边就行，宽度尽量给字 |

### 2.6 语音听写

交互向 iOS 听写看齐：**点一下麦克风键开始，再点一下结束**，没有按住说话；长按麦克风键不做任何事。

| 事件 | 结果 | 已识别的文字 |
|---|---|---|
| 点麦克风键 | 开始听写。Rime 里没上屏的编码先按首选上屏（等于按了空格，打了的字不丢） | — |
| 听写中再点麦克风键 | 关麦，等识别服务定稿（最多 8 秒） | 定稿后上屏 |
| 3 秒没有人声 / 开始后 3 秒一直没人声 / 到单次最长时长 | 同上 | 同上 |
| 按下别的键、点面板里的键 | 立刻结束，不等定稿，按下的键照常生效 | 待定文字按当前样子上屏 |
| 光标被点到别处、键盘收起、换输入框 | 立刻结束 | 同上 |
| 出错（没开启、没凭证、网络、识别失败、定稿超时） | 结束，胶囊显示错误 2.5 秒 | 同上 |
| 没有录音权限 | Toast 提示并拉起权限页 | — |

规则只有一条：**识别出来的字永远保留**，要么等定稿，要么按当前样子上屏，从不丢弃，也不留下半截待定文字。
识别中的文字是输入框里带下划线的待定文字（`setComposingText`），中间结果也经过词库映射。

**胶囊**：强调色底、实心麦克风加三根随音量起伏的竖条，出错时换成划掉的麦克风加文字。
紧跟光标右边、和光标所在行垂直居中（光标位置来自 `CursorAnchorInfo`，按其矩阵换成屏幕坐标）；
编辑器不报光标、光标被键盘挡住或滚出屏幕、横屏全屏输入时，退到键盘左上角上方。胶囊不接收触摸。

**麦克风键**：听写开始到定稿之间是强调色胶囊加实心麦克风，外面一圈同色 24% 的光晕随音量向外扩最多 `voiceKeyLevelSpread`。
音量由录音的 RMS 算出，每 100ms 更新一次，变化不到一格（0.05）不重画。

**自动停止**（`VoiceActivityDetector`）：底噪取最近 5 秒里最安静的一包，比底噪高 10dB 算人声（阈值限制在 −55 到 −30 dBFS 之间）；
前 300ms 只估底噪；连续两包过阈值才算开口，免得按键的咔哒声被当成说话。

| Token | 竖屏 | 横屏 | 理由 |
|---|---|---|---|
| `voicePillHeight` | 28dp | 24dp | 大约一行字高，看起来是这一行的一部分 |
| `voicePillHorizontalPadding` / `voicePillContentGap` | 9dp / 4dp | 同左 | |
| `voicePillIconSize` | 16dp | 14dp | |
| `voicePillTextSize` / `voicePillMessageMaxLines` | 13sp / 2 | 12sp / 2 | 错误文字，字重 `panelLabelWeight` |
| `voicePillCaretGap` | 4dp | 4dp | 光标到胶囊左边 |
| `voicePillScreenMargin` | 8dp | 8dp | 离屏幕两边和键盘的最小距离 |
| `voicePillShadowElevation` | 3dp | 3dp | 和按键气泡一样浮在 App 上 |
| `voiceLevelBarWidth` / `voiceLevelBarGap` | 2.5dp / 2dp | 同左 | |
| `voiceLevelBarMinHeight` / `voiceLevelBarMaxHeight` | 3dp / 12dp | 3dp / 10dp | |
| `voiceKeyLevelSpread` / `voiceKeyLevelAlpha` | 3dp / 0.24 | 同左 | 光晕不超出键距 |

---

## 3. 可变部分：主题 yaml 还能配什么

**可以配：**

- **配色**：由 `KeyboardColorRoles` 的角色生成浅色 / 深色两套（§0.1），颜色键名沿用 yaml 时代的 `preset_color_schemes`。
  包括自定义键名（如 `func_key_back_color`）供按键引用。提示色 `key_symbol_color` 只剩 View 渲染在用（按 §2.3 的 50% 规则取值），Compose 画布不读，见 §3.1。
  运行时的暖度 / 亮度 / 不透明度滤镜在 App 里调，主题里保持中性。
- ~~字体文件~~：字体已内置（§0.3），`style` 下的 `*_font` 不再读取，用户目录的 `fonts/` 也不再使用。
- **键盘布局**：`preset_keyboards` 里有哪些键盘、每个键的动作（`click` / `long_click` / `swipe_*`）、宽度百分比、
  `label` / `label_symbol`、按键引用哪个配色键、`hide_in_landscape`、`_land` 横屏专用键盘。
- **行高比例**：按键 / 行的 `height`（相对 `keyboard_height` 等比缩放），用于地球行这类矮一截的行。
- **回车文字**：`enter_labels`。
- **`preset_keys`、`liquid_keyboard`**：按键动作定义和符号面板内容。

**不可以配（Compose 接手后忽略）：**

- 所有尺寸和间距：`key_height` 以外的 `*_gap`、`round_corner`、`keyboard_padding*`、`keyboard_corner_radius`、
  `candidate_padding`、`hilited_candidate_padding`、`candidate_corner_radius`、`preedit` 下的内边距和圆角。
- 所有字号：`*_text_size`、`preedit/foreground/font_size`、`window/foreground/*`。
- 提示的位置：`key_symbol_offset_*`、`key_hint_offset_*`、`key_text_offset_*`、`key_press_offset_*`。
- 按下气泡的尺寸：`popup_*`、`preview_*`。
- 按键上单独写的 `key_text_size` / `symbol_text_size` / `round_corner`（图标键尺寸由 `keyIconSize` 统一）。

### 3.1 yaml 和 token 冲突时听谁的

一条规则：**颜色跟 yaml，尺寸跟 token；token 没有管到的尺寸仍然跟 yaml。** 按键画布逐项这样处理：

| yaml 字段 | Compose 按键画布 | 理由 |
|---|---|---|
| 配色：`key_back_color`、`key_text_color`、`hilited_*`、`on_*` / `off_*`、`key_border_color`，以及按键上单独写的这些颜色 | 照旧，经 `Key.getBackgroundDrawable()` / `getTextColor()` / `getBorderColor()` 取色 | 配色是可变的一半 |
| `enter_key_action_back_color` / `hilited_enter_key_action_back_color` / `enter_key_action_text_color` | 照旧，编辑框要求前往 / 搜索 / 发送 / 完成时回车键用它 | 同上 |
| `key_symbol_color`（含 `hilited_` / `on_` / `off_` 变体） | **不读**：提示色 = 这个键此刻的文字色 × `keySymbolAlpha` | 颜色类里唯一的例外。§2.3 已经把提示色定义成字色的函数，按下、开关、深色、回车强调色时自动跟着变；单独配一个固定色正是原来提示看不见的原因 |
| 背景是图片（`.png` / `.9.png`） | 照旧画，不裁圆角 | 图片皮肤自带形状 |
| `round_corner`（style、键盘、按键上） | 忽略，用 `keyCornerRadius` | 尺寸 |
| `horizontal_gap` / `vertical_gap`（style、键盘上） | 画键身时忽略，用 `keyHorizontalGap` / `keyVerticalGap` 从触摸格内缩；触摸格本身不变 | 尺寸。键距本来就算触摸区，改键距不影响命中 |
| `key_text_size` / `key_long_text_size` / `label_text_size`（style、按键上） | 忽略，按键面内容分档（§2.3） | 尺寸 |
| `symbol_text_size`（style、按键上） | 忽略，用 `keySymbolTextSize` | 尺寸 |
| `key_text_offset_*` / `key_symbol_offset_*` / `key_hint_offset_*` / `key_press_offset_*` | 忽略；提示位置用 `keySymbolInsetTop` / `keySymbolInsetEnd` | 尺寸 |
| `key_border`（style、键盘、按键上） | **照旧生效**，宽度按 dp，颜色跟 `key_border_color` | token 里没有描边宽度，也就没有冲突；设计本身不用描边，iOS 主题写的是 0 |
| `keyboard_height*`、按键 `width`、行 `height`、`hide_in_landscape` | 照旧生效（`Keyboard` 据此算触摸格） | 布局是可变的一半；键盘总高见下 |
| `key_font` / `symbol_font` / 所有 `*_font` | 忽略，用内置思源黑体 + 字重 token | 字体内置（§0.3） |
| 浮动键盘缩放（App 设置，不是 yaml） | 字号、图标、提示内边距乘缩放；键距、圆角不缩 | 小窗里字要跟着缩，键距再缩键就挤成一片 |

`keyboard_height` / `keyboard_height_land` 暂时保留可配：键盘总高和屏幕、导航栏、用户习惯都有关，
等 Compose 版有「键盘高度」设置项之后再收回。

---

## 4. 键位方案：iOS 的键面 + 默认主题的效率

以用户的小鹤双拼主题（trime-config `tools/gen_ios_theme.py`）为准。每一条都写明在「键面干净」和「功能可发现」之间怎么取舍。

### 4.1 中文 26 键（`default`）

```
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐   右上角淡色提示 = 上滑 / 长按
│ q¹│ w²│ e³│ r⁴│ t⁵│ y⁶│ u⁷│ i⁸│ o⁹│ p⁰│   数字
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
  ┌───┬───┬───┬───┬───┬───┬───┬───┬───┐
  │a▣ │ s-│ d/│ f：│ g；│ h（│ j）│k“”│ l@│   ▣ = 全选图标（仅长按）
  └───┴───┴───┴───┴───┴───┴───┴───┴───┘
┌────┬───┬───┬───┬───┬───┬───┬───┬────┐
│ ⇧  │ z、│ x✂│ c⧉│ v📋│ b？│ n！│ m…│ ⌫  │   ✂ ⧉ 📋 = 剪切 / 复制 / 粘贴图标（仅长按）
└────┴───┴───┴───┴───┴───┴───┴───┴────┘
┌────┬───┬──────────────┬───┬────┬───────┐
│123 │ ， │        空格         │ 。 │ 换行  │
└────┴───┴─────────────────────┴───┴───────┘
  ⌨         ☺         📋         ✎         🎤    方案 / 表情 / 剪贴板 / 编辑 / 语音，透明底，横屏隐藏
```

| 条目 | 方案 | 键面干净 vs 可发现 |
|---|---|---|
| 数字 | 第一行上滑或长按出 `1`–`0`；`123` 层第一行也是数字；`123` 长按直接进数字九宫格；数字输入框自动切数字键盘 | 提示印在右上角（可发现），不加数字行（干净） |
| `，` `。` | 空格两侧独立键，中文态经 Rime 变全角，英文键盘上是半角直接上屏 | 零切换；标点键和字母键同色，不增加颜色层级 |
| 其他中文标点 | 第二、三行上滑 / 长按：`- / ： ； （ ） “” @`、`、 ？ ！ …`，顺序照 iOS `123` 层第二行，iPhone 用户在 `123` 层找惯的位置对得上。全角标点用 `text` 动作直接上屏（先把正在打的拼音上屏），`“”` 成对上屏、光标落在中间 | 每键只印一个字符 |
| 全选 / 剪切 / 复制 / 粘贴 | `a` `x` `c` `v` 长按，和默认主题同位置；键面画 Material 图标 | 图标比 `全選` 两个字干净；**只给长按不给上滑**，误触代价太高 |
| 撤销 / 光标 | 撤销不上键面（系统快捷键和剪贴板面板覆盖）；光标靠空格左右拖动，退格左拖删除 | 不加方向键（干净），靠手势（没有提示，这是有意识放弃的可发现性，写进 README） |
| 退格 / 回车 | 退格 15%（与 Shift 同宽，去掉两侧空隙）；换行 22% | 最常用的功能键最大 |
| Shift | 保留，15% | iOS 位置不动；中文态下它的作用有限，但删掉会破坏 iOS 手指记忆 |
| 中英切换 | 左下角键盘图标点一下切中文 / 英文键盘，长按出方案菜单；不再有 `ZH` / `EN` 键 | 入口只留一个；当前语言看键面（中文键盘的提示是全角） |
| emoji / 剪贴板 / 编辑面板 | 最底下一行中间三个等宽键 | 给 `，` `。` 腾位置；剪贴板和方向键不用先回到工具栏（打字时工具栏被候选占着） |
| 「更多」面板 | `123` 上滑（颜文字、符号表、剪贴板历史） | 不印提示（功能键不印），README 说明 |
| 切方案 | 左下角键长按弹出方案选单（双拼 / 九宫格…），不再弹系统输入法选择器 | 位置同 iOS 地球键；选单比轮换直接 |

### 4.2 英文 26 键（`english`）

同一位置，所有提示变半角并**直接上屏**（不经 Rime）；`z` 换成英文里最常用的撇号 `'`，`m` 换成 `#`。
底部 `,` `.` 也是半角。

### 4.3 符号层、数字键盘、九宫格

- `123` / `#+=` 层（中英各一套）布局不变，只跟随 §2 的尺寸；底部去掉 emoji 键，空格加宽。
- 九宫格 `t9` / `t9_land` **只对齐样式、不改键位**（九宫格是用户按截图定的）；竖屏 `t9` 最底下一行和 26 键共用，横屏 `t9_land` 没有这一行。
  打字时左列变成拼音列，见 §2.5。
- 数字键盘跟 26 键共用最底下一行，有 emoji 入口。

---

## 5. View 渲染做不到、需要 Kotlin 改动的

「Compose」一列是按键画布（`KeyboardCanvas`）接手后的状态；标「—」的属于候选栏、预编辑区或按键逻辑，不在画布里做。

| 设计 | View 现状 | 过渡期怎么办 | Compose |
|---|---|---|---|
| 提示放右上角（`keySymbolInsetEnd`） | 只能画在键顶**正中** + 统一的横向偏移（sp），不同宽度的键偏移后位置不同 | 所有带提示的键都是等宽字母键，统一偏移 `10`；功能键用 `label_symbol: ' '` 关掉提示 | 已实现：右对齐到键身右边内缩 `keySymbolInsetEnd`、顶边内缩 `keySymbolInsetTop`，和键宽无关；空白提示不画 |
| 提示色 = 字色 × 50%（`keySymbolAlpha`） | 提示色只能从配色读一个固定色 | 主题生成脚本算好等效色写进 `key_symbol_color` | 已实现：按下、开关、深色时跟着字色变；不再读 `key_symbol_color` |
| 两字提示改图标 | `label_symbol: ic@...` 已支持（`drawIcon` 按提示字号画） | 直接用 | 已实现：图标边长 = 提示字号，放在同一个角 |
| 按键字号分档（`keyTextSize` / `keyLabelTextSize` / `keyIconSize`） | 按键上的 `key_text_size` 决定，图标键要逐个写 | 生成脚本逐键写 | 已实现：按键面内容自动分档（§2.3），按键上的尺寸忽略 |
| 功能键按下不弹气泡 | 气泡由「按键弹出预览」全局开关控制，功能键也会弹（显示标签首字） | 暂时接受；Compose 版按键级别区分 | —（按键逻辑）。按住换亮色由配色实现，已验证 |
| 候选注释间距 2dp（`candidateCommentGap`） | 写死 1dp | 差 1dp，不影响 | — |
| 九宫格预编辑字号（`preeditT9TextSize`） | 用注释字号 | 注释改成 14sp 后九宫格拼音也跟着变 14sp，比设计大 1sp | — |
| 按下下沉（`key_press_offset_*`） | 字段解析了但没画 | 设计里本来就不用 | 不画，字段忽略 |
| 上滑 / 长按出全角标点 | 需要 Shift 的符号（`: ? ! ( ) "`）没有 Android 键码，Trime 按文本模拟按键，实测中文态打出的是半角 | 主题里写 `{text: "？"}` 这类全角字符；Kotlin 侧可以让 `onText` 的 ASCII 分支走 Rime 标点转换，统一中英文键盘的写法 | — |
| 候选行 48dp 且不给注释单独留高度 | 候选栏高度 = `candidate_view_height + comment_height` | 过渡期保持 52 + 12，Compose 版改 48 | — |
| 横屏字号降一级 | 字号没有 `_land` 版本（除候选栏高度） | 横屏只能跟竖屏同字号；Compose 版按 `Landscape` token | 按键已实现：字母 20sp、功能键 14sp、九宫格字母组 18sp、图标 20dp、提示 9sp |
| 横屏键距 6dp | 键距没有 `_land` 版本 | 同上 | 按键已实现：横屏纵向键距 6dp |
