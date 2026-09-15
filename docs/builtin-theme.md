# 内置主题

键盘主题不再从 yaml 加载，而是直接写在代码里。App 只有这一套主题：现代 Android（Material）外观，
键位来自原来配置仓库里的 `ios.trime.yaml`。字体（思源黑体）打包进 APK。设计语言见 `docs/ime-design-system.md` §0。

## 主题在哪

`app/src/main/java/com/osfans/trime/data/theme/builtin/`：

| 文件 | 内容 |
| --- | --- |
| `BuiltinTheme.kt` | 组装入口，`BuiltinTheme.theme` 就是全 App 用的 `Theme` |
| `BuiltinKeyboards.kt` | 各个键盘的键位：`default`（中文）、`english`、`t9`、`t9_land`、`symbols`、`symbols2`、`symbols_en`、`symbols2_en`、`number` |
| `BuiltinKeys.kt` | 预设键 `presetKeys`：按键里 `key("ios_shift")` 这种名字指向的动作 |
| `BuiltinColors.kt` | 配色：`light`、`dark` 两套（由 `KeyboardColorRoles` 生成），外加 `fallbackColors` |
| `KeyboardColorRoles.kt` | 颜色角色和「角色 → 颜色键」的对照表，固定配色和壁纸取色共用 |
| `BuiltinStyle.kt` | 尺寸、字号，候选窗和预编辑条的样式（不含字体） |
| `BuiltinLiquid.kt` | 符号 / 表情面板的标签页和底栏 |

模型类在 `data/theme/model/`，构造参数都带默认值。内置主题只写和默认值不同的字段，所以没写出来的字段就是默认值。

## 改键位

打开 `BuiltinKeyboards.kt`，找到对应键盘，按行注释找到那一行：

```kotlin
// 第 1 行：q w e r t y u i o p
TextKey(click = key("q"), longClick = key("1"), swipeUp = key("1")),
```

- 一行内所有键的 `width` 加起来是 100（即键盘宽度的百分比）。没写 `width` 的键用键盘的 `width`，
  没有 `click` 的键是空白占位。
- 动作槽位：`click`、`longClick`、`swipeUp`、`swipeDown`、`swipeLeft`、`swipeRight`、`doubleClick` 等，
  和 `KeyBehavior` 一一对应。
- 动作的三种写法：
  - `key("…")`：预设键名（`ios_backspace`，定义在 `BuiltinKeys.kt`）、keysym（`Escape`、`q`、`KP_1`）或直接文本；
  - `commit("[")`：不经 Rime，直接上屏，键面显示同样的字，可以用 `label =` 另给键面文字；
  - `text("：")`：按键序列经 Rime 发送（可以写 `{Left}` 这种 keysym），同样可以带 `label`。
- 功能键底色写 `keyBackColor = FUNC_KEY_BACK, hlKeyBackColor = FUNC_KEY_HILITED_BACK`。
- `bottomBar` 是竖屏最底下那行，好几个键盘共用，改一处全都生效，见下一节。
- 只在一个方向出现的键：`hideInLandscape` 横屏不排版，`hideInPortrait` 竖屏不排版（整行都藏掉时这一行也没了）。
  `widthLand` 是横屏宽度，不写（0）就用 `width`；横屏多出一个键时，由旁边的键写 `widthLand` 让出宽度，
  这样横竖屏两种排法每行都还是 100，竖屏布局一点不动。
- 键盘 id 不能随便改：输入方案会按 id 选键盘，`select = "symbols"` 这类预设键也按 id 切换；`t9_land` 靠
  「同名 + `_land`」在横屏自动替换 `t9`。

加新的预设键就在 `BuiltinKeys.kt` 里加一条 `"名字" to PresetKey(...)`，字段和原来 yaml 的 `preset_keys` 一样，只是改成了驼峰命名。

## 底行按钮

`BuiltinKeyboards.kt` 的 `bottomBar`，接在 `default`、`english`、`t9`、`symbols`、`symbols2`、`symbols_en`、`symbols2_en`、
`number` 的最后。`t9_land` 没有这一行。一行 44（其余行 53），透明底只画图标，按下时叠功能键的按下色；
每个键都带 `hideInLandscape`，横屏整行不排版。

