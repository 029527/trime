# 内置主题

键盘主题不再从 yaml 加载，而是直接写在代码里。App 只有这一套主题：iOS 风格，原来是配置仓库里的
`ios.trime.yaml`。字体文件仍然放在用户目录，不打包进 APK。

## 主题在哪

`app/src/main/java/com/osfans/trime/data/theme/builtin/`：

| 文件 | 内容 |
| --- | --- |
| `BuiltinTheme.kt` | 组装入口，`BuiltinTheme.theme` 就是全 App 用的 `Theme` |
| `BuiltinKeyboards.kt` | 各个键盘的键位：`default`（中文）、`english`、`t9`、`t9_land`、`symbols`、`symbols2`、`symbols_en`、`symbols2_en`、`number` |
| `BuiltinKeys.kt` | 预设键 `presetKeys`：按键里 `key("ios_shift")` 这种名字指向的动作 |
| `BuiltinColors.kt` | 配色：`ios_light`、`ios_dark` 两套，外加 `fallbackColors` |
| `BuiltinStyle.kt` | 尺寸、字号、字体文件名，候选窗和预编辑条的样式 |
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
- `bottomBar` 是竖屏最底下那行（地球 / emoji / 麦克风），好几个键盘共用，改一处全都生效。
- 键盘 id 不能随便改：输入方案会按 id 选键盘，`select = "symbols"` 这类预设键也按 id 切换；`t9_land` 靠
  「同名 + `_land`」在横屏自动替换 `t9`。

加新的预设键就在 `BuiltinKeys.kt` 里加一条 `"名字" to PresetKey(...)`，字段和原来 yaml 的 `preset_keys` 一样，只是改成了驼峰命名。

## 改配色

打开 `BuiltinColors.kt`，两套方案各是一张 `"颜色名" to "0xRRGGBB"` 的表。颜色名和原来 yaml 的
`preset_color_schemes` 相同；值也可以写成另一个颜色名，引用它。方案里没写的颜色先查 `fallbackColors`，
再查 `ColorManager` 里内置的兜底链。**两套方案的颜色名要保持一致**，`BuiltinThemeTest` 会检查。

要改的只是整体冷暖、亮度、底色透明度时不必改这里：设置页的「配色微调」三个滑块会给所有颜色叠一层滤镜，
见 `ColorTint`。

## 尺寸与字体

`BuiltinStyle.kt` 里的尺寸大多只是历史数据：键盘已经改成 Compose 渲染，尺寸以 `ime/compose/theme/ImeTokens.kt`
为准，见 `docs/ime-design-system.md`。

字体只写文件名（`MiSans-Medium.ttf`、`MiSans-Regular.ttf`），由 `FontManager` 到用户目录的 `fonts/` 里找，
文件由配置仓库通过 Git 同步过来。每个文件约 8MB，所以刻意不打进 APK。找不到文件时回退系统字体。
部署成功后会清一次字体缓存，同步换了字体文件也能生效。

## 深浅色

偏好 `ThemePrefs.dayNightMode`（key `day_night_mode`），在设置 → 键盘样式 →「深浅色」里三选一：

| 选项 | 用哪套配色 |
| --- | --- |
| 跟随系统（默认） | 系统夜间模式时用 `ios_dark`，否则用 `ios_light` |
| 浅色 | 总是 `ios_light` |
| 深色 | 总是 `ios_dark` |

`ColorManager` 监听这个偏好和系统夜间模式，切换后重建键盘视图。

### 旧偏好迁移

`DayNightMigration` 在 `ThemePrefs` 创建时执行一次：

1. 存过 `follow_system_day_night = true` → 跟随系统；
2. 否则，如果存过 `normal_mode_color`：该方案是深色（按底色亮度判断）→ 深色，否则 → 浅色。
   不认识的方案 id 按浅色处理，因为旧版找不到方案时也会退回浅色的 `default`；
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
  改完键位或配色先跑 `./gradlew :app:testDebugUnitTest`。
- 内置主题最初由一次性脚本从 Rime 部署后的 `build/ios.trime.yaml` 生成。提交
  「把 iOS 主题生成成内置 Kotlin 代码…」里的 `BuiltinThemeEquivalenceTest` 断言了 `Theme.decode(yaml)`
  与内置主题完全相等；这个测试已经随 decode 一起删除，需要时可以回到那个提交查看。
