package de.flexy.widgetwerk.glass

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import de.flexy.widgetwerk.data.ColorRole

/**
 * Dynamische Material-You-Farben (aus dem Wallpaper) für das Rendern von Bitmaps.
 * Die System-Tonpaletten gibt es ab Android 12 als Farbressourcen.
 */
data class Palette(
    val dark: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surface: Int,
    val surfaceHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    // Rohe Töne für Deko/Frost
    val a1: IntArray, val a2: IntArray, val a3: IntArray, val n1: IntArray, val n2: IntArray,
) {
    /** Akzent nach gewählter Rolle. */
    fun role(r: ColorRole): Triple<Int, Int, Int> = when (r) {
        ColorRole.PRIMARY -> Triple(primary, primaryContainer, onPrimaryContainer)
        ColorRole.SECONDARY -> Triple(secondary, secondaryContainer, onSecondaryContainer)
        ColorRole.TERTIARY -> Triple(tertiary, tertiaryContainer, onTertiaryContainer)
        ColorRole.NEUTRAL -> Triple(onSurfaceVariant, surfaceHigh, onSurface)
    }

    val hash: Int get() = listOf(primary, secondary, tertiary, surface, if (dark) 1 else 0).hashCode()

    companion object {
        // Töne: 0,10,50,100,200,300,400,500,600,700,800,900,1000 → Index 0..12
        private val A1 = intArrayOf(
            android.R.color.system_accent1_0, android.R.color.system_accent1_10, android.R.color.system_accent1_50,
            android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300,
            android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600,
            android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900,
            android.R.color.system_accent1_1000,
        )
        private val A2 = intArrayOf(
            android.R.color.system_accent2_0, android.R.color.system_accent2_10, android.R.color.system_accent2_50,
            android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300,
            android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600,
            android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900,
            android.R.color.system_accent2_1000,
        )
        private val A3 = intArrayOf(
            android.R.color.system_accent3_0, android.R.color.system_accent3_10, android.R.color.system_accent3_50,
            android.R.color.system_accent3_100, android.R.color.system_accent3_200, android.R.color.system_accent3_300,
            android.R.color.system_accent3_400, android.R.color.system_accent3_500, android.R.color.system_accent3_600,
            android.R.color.system_accent3_700, android.R.color.system_accent3_800, android.R.color.system_accent3_900,
            android.R.color.system_accent3_1000,
        )
        private val N1 = intArrayOf(
            android.R.color.system_neutral1_0, android.R.color.system_neutral1_10, android.R.color.system_neutral1_50,
            android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_300,
            android.R.color.system_neutral1_400, android.R.color.system_neutral1_500, android.R.color.system_neutral1_600,
            android.R.color.system_neutral1_700, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900,
            android.R.color.system_neutral1_1000,
        )
        private val N2 = intArrayOf(
            android.R.color.system_neutral2_0, android.R.color.system_neutral2_10, android.R.color.system_neutral2_50,
            android.R.color.system_neutral2_100, android.R.color.system_neutral2_200, android.R.color.system_neutral2_300,
            android.R.color.system_neutral2_400, android.R.color.system_neutral2_500, android.R.color.system_neutral2_600,
            android.R.color.system_neutral2_700, android.R.color.system_neutral2_800, android.R.color.system_neutral2_900,
            android.R.color.system_neutral2_1000,
        )

        fun isSystemDark(ctx: Context): Boolean =
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fun of(ctx: Context, dark: Boolean = isSystemDark(ctx)): Palette {
            fun tones(ids: IntArray) = IntArray(ids.size) { ctx.getColor(ids[it]) }
            val a1 = tones(A1); val a2 = tones(A2); val a3 = tones(A3); val n1 = tones(N1); val n2 = tones(N2)
            return if (dark) Palette(
                dark = true,
                primary = a1[4], onPrimary = a1[10], primaryContainer = a1[9], onPrimaryContainer = a1[3],
                secondary = a2[4], secondaryContainer = a2[9], onSecondaryContainer = a2[3],
                tertiary = a3[4], tertiaryContainer = a3[9], onTertiaryContainer = a3[3],
                surface = n1[11], surfaceHigh = n1[10], onSurface = n1[2], onSurfaceVariant = n2[4],
                a1 = a1, a2 = a2, a3 = a3, n1 = n1, n2 = n2,
            ) else Palette(
                dark = false,
                primary = a1[8], onPrimary = a1[0], primaryContainer = a1[3], onPrimaryContainer = a1[11],
                secondary = a2[8], secondaryContainer = a2[3], onSecondaryContainer = a2[11],
                tertiary = a3[8], tertiaryContainer = a3[3], onTertiaryContainer = a3[11],
                surface = n1[1], surfaceHigh = n1[2], onSurface = n1[11], onSurfaceVariant = n2[9],
                a1 = a1, a2 = a2, a3 = a3, n1 = n1, n2 = n2,
            )
        }

        fun luminance(c: Int): Float = Color.luminance(c)
    }
}