| 位置 | 宽度 | 预设键 | 点击 | 长按 | 图标 |
| --- | --- | --- | --- | --- | --- |
| 左下角 | 12 | `ios_schema` | 发 `MENU`：弹出方案选单（已启用的方案单选，外加「其他输入法」按钮） | `IME_switch`：系统输入法选择器 | `ic@keyboard-outline` → Keyboard |
| 空白 | 8 | — | | | |
| 中间 | 20 | `ios_emoji` | 表情面板 | — | `ic@emoticon-outline` → EmojiEmotions |
| 中间 | 20 | `ios_clipboard` | 命令 `clipboard_window`：剪贴板窗口 | — | `ic@clipboard-outline` → ContentPaste |
| 中间 | 20 | `ios_edit` | 命令 `edit_panel`：编辑面板（方向键、选择、行首行尾、全选剪切复制粘贴） | — | `ic@cursor-text` → Edit（铅笔，和工具栏同一个） |
| 空白 | 8 | — | | | |
| 右下角 | 12 | `ios_mic` | 语音输入（按住说话） | — | `ic@microphone-outline` → Mic |

- 两个角保留原来地球 / 麦克风的 12% 格子，图标位置不变；中间三个等宽 20%（竖屏约 82dp 宽），和两角之间各空 8%，
  五个图标的间距是 24 / 20 / 20 / 24%，看起来均匀，每个触摸区都比 44dp 大。
- 剪贴板和编辑面板用单独的 `ios_clipboard` / `ios_edit`，不直接写 `clipboard_window` / `edit_panel`：那两个预设键的
  `label` 是文字，写在按键上的 `label` 在英文（ascii）状态下会被预设键的文字顶掉，键面就从图标变成「剪贴」「编辑」。
- 原来的 `ios_globe`（`Control+Shift+1` 轮换方案）已删除：方案多于两个时轮换不如直接选。

### 横屏的麦克风键

横屏没有底行，麦克风键挪进最后一行，用同一个预设键 `ios_mic`（所以听写时同样画成激活样式），外观和同一行的功能键一样：

| 键盘 | 横屏最后一行 | 让出宽度的键 |
| --- | --- | --- |
| `default`、`english` | 符号键 12、逗号 10、**麦克风 10**、空格 24、句号 10、中英 12、回车 22 | 空格 34 → 24 |
| `symbols`、`symbols2`、`symbols_en`、`symbols2_en` | ABC 12、**麦克风 10**、空格 56、回车 22 | 空格 66 → 56 |
| `number` | ABC 15、**麦克风 10**、0、逗号、回车（各 25） | ABC 25 → 15，数字列不动 |
| `t9_land` | 左边数字区不变；`!` 9、**麦克风 12**、空格 13、ZH 13、回车 13 | 空格 25 → 13 |

- 前几个键盘的麦克风是共用的 `landscapeMic`（带 `hideInPortrait`），让宽度的键写 `widthLand`；`t9_land` 只在横屏用，直接改宽度。
- 都放在空格左边：空格和右手边的中英、回车挨在一起，最常按的几个键不被隔开；麦克风一次听写只按一两下，放远一点也不碍事。
- `t9_land` 的麦克风正好在 PQRS 下面、空格在 TUV 下面，最后一行和上面的列对齐。

## 改工具栏

没有在打字时，候选栏那一行显示工具栏。它配置在 `BuiltinTheme.kt` 的 `toolBar`（模型见 `data/theme/model/ToolBar.kt`），
现在从左到右是：

| 位置 | 按钮 | 动作 |
| --- | --- | --- |
| 最左 | 「…」 | `primaryButton` 留空时的内置按钮，打开方案 / 开关窗口 |
| 左侧 | 光标 `ic@cursor-text` | 预设键 `edit_panel`：打开编辑面板（方向键、选择、行首行尾、全选剪切复制粘贴） |
| 左侧 | 剪贴板 `ic@clipboard-outline` | 预设键 `clipboard_window` |
| 最右 | 收起 `ic@menu-down` | 预设键 `Hide`；在这个按钮上往下滑也会收起键盘 |

