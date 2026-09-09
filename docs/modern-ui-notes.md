# 应用界面现代化改造笔记（Compose + Material 3）

分支 `feature/modern-ui`，基于 `custom`。本轮只做「地基 + 打样」：Compose 接入、主题层、
Compose 版 preference 渲染器，以及两个已迁移页面（首页、虚拟键盘设置）。
**后续 agent 请照本文的约定迁其余页面。**

范围只限 **应用界面**（`ui/` 下的设置 App）。键盘视图 `ime/`、librime 绑定 `core/`、
主题 yaml 解析 `data/theme/` 一律不动。

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

---

## 2. 导航方案：保留 Fragment 导航图，Compose 屏幕做它的目的地

**决定：不引入 `navigation-compose`，继续用现有的 `NavigationRoute.createGraph()`
（Navigation for Fragments），迁移过的页面写成宿主是 Fragment 的 Compose 屏幕。**

理由：

1. `NavigationRoute` 是 `@Parcelize` 的，键盘那边通过
   `MainActivity.EXTRA_SETTINGS_ROUTE` 直接把路由塞进 Intent 来跳设置页
   （`processIntent` → `navController.navigate(route)`）。换成 navigation-compose 要
   重做这条外部入口。
2. 还有 11 个页面没迁。它们依赖 `activityViewModels<MainViewModel>()` 和
   `findNavController()`。保留同一张图，它们**一行都不用改**就继续能进。
3. 两套导航栈（fragment 一套、compose 一套）会带来返回栈错乱；单栈最稳。

代价：拿不到 navigation-compose 的转场动画，用的还是
`util/NavController.kt` 里的 `navigateWithAnim`（objectAnimator）。等所有页面都迁完，
可以一次性换成 navigation-compose，届时只需要改 `NavigationRoute` 和 `MainActivity`。

### 谁负责画顶栏

`MainActivity` 的 XML `toolbar` 只服务**没迁的页面**。Compose 页面自己画
`LargeTopAppBar`，并且要全屏铺到状态栏下面，所以 activity 会：

- 隐藏 XML toolbar；
- 不再给根布局加 systemBars 的 margin（insets 交给 Compose 的 `safeDrawing` 处理）；
- 把 systemBars/ime 的底部间距改挂到 `TestInputPanel` 上，让试打字面板照样避开输入法。

判断依据是 `NavigationRoute.Companion.composeDestinations`：

```kotlin
private val composeDestinations = listOf(
    Main::class,
    VirtualKeyboard::class,
)
```

> **迁完一个页面，务必把它的路由加进这个列表**，否则会同时出现两个标题栏。

用目的地（而不是 fragment 的 `onStart`/`onStop`）来切 chrome，是为了避免前后两个
fragment 生命周期交错时顶栏闪一下。

---

## 3. 目录与命名约定

```
ui/theme/                     主题层（只给 App 界面用，跟键盘的 yaml 主题无关）
  Color.kt                    品牌配色（低版本回退用）
  Theme.kt                    TrimeTheme：动态取色 / 回退、深浅色、系统栏图标色
  Type.kt                     Typography
  Shape.kt                    Shapes

ui/compose/                   可复用的 Compose 地基
  ComposeFragment.kt          Compose 屏幕的 Fragment 宿主基类
  TrimeScreen.kt              统一 chrome：Large TopAppBar + 折叠 + edge-to-edge
  preference/
    PreferenceItems.kt        低层列表项与对话框（PreferenceRow / Switch / Slider / …）
    PreferenceDelegateScreen.kt  吃 PreferenceDelegateUi 模型的渲染器 + Fragment 基类

ui/main/MainScreen.kt         首页的可组合函数
ui/main/MainFragment.kt       首页的宿主（只有十几行）
```

命名约定：

- 页面的可组合函数叫 `XxxScreen`，**放在与旧 Fragment 同一个包**（例如
  `ui/main/settings/CandidatesSettingsScreen.kt`），文件名 `XxxScreen.kt`。
