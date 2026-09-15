// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.collection.LruCache
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.theme.ThemePrefs.DayNightMode
import com.osfans.trime.data.theme.builtin.BuiltinColors
import com.osfans.trime.data.theme.builtin.KeyboardColorRoles
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.appContext
import com.osfans.trime.util.isNightMode
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object ColorManager {
    private lateinit var theme: Theme
    private val prefs = ThemeManager.prefs
    private val dayNightMode by prefs.dayNightMode
    private val backgroundFolder get() = theme.generalStyle.backgroundFolder

    private var isNightMode = false

    private lateinit var _activeColorScheme: ColorScheme

    var activeColorScheme: ColorScheme
        get() = _activeColorScheme
        private set(value) {
            if (this::_activeColorScheme.isInitialized && _activeColorScheme == value) return
            _activeColorScheme = value
            invalidateColors()
            fireChange()
        }

    // ------------------------------------------------ 运行时配色微调，见 [ColorTint]

    /** 解析结果（含滤镜）的缓存。key 的解析只取决于配色方案和 fallback，两者变了就整清。 */
    private val colorCache = ConcurrentHashMap<String, Int>()

    private var cachedTintParams: ColorTint.Params? = null

    private var cachedDarkScheme: Boolean? = null

    /** 三个滑块的当前值。SharedPreferences 不用每次颜色都读，改了会通过监听器清掉。 */
    private val tintParams: ColorTint.Params
        get() = cachedTintParams ?: ColorTint
            .of(
                prefs.tintWarm.getValue(),
                prefs.tintDim.getValue(),
                prefs.tintAlpha.getValue(),
            ).also { cachedTintParams = it }

    /**
     * 当前配色是深色配色吗（决定用 BASE_DARK + lift 还是 BASE_LIGHT）。
     * 按方案自己的底色亮度判断，而不是看系统深浅色：深浅色偏好可以把系统浅色模式下的键盘固定成深色。
     */
    private val isDarkScheme: Boolean
        get() = cachedDarkScheme ?: evaluateDarkScheme().also { cachedDarkScheme = it }

    private fun evaluateDarkScheme(): Boolean {
        for (key in arrayOf("back_color", "keyboard_back_color", "key_back_color")) {
            val raw = _activeColorScheme.colors[key]?.takeIf { it.isNotEmpty() } ?: continue
            val color =
                try {
                    ColorUtils.parseColor(raw)
                } catch (_: Exception) {
                    continue // 底色是张图片之类，换下一个 key 判断
                }
            return ColorTint.isDarkColor(color)
        }
        return isNightMode
    }

    /**
     * Bumped every time resolved colours may have changed (scheme switch, theme switch, tint).
     * Long-lived holders of resolved colours compare against it, see [colorCached].
     */
    @Volatile
    var revision = 0
        private set

    private fun invalidateColors() {
        colorCache.clear()
        cachedTintParams = null
        cachedDarkScheme = null
        revision++
    }

    /**
     * 拖动配色微调滑块：清缓存后只通知 [OnTintChangeListener]，不走 [fireChange]。
     * 配色方案、主题都没变，布局也没变，用色的地方原地重新取色即可，不必重建输入视图
     * （重建会打断正在进行的输入）。
     */
    @Keep
    private val onTintChangeListener =
        PreferenceDelegate.OnChangeListener<Any> { _, _ ->
            invalidateColors()
            if (this::theme.isInitialized) fireTintChange()
        }

    /** 设置页切了深浅色或跟随壁纸取色：换成对应的配色方案，走 [fireChange] 重建键盘。 */
    @Keep
    private val onColorSourceChangeListener =
        PreferenceDelegate.OnChangeListener<Any> { _, _ ->
            if (this::theme.isInitialized) activeColorScheme = evaluateActiveColorScheme()
        }

    init {
        listOf(prefs.tintWarm, prefs.tintDim, prefs.tintAlpha).forEach {
            it.registerOnChangeListener(onTintChangeListener)
        }
        prefs.dayNightMode.registerOnChangeListener(onColorSourceChangeListener)
        prefs.followWallpaper.registerOnChangeListener(onColorSourceChangeListener)
    }

    private val BuiltinFallbackColors =
        mapOf(
            "candidate_text_color" to "text_color",
            "comment_text_color" to "candidate_text_color",
            "border_color" to "back_color",
            "candidate_separator_color" to "border_color",
            "hilited_text_color" to "text_color",
            "hilited_back_color" to "back_color",
            "hilited_candidate_text_color" to "hilited_text_color",
            "hilited_candidate_back_color" to "hilited_back_color",
            "hilited_candidate_button_color" to "hilited_candidate_back_color",
            "hilited_label_color" to "hilited_candidate_text_color",
            "hilited_comment_text_color" to "comment_text_color",
            "hilited_key_back_color" to "hilited_candidate_back_color",
            "hilited_key_border_color" to "key_border_color",
            "hilited_key_text_color" to "hilited_candidate_text_color",
            "hilited_key_symbol_color" to "hilited_comment_text_color",
            "hilited_off_key_back_color" to "hilited_key_back_color",
            "hilited_on_key_back_color" to "hilited_key_back_color",
            "hilited_off_key_border_color" to "hilited_key_border_color",
            "hilited_on_key_border_color" to "hilited_key_border_color",
            "hilited_off_key_text_color" to "hilited_key_text_color",
            "hilited_on_key_text_color" to "hilited_key_text_color",
            "hilited_off_key_symbol_color" to "hilited_key_symbol_color",
            "hilited_on_key_symbol_color" to "hilited_key_symbol_color",
            "key_back_color" to "back_color",
            "key_border_color" to "border_color",
            "key_text_color" to "candidate_text_color",
            "key_symbol_color" to "comment_text_color",
            "label_color" to "candidate_text_color",
            "off_key_back_color" to "key_back_color",
            "off_key_border_color" to "key_border_color",
            "off_key_text_color" to "key_text_color",
            "off_key_symbol_color" to "key_symbol_color",
            "on_key_back_color" to "hilited_key_back_color",
            "on_key_border_color" to "hilited_key_border_color",
            "on_key_text_color" to "hilited_key_text_color",
            "on_key_symbol_color" to "hilited_key_symbol_color",
            "popup_back_color" to "key_back_color",
            "popup_text_color" to "key_text_color",
            "hilited_popup_back_color" to "hilited_key_back_color",
            "hilited_popup_text_color" to "hilited_key_text_color",
            "shadow_color" to "border_color",
            "root_background" to "back_color",
            "candidate_background" to "back_color",
            "keyboard_back_color" to "border_color",
            "keyboard_background" to "keyboard_back_color",
            "liquid_keyboard_background" to "keyboard_back_color",
            "text_back_color" to "back_color",
            "long_text_color" to "key_text_color",
            "long_text_back_color" to "key_back_color",
        )

    private var bitmapCache: LruCache<String, Bitmap>? = null

    fun interface OnColorChangeListener {
        fun onColorChange(theme: Theme)
    }

    private val onChangeListeners = WeakHashSet<OnColorChangeListener>()

    fun addOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onColorChange(theme) }
    }

    /**
     * Only the tint (warmth / brightness / opacity) changed: same scheme, same keys, new
     * values. Listeners re-read their colours in place instead of rebuilding views.
     * A scheme or theme switch goes through [OnColorChangeListener] instead, never both.
     */
    fun interface OnTintChangeListener {
        fun onTintChange()
    }

    private val onTintChangeListeners = WeakHashSet<OnTintChangeListener>()

    fun addOnTintChangedListener(listener: OnTintChangeListener) {
        onTintChangeListeners.add(listener)
    }

    fun removeOnTintChangedListener(listener: OnTintChangeListener) {
        onTintChangeListeners.remove(listener)
    }

    private fun fireTintChange() {
        onTintChangeListeners.forEach { it.onTintChange() }
    }

    private fun colorScheme(id: String) = theme.colorSchemes.find { it.id == id }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        activeColorScheme = evaluateActiveColorScheme()

        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = maxMemory / 8
        bitmapCache =
            object : LruCache<String, Bitmap>(cacheSize.toInt()) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount / 1024
            }
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        isNightMode = isNight
        invalidateColors()
        activeColorScheme = evaluateActiveColorScheme()
    }

    /**
     * Picks the wallpaper palette up again when it may have changed: a new wallpaper arrives as a
     * resources change, possibly while the keyboard is hidden, so the input method calls this when
     * its configuration changes, when it builds its input view and when its window is shown.
     * Nothing happens unless the colours really differ, see [activeColorScheme].
     */
    fun refreshWallpaperColors() {
        if (!this::theme.isInitialized || !prefs.isFollowingWallpaper) return
        activeColorScheme = evaluateActiveColorScheme()
    }

    private fun evaluateActiveColorScheme(): ColorScheme {
        // the wallpaper palette has a light and a dark version; which one follows the system, like Material You
        if (prefs.isFollowingWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { wallpaperScheme(isNightMode) }
                .onFailure { Timber.w(it, "No wallpaper colours, using the built-in scheme") }
                .getOrNull()
                ?.let { return it }
        }
        val dark = when (dayNightMode) {
            DayNightMode.FOLLOW_SYSTEM -> isNightMode
            DayNightMode.LIGHT -> false
            DayNightMode.DARK -> true
        }
        val id = if (dark) BuiltinColors.DARK_SCHEME else BuiltinColors.LIGHT_SCHEME
        return colorScheme(id) ?: theme.colorSchemes.first()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun wallpaperScheme(dark: Boolean): ColorScheme {
        val context = appContext
        val material = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return ColorScheme(
            id = if (dark) WALLPAPER_DARK_SCHEME else WALLPAPER_LIGHT_SCHEME,
            colors = KeyboardColorRoles.fromMaterial(material, dark).colors(if (dark) "壁纸深色" else "壁纸浅色"),
        )
    }

    /** 拿到主题后调用一次，初始化配色 */
    fun switchTheme(theme: Theme) {
        bitmapCache?.evictAll()
        invalidateColors()
        this.theme = theme
        activeColorScheme = evaluateActiveColorScheme()
    }

    @ColorInt
    private fun resolveColor(key: String): Int {
        colorCache[key]?.let { return it }
        val color =
            try {
                resolveValue(key) { value ->
                    tinted(key, value)
                }
            } catch (_: IllegalArgumentException) {
                tinted(key, key)
            }
        colorCache[key] = color
        return color
    }

    /**
     * 解析主题里的颜色字面量，并在返回前套一层运行时配色滤镜。
     *
     * 这里是全 App 键盘颜色的唯一出口，所以滤镜挂在这一层。要不要套不透明度既取决于**请求的
     * key**（是不是底色），也取决于**主题里写没写 alpha**，所以必须拿着原始字符串判断 ——
     * 光看解析完的 int 分不出 `0xD1D3D9` 和 `0xFFD1D3D9`。
     */
    @ColorInt
    private fun tinted(
        key: String,
        rawValue: String,
    ): Int {
        val color = ColorUtils.parseColor(rawValue)
        val params = tintParams
        if (params.isNeutral) return color
        return ColorTint.tint(color, params, isDarkScheme, ColorTint.appliesAlpha(key, rawValue))
    }

    private fun resolveDrawable(key: String): Drawable? {
        val drawable =
            try {
                resolveValue(key) { value ->
                    parseDrawable(key, value)
                }
            } catch (_: IllegalArgumentException) {
                parseDrawable(key, key)
            }
        return drawable
    }

    private inline fun <T> resolveValue(
        key: String,
        parser: (String) -> T,
    ): T {
        var currentKey = key

        while (true) {
            val target = activeColorScheme.colors[currentKey]
            if (!target.isNullOrEmpty()) {
                Timber.d("current: $currentKey, origin: $key, target: $target")
                return parser(target)
            }
            val fallback = theme.fallbackColors[currentKey]
            if (!fallback.isNullOrEmpty()) {
                currentKey = fallback
                continue
            }
            val altFallback = BuiltinFallbackColors[currentKey]
            if (!altFallback.isNullOrEmpty()) {
                currentKey = altFallback
            } else {
                throw IllegalArgumentException("$key not found")
            }
        }
    }

    private fun parseDrawable(
        key: String,
        value: String,
    ): Drawable? {
        if (value.isEmpty()) return null
        if (SUPPORTED_IMG_FORMATS.any { value.endsWith(it) }) {
            val path = resolveImageFilePath(value)
            val bitmap =
                bitmapCache?.get(path)
                    ?: BitmapFactory.decodeFile(path)?.also {
                        bitmapCache?.put(path, it)
                    } ?: return null
            if (path.endsWith(".9.png")) {
                val chunk = bitmap.ninePatchChunk
                return if (NinePatch.isNinePatchChunk(chunk)) {
                    // for compiled nine patch image
                    NinePatchDrawable(Resources.getSystem(), bitmap, chunk, Rect(), null)
                } else {
                    // for source nine patch image
                    NinePatchBitmapFactory.createNinePatchDrawable(Resources.getSystem(), bitmap)
                }
            }
            return bitmap.toDrawable(Resources.getSystem())
        } else {
            // 纯色背景（键面、键盘底……）也要过滤镜，否则拖滑块只有文字色会变
            val color =
                try {
                    tinted(key, value)
                } catch (_: Exception) {
                    Color.TRANSPARENT
                }
            return GradientDrawable().apply { setColor(color) }
        }
    }

    private fun resolveImageFilePath(value: String): String {
        val default = DataManager.userDataDir.resolve("backgrounds/$backgroundFolder/$value")
        if (!default.exists()) {
            val fallback = DataManager.userDataDir.resolve("backgrounds/$value")
            if (fallback.exists()) return fallback.absolutePath
        }
        return default.absolutePath
    }

    @ColorInt
    fun getColor(key: String): Int = resolveColor(key)

    fun getDrawable(key: String): Drawable? = resolveDrawable(key)

    fun getDecorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = when (val drawable = getDrawable(colorKey)) {
        is GradientDrawable ->
            drawable.also {
                it.cornerRadius = cornerRadius
                it.alpha = MathUtils.clamp(alpha, 0, 255)
                if (!borderColorKey.isNullOrEmpty()) {
                    try {
                        val borderColor = getColor(borderColorKey)
                        it.setStroke(borderPx, borderColor)
                    } catch (_: Exception) {
                    }
                }
            }
        else -> drawable?.also { it.alpha = MathUtils.clamp(alpha, 0, 255) }
    }

    private val SUPPORTED_IMG_FORMATS = arrayOf(".png", ".webp", ".jpg", ".gif")

    private const val WALLPAPER_LIGHT_SCHEME = "wallpaper_light"
    private const val WALLPAPER_DARK_SCHEME = "wallpaper_dark"
}

/**
 * Like `lazy`, but computed again once [ColorManager.revision] has moved on, so a resolved
 * colour or drawable kept in a field follows scheme and tint changes on its next read.
 */
fun <T> colorCached(compute: () -> T): ReadOnlyProperty<Any?, T> = ColorCached(compute)

private class ColorCached<T>(
    private val compute: () -> T,
) : ReadOnlyProperty<Any?, T> {
    private var revision = -1
    private var value: T? = null

    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T {
        val current = ColorManager.revision
        if (revision != current) {
            value = compute()
            // record the revision read before computing: a change in between is picked up next read
            revision = current
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
