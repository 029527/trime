/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.graphics.Typeface
import android.util.SparseArray
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.osfans.trime.util.appContext
import timber.log.Timber

/**
 * The one typeface of the whole app, keyboard and settings alike: Source Han Sans, shipped as
 * the Noto Sans SC variable font (same glyphs, OFL 1.1) in `assets/fonts/`.
 *
 * The font has a single `wght` axis from [MIN_WEIGHT] to [MAX_WEIGHT]. Every weight is an
 * instance of that axis, never a synthetic bold, so 450 or 550 are as real as 400 and 700.
 *
 * - [typeface] is for platform drawing (`Paint`, `TextView`): one [Typeface] per weight, cached.
 * - [family] is for Compose: one `Font` per step of [WEIGHTS], loaded when first used.
 *
 * The asset is stored uncompressed (see `noCompress` in the build script), so every weight maps
 * the same bytes of the APK instead of inflating 17 MB into memory per typeface.
 *
 * Glyphs the font lacks (emoji, CJK Extension B and beyond) fall back to the system fonts:
 * the builder is given the platform `sans-serif` chain, which ends in the colour emoji font.
 */
object SourceHanSans {
    const val ASSET_PATH = "fonts/NotoSansSC-VF.ttf"
    const val MIN_WEIGHT = 350
    const val MAX_WEIGHT = 700

    /** The steps Compose text can ask for; a weight in between resolves to the nearest one. */
    val WEIGHTS = intArrayOf(350, 400, 450, 500, 550, 600, 650, 700)

    private val typefaces = SparseArray<Typeface>()

    /** The typeface at [weight], clamped into the font's axis. Thread-safe. */
    fun typeface(weight: Int): Typeface {
        val w = weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        synchronized(typefaces) {
            typefaces[w]?.let { return it }
            val typeface =
                runCatching {
                    Typeface
                        .Builder(appContext.assets, ASSET_PATH)
                        .setFontVariationSettings("'wght' $w")
                        .setWeight(w)
                        .setFallback(SYSTEM_FALLBACK)
                        .build()
                }.getOrElse {
                    Timber.w(it, "Failed to load %s at weight %d", ASSET_PATH, w)
                    Typeface.create(SYSTEM_FALLBACK, Typeface.NORMAL)
                }
            typefaces.put(w, typeface)
            return typeface
        }
    }

    fun typeface(weight: FontWeight): Typeface = typeface(weight.weight)

    @OptIn(ExperimentalTextApi::class)
    val family: FontFamily by lazy {
        FontFamily(
            WEIGHTS.map { w ->
                Font(
                    path = ASSET_PATH,
                    assetManager = appContext.assets,
                    weight = FontWeight(w),
                    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
                )
            },
        )
    }

    private const val SYSTEM_FALLBACK = "sans-serif"
}