- 宿主类沿用旧的 Fragment 类名（`XxxFragment`），这样 `NavigationRoute` 不用改。
- 只在一个页面里用的可组合函数写成 `private`，多页面复用的才提到 `ui/compose/`。
- 可复用的列表项后缀统一是 `...PreferenceItem`。

---

## 4. 主题层怎么用

```kotlin
TrimeTheme { /* content */ }
```

`ComposeFragment` 已经帮你包好了，页面里**不要再包一层**。

- **动态取色**：Android 12+（API 31）走 `dynamicLightColorScheme` /
  `dynamicDarkColorScheme`，即 Material You 跟随壁纸；低版本回退到 `Color.kt` 里那套
  以 Rime 强调色 `#009BD1` 为种子的冷蓝品牌色。
- **深浅色**：`isSystemInDarkTheme()` 读的是 activity 的 configuration，而
  `MainActivity` 已经用 `AppCompatDelegate.setDefaultNightMode()` 把 `uiMode` 偏好项
  （AUTO/LIGHT/DARK）应用上去了，所以**这个偏好项自动生效，不用另外接线**。
- **系统栏图标颜色**：`TrimeTheme` 里的 `SideEffect` 按深浅色设置
  `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars`。旧页面回来时
  `MainActivity` 会把它设回 `false`（旧 toolbar 是深色的）。

`TrimeScreen` 提供统一 chrome：

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

---

## 5. Compose 版 preference 渲染器（本轮最大的杠杆）

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
| `StringList` / `UniversalStringList` | 行显示当前项 + M3 单选对话框 |
| `StringLike` | 纯行；点击行为由 `clickHandlers()` 按 key 注入 |

**存储格式没有任何改动。** 读写一律通过既有的 `PreferenceDelegate.getValue()/setValue()`，
SharedPreferences 的 key、类型、序列化方式全部照旧，用户的设置不会丢。

`enableUiOn`（依赖关系）保持旧行为——**置灰而不是隐藏**。实现方式：页面持有一个
`revision` 计数，注册 `PreferenceDelegateProvider.OnChangeListener`，任何一项变化就 +1，
所有行重新求值 `isEnabled()` 并重读自己的值。

### 迁一个设置页要写多少代码

绝大多数情况只要一个类：

```kotlin
class CandidatesSettingsFragment :
    PreferenceDelegateComposeFragment(AppPrefs.defaultInstance().candidates)
```

再把 `NavigationRoute.composeDestinations` 里加上 `CandidatesWindow::class`，完事。

标题按这个顺序取：构造参数 `titleRes` → `PreferenceDelegateOwner.title` → 导航图的
`label`。现有的 provider（含 `ThemeManager.prefs`，即 `ThemePrefs`）都带了 `title`，
所以一般不用传 `titleRes`；只有 provider 不是 `PreferenceDelegateOwner`、
或者 `title` 为 0 时才需要显式传。

需要给某个 `StringLike` 行挂点击行为（例如主题设置页的 `selected_theme`、
`normal_mode_color`，虚拟键盘页的 `custom_sound_effect_name`）：

```kotlin
@Composable
override fun clickHandlers(): Map<String, () -> Unit> = mapOf(
    "selected_theme" to { ThemePickerDialog.build(lifecycleScope, requireContext()).show() },
)
```

需要在模型渲染的行后面追加自定义内容，覆写 `Footer()`（或直接用
`PreferenceDelegateScreen(header = , footer = )`）。

需要完全手写的页面（首页、Profile、关于），用 `TrimeScreen` +
`PreferenceRow` / `PreferenceCard` / `PreferenceCategoryHeader` 这些积木自己拼，
参考 `ui/main/MainScreen.kt`。

---

## 6. 已迁移的页面

- **首页 `MainFragment`** → `MainScreen.kt`。分两组圆角卡片（数据 / 设置），
  行带图标；顶栏是部署、试打字两个图标按钮加一个溢出菜单（开发者、关于）。