- `buttons` 的**第一个**按钮固定占最右边的位置，也就是原来收起箭头的位置，下滑收起键盘也跟着这个位置走。
- 其余按钮由 `buttonsAlignment` 决定：`START` 紧跟在「…」后面按列表顺序排（现在的写法），`END`（默认）从右往左排。
  按钮之间和两端空 `buttonSpacing` dp，现在是 0，因为按钮本身已经是栏高的正方形。
- 按钮样式由 `toolBarButton()` 统一：24dp Material 图标、候选字色、按下时圆形色块。
  图标名仍写 Community Material 的名字（`ic@` 加上 materialdesignicons 的名字，横线或下划线都行），
  实际画的是 `ime/compose/theme/ImeIcons.kt` 里映射到的 Material 图标；加新按钮时记得在那里加映射。
- `action` 写预设键名。加按钮就在 `buttons` 里多写一条 `toolBarButton("ic@…", "预设键")`；
  `BuiltinThemeTest` 会检查引用的预设键存在。
- 编辑面板本身（`ime/edit/EditPanelWindow.kt`、`ime/compose/edit/EditPanel.kt`）不在主题里配置，
  尺寸见 `docs/ime-design-system.md` §2.5。

## 改配色

两套方案不再逐个写颜色：`BuiltinColors.kt` 用 `KeyboardColorRoles.NeutralLight` / `NeutralDark` 生成。
改色调就改这两组角色的值；想让某个颜色键换一个角色，改 `KeyboardColorRoles.colors()` 里的对照表（KDoc 里有表格）。
按下色由 on 色 12% 叠在底色上自动算出。颜色名和原来 yaml 的 `preset_color_schemes` 相同；方案里没写的颜色先查
`fallbackColors`，再查 `ColorManager` 里内置的兜底链。`BuiltinThemeTest` 检查两套方案颜色名一致，
`KeyboardColorRolesTest` 检查对比度。

要改的只是整体冷暖、亮度、底色透明度时不必改这里：设置页的「配色微调」三个滑块会给所有颜色叠一层滤镜，
见 `ColorTint`。

## 尺寸与字体

`BuiltinStyle.kt` 里的尺寸大多只是历史数据：键盘已经改成 Compose 渲染，尺寸以 `ime/compose/theme/ImeTokens.kt`
为准，见 `docs/ime-design-system.md`。

字体不属于主题：Noto Sans SC 可变字体（思源黑体同一套字形）放在 `app/src/main/fontAssets/fonts/`，由
`data/theme/SourceHanSans.kt` 提供。放在单独的 assets 目录是因为 `src/main/assets` 里的文件都会进 checksums、
同步时被拷到用户目录；字体不压缩存放，各个字重直接映射 APK。用户目录的 `fonts/` 不再读取。
字重见 `docs/ime-design-system.md` §0.3，许可证（OFL 1.1）随字体放在同一目录，并列在 App 的开源许可页。

## 深浅色

偏好 `ThemePrefs.dayNightMode`（key `day_night_mode`），在设置 → 键盘样式 →「深浅色」里三选一：

| 选项 | 用哪套配色 |
| --- | --- |
| 跟随系统（默认） | 系统夜间模式时用 `dark`，否则用 `light` |
| 浅色 | 总是 `light` |
| 深色 | 总是 `dark` |

`ColorManager` 监听这个偏好和系统夜间模式，切换后重建键盘视图。

### 跟随壁纸取色

偏好 `ThemePrefs.followWallpaper`（key `theme_follow_wallpaper`，默认关），在「深浅色」下面，只在 Android 12+ 显示。

- 打开后键盘不用 `light` / `dark`，改用壁纸调色板生成的 `wallpaper_light` / `wallpaper_dark`
  （Material 角色到键盘角色的对照在 `KeyboardColorRoles.fromMaterial`）；用浅色还是深色版由**系统**深浅色决定，
  所以「深浅色」三选一在设置页里置灰。配色微调滑块照常叠加。
