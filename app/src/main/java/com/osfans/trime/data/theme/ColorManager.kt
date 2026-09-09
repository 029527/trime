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
import androidx.annotation.ColorInt
import androidx.annotation.Keep
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object ColorManager {
    private lateinit var theme: Theme
    private val prefs = ThemeManager.prefs
    private var normalModeColor by prefs.normalModeColor
    private val followSystemDayNight by prefs.followSystemDayNight
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

    private var lightModeColorScheme: ColorScheme? = null

    private var darkModeColorScheme: ColorScheme? = null

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
     * 按方案自己的底色亮度判断，而不是看系统深浅色：用户可以在浅色模式下手动选深色配色。
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

    private fun invalidateColors() {
        colorCache.clear()
        cachedTintParams = null
        cachedDarkScheme = null
    }

    /** 拖动配色微调滑块：清缓存并让所有用色的地方重建，不需要重新部署主题。 */
    @Keep
    private val onTintChangeListener =
        PreferenceDelegate.OnChangeListener<Any> { _, _ ->
            invalidateColors()
            if (this::theme.isInitialized) fireChange()
        }

    init {
        listOf(prefs.tintWarm, prefs.tintDim, prefs.tintAlpha).forEach {
            it.registerOnChangeListener(onTintChangeListener)
        }
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

    private fun evaluateActiveColorScheme(): ColorScheme = when {
        followSystemDayNight -> {
            val defaultModeScheme = if (isNightMode) darkModeColorScheme else lightModeColorScheme

            fun resolveScheme(id: String?) = id?.let { colorScheme(it) } ?: defaultModeScheme

            colorScheme(normalModeColor)?.let { userScheme ->
                val lightSchemeId = userScheme.colors["light_scheme"]
                val darkSchemeId = userScheme.colors["dark_scheme"]

                when {
                    lightSchemeId != null && darkSchemeId != null ->
                        // 如果两者都指定了，根据当前模式选择对应的配色
                        resolveScheme(if (isNightMode) darkSchemeId else lightSchemeId)
                    lightSchemeId != null ->
                        // 如果只指定了light_scheme，说明是暗色方案
                        if (isNightMode) userScheme else resolveScheme(lightSchemeId)
                    darkSchemeId != null ->
                        // 如果只指定了dark_scheme，说明是亮色方案
                        if (isNightMode) resolveScheme(darkSchemeId) else userScheme
                    else -> defaultModeScheme
                }
            } ?: defaultModeScheme
        }
        else -> colorScheme(normalModeColor)
    } ?: colorScheme("default") ?: theme.colorSchemes.first()

    /** 每次切换主题后，都要调用此函数，初始化配色 */
    fun switchTheme(theme: Theme) {
        bitmapCache?.evictAll()
        invalidateColors()
        this.theme = theme
        val defaultScheme = colorScheme("default") ?: theme.colorSchemes.first()
        lightModeColorScheme = defaultScheme.colors["light_scheme"]?.let { colorScheme(it) }
        darkModeColorScheme = defaultScheme.colors["dark_scheme"]?.let { colorScheme(it) }
        activeColorScheme = evaluateActiveColorScheme()
    }

    fun setColorScheme(scheme: ColorScheme) {
        activeColorScheme = scheme
        normalModeColor = scheme.id
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
}