- **虚拟键盘设置 `KeyboardSettingsFragment`** → 直接继承
  `PreferenceDelegateComposeFragment`，只覆写了 `clickHandlers()`。**这是模板页面。**

配套改动：

- `MainViewModel` 加了 `testInputRequests: SharedFlow<Unit>` / `requestTestInput()`。
  试打字面板 `TestInputPanel` 是 activity 布局里的 View，Compose 页面通过这个事件流
  请求它，而不是去摸 activity。
- `MainActivity` 按目的地切 chrome + insets（见第 2 节）。

### 行为上的两处小变化（有意为之）

1. 首页顶栏不再显示「返回」箭头（旧实现里那个箭头按下去是 `moveTaskToBack`，
   即最小化）。系统返回键行为不变。
2. 首页顶栏不再显示副标题 slogan（`R.string.trime_app_slogan`）。M3 的
   `LargeTopAppBar` 没有 subtitle 槽位，硬塞两行在收起状态会挤。
   如果要保留，等 material3 的 `LargeFlexibleTopAppBar` 稳定后再加。

`MainActivity.setupToolbarMenu()` 里那几个「部署 / 试打字 / 开发者 / 关于」菜单项现在
不会再显示了（只有首页会 `enableTopOptionsMenu()`，而首页已经迁走）。代码暂时留着，
等最后一个页面迁完，连同 `MainViewModel.topOptionsMenu` 一起删。

---

## 7. 剩余待迁移页面清单

按「好迁 → 难迁」排：

**A 类：一行搞定（纯 `PreferenceDelegateFragment`）**

| 页面 | 路由 | 备注 |
| --- | --- | --- |
| `GeneralSettingsFragment` | `General` | |
| `CandidatesSettingsFragment` | `CandidatesWindow` | |
| `ClipboardSettingsFragment` | `Clipboard` | |

**B 类：模型 + 少量钩子**

| 页面 | 路由 | 备注 |
| --- | --- | --- |
| `theme/ThemeSettingsFragment` | `Theme` | provider 是 `ThemeManager.prefs`；两个 `clickHandlers`：`selected_theme` / `normal_mode_color` |
| `AdvancedSettingsFragment` | `Advanced` | 只有 `onCreate/onDestroy` 里注册的两个 `PreferenceDelegate.OnChangeListener`，原样搬到新的 Fragment 即可 |

**C 类：手写页面**

| 页面 | 路由 | 备注 |
| --- | --- | --- |
| `AboutFragment` | `About` | 里面有跳 `License` 的入口 |
| `LicenseFragment` | `License` | aboutlibraries 数据，适合做成 `LazyColumn` |
| `DeveloperFragment` | `Developer` | |
| `ProfileSettingsFragment` | `Profile` | 479 行，最重的一个：文件选择、同步、备份还原，牵扯 `ActivityResultLauncher` 和 `ProgressFragment` |

**D 类：列表页（RecyclerView + splitties view DSL + toolbar 的编辑/删除按钮）**

| 页面 | 路由 | 备注 |
| --- | --- | --- |
| `schema/SchemaListFragment` | `SchemaList` | 多选模式接了 `OnBackPressedDispatcher` |
| `userdict/UserDictionaryFragment` | `UserDict` | |
| `hotwords/HotWordFragment` | `HotWords` | |

这三页依赖 `MainViewModel.enableToolbarEditButton/enableToolbarDeleteButton` 往 XML
toolbar 上挂按钮。迁到 Compose 后应改成 `TrimeScreen(actions = ...)` 直接画，
迁完后把 `MainViewModel` 里那几个 `toolbar*` LiveData 删掉。

**E 类：独立 Activity / 对话框（本轮完全没碰）**

- `ui/setup/`（首次引导，`SetupActivity` + ViewPager2 + `SetupPage`）
- `ui/main/LogActivity`、`ui/main/ClipEditActivity`
- `ColorPickerDialog` / `ThemePickerDialog` / `SoundEffectPickerDialog`
  （还是 AppCompat 的 `AlertDialog`，它们做的事不只是写一个偏好项，所以先留着；
  以后可以换成 M3 的 `AlertDialog` + `SingleChoiceDialog`）