- 换壁纸或系统切深浅色时，输入法在 `onConfigurationChanged`、`onCreateInputView`、`onWindowShown` 重新取色，颜色变了才重建。

### App 界面和键盘用同一套设置

设置 App（`TrimeTheme`）不再有自己的「用户界面模式」：

| 设置 | 键盘 | App |
| --- | --- | --- |
| 跟随壁纸取色 开 | 壁纸配色，深浅跟随系统 | Material You 动态配色，深浅跟随系统 |
| 关，深浅色 = 跟随系统 / 浅色 / 深色 | `light` / `dark` 按选择 | zinc 中性配色，深浅按同一个选择 |

App 的深浅色由 `ThemePrefs.appNightMode` 经 `AppCompatDelegate.setDefaultNightMode` 设置（`MainActivity` 启动时和主题设置页改动时）。
原来「高级」里的 `ui_mode` 已删除：迁移时只删这个 key，不拿它改键盘的深浅色，升级后 App 跟着键盘原有的选择走。

### 旧偏好迁移

`DayNightMigration` 在 `ThemePrefs` 创建时执行一次：

1. 存过 `follow_system_day_night = true` → 跟随系统；
2. 否则，如果存过 `normal_mode_color`：该方案是深色（按底色亮度判断）→ 深色，否则 → 浅色。
   旧的 `ios_light` / `ios_dark` 按 `BuiltinColors.legacyIds` 对应到现在的方案；不认识的方案 id 按浅色处理，因为旧版找不到方案时也会退回浅色的 `default`；
3. 两个都没存过 → 跟随系统（也就是默认值，不写入）。

之后删掉 `follow_system_day_night`、`normal_mode_color` 和 `selected_theme` 三个旧 key，所以只会迁移一次。
如果已经有 `day_night_mode`，就保留它，只删除旧 key。规则由 `DayNightMigrationTest` 覆盖。

## 已经移除的机制

- 不再读取任何 `*.trime.yaml`，不再让 Rime 部署主题文件，也没有兜底主题。用户目录里残留的
  `ios.trime.yaml` / `trime.yaml` 和 `build/` 里的编译产物都不会被读到，可以删掉。
- APK 不再内置 `assets/shared/trime.yaml`。升级后，`DataManager.sync` 会按 checksums 的差异删掉手机上的
  `shared/trime.yaml`。
- 设置页的「主题」「配色」选择和两个选择对话框都已删除；`selected_theme`、`normal_mode_color`、
  `follow_system_day_night` 三个偏好已删除（见上面的迁移规则）。
- 老配置里可能还有换主题相关的按键，它们现在都**什么都不做，只打一行日志**，不会崩溃：
  `set_theme`、`set_color_scheme`、`PROG_RED`（原来会弹配色选择），以及 `SETTINGS` 的 `theme` / `color` 选项。
- 工具栏开关窗口里的「键盘样式」改为打开键盘样式设置页。
- `Theme.decode` 和各模型的 yaml 解析代码已删除。`util/yaml` 只保留语音词库（`VoiceVocabulary`）和按键音效
  （`SoundEffect`）还在用的部分。

## 验证

- `BuiltinThemeTest`：检查键盘齐全、有一深一浅两套配色，按键引用的预设键、切换目标和面板都存在。
- `BuiltinKeyboardsSnapshotTest`：钉住每个键盘每个键的宽高、键面文字、提示和动作；只改外观时它必须不变。
  有意改键位后用 `UPDATE_SNAPSHOT=1 ./gradlew :app:testDebugUnitTest` 重新生成快照。
  改完键位或配色先跑 `./gradlew :app:testDebugUnitTest`。
- 内置主题最初由一次性脚本从 Rime 部署后的 `build/ios.trime.yaml` 生成。提交
  「把 iOS 主题生成成内置 Kotlin 代码…」里的 `BuiltinThemeEquivalenceTest` 断言了 `Theme.decode(yaml)`
  与内置主题完全相等；这个测试已经随 decode 一起删除，需要时可以回到那个提交查看。