- `ui/common/ProgressBarDialogIndeterminate`、`ui/common/PaddingPreferenceFragment`
  （最后一个 androidx.preference 页面迁完后，这两个和
  `data/prefs/PreferenceDelegateFragment.kt`、
  `PreferenceDelegateUi.createUi()`、`ui/main/settings/DialogSeekBarPreference.kt`、
  `EditTextIntPreference.kt` 一起删，然后就能从依赖里去掉 `androidx.preference`）

---

## 8. 后续 agent 必须遵守的约定

1. **动一个页面 = 改一个 `XxxFragment` + 新增一个 `XxxScreen.kt` + 在
   `NavigationRoute.composeDestinations` 里登记路由。** 三步缺一不可，
   漏了第三步会出现两个标题栏。
2. **不要改 `data/prefs/` 的数据模型**。`PreferenceDelegate` / `AppPrefs` 的 key、
   默认值、序列化方式是用户设置的存储格式，改了就是丢用户配置。要加设置项，
   照 `PreferenceDelegateOwner` 现有的写法加，Compose 渲染器会自动认。
   `PreferenceDelegateUi.createUi()`（androidx 那半边）在最后一个旧页面迁完前不要删。
3. **不要包第二层 `TrimeTheme`**，`ComposeFragment` 已经包好了。
4. **不要在 Compose 页面里自己画顶栏**，用 `TrimeScreen`；也不要自己算 insets，
   用它给的 `padding`。
5. **不要动 `ime/`、`core/`、`data/theme/`。**
6. **不要动 `.github/workflows/`、签名配置、版本号、`minSdk`。**
7. 每完成一块就提交一次，中文 commit message，末尾带
   `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`。
8. 交付前必须 `./gradlew spotlessApply` 再 `./gradlew :app:compileDebugKotlin` 通过。
   本机没有 NDK，**跑不了 `assembleDebug`，也装不了设备**，这两条是唯一的验证手段。
9. 新增字符串资源要同时补 `values-zh-rCN` 和 `values-zh-rTW`。本轮为了不碰翻译，
   「返回」「更多」的无障碍描述直接借用了 `androidx.appcompat.R.string.*`。

---

## 9. 已知遗留 / 后续可做

- **Predictive back 没做。** 它需要在 `AndroidManifest.xml` 的 `<application>` 上开
  `android:enableOnBackInvokedCallback="true"`，而这是**进程级**开关，Trime 是输入法，
  `InputMethodService` 在 API 33+ 也会受它影响（返回键怎么收起键盘）。本机装不了
  NDK、跑不了真机，没法验证输入法那半边不出问题，所以本轮**有意没开**。
  等能上真机验证时再开；开了之后 fragment 转场和 Compose 的
  `PredictiveBackHandler` 才会有动画。
- `MainActivity` 上的 `WindowInsetsAnimationCompat.Callback` 用的是
  `DISPATCH_MODE_STOP`，输入法弹出的动画 insets 不会往 Compose 子树传。
  目前 Compose 页面里没有内联输入框（文本输入都在对话框里），影响不大；
  以后如果 Compose 页面要跟 IME 动画联动，得把它改成 `DISPATCH_MODE_CONTINUE_ON_SUBTREE`。
- 渲染器目前是**平铺列表**，因为 `PreferenceDelegateUi` 模型里没有分组信息
  （androidx 那边也是平铺的）。想要分组的话，可以给模型加一个可选的 group 标签，
  或者在页面里用 `header`/`footer` + `PreferenceCategoryHeader` 手动分。
- `values/themes.xml` 里的 `Theme.TrimeAppTheme` 还是 AppCompat 主题，
  没迁的页面和窗口背景仍然靠它。全部迁完后可以换成 M3 的 XML 主题或干脆删掉。
