package de.flexy.widgetwerk.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Software-Blur: dreifacher Box-Blur (≈ Gauß) auf einem stark verkleinerten Bild.
 * Läuft komplett im Hintergrund ohne RenderScript/GPU und ist für Widgetgrößen schnell genug.
 */
object Blur {

    /**
     * Schneidet [src] auf [region] zu, verkleinert, blurt und liefert ein kleines Bild.
     * [strength] 0..1. Das Ergebnis wird später gefiltert hochskaliert.
     */
    fun blurRegion(src: Bitmap, region: RectF, strength: Float, saturation: Float = 1.25f): Bitmap {
        // Rand mitnehmen, damit die Kanten nicht abdunkeln
        val pad = max(region.width(), region.height()) * 0.12f
        val r = Rect(
            (region.left - pad).roundToInt().coerceIn(0, src.width - 1),
            (region.top - pad).roundToInt().coerceIn(0, src.height - 1),
            (region.right + pad).roundToInt().coerceIn(1, src.width),
            (region.bottom + pad).roundToInt().coerceIn(1, src.height),
        )
        if (r.width() < 2 || r.height() < 2) return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        // Zielauflösung: je stärker der Blur, desto kleiner
        val target = (96 - 64 * strength).coerceAtLeast(20f)
        val scale = target / max(r.width(), r.height())
        val w = max(2, (r.width() * scale).roundToInt())
        val h = max(2, (r.height() * scale).roundToInt())
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(src, r, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
        })
        val radius = (1 + strength * 4).roundToInt()
        boxBlur(small, radius, 3)

        // Den Teil ohne Rand zurückgeben
        val sx = (region.left - r.left) * scale; val sy = (region.top - r.top) * scale
        val cw = region.width() * scale; val ch = region.height() * scale
        val cx = sx.roundToInt().coerceIn(0, w - 1); val cy = sy.roundToInt().coerceIn(0, h - 1)
        val out = Bitmap.createBitmap(
            small, cx, cy,
            cw.roundToInt().coerceIn(1, w - cx), ch.roundToInt().coerceIn(1, h - cy),
        )
        return out
    }

    /** Ganzes Bild blurren (für Albumcover/Fotos als Hintergrund). */
    fun blurWhole(src: Bitmap, strength: Float, saturation: Float = 1.3f): Bitmap =
        blurRegion(src, RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), strength, saturation)

    /** Mehrfacher separierbarer Box-Blur in-place. */
    fun boxBlur(bmp: Bitmap, radius: Int, passes: Int) {
        if (radius < 1) return
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(passes) {
            blurPass(px, tmp, w, h, radius)       // horizontal → tmp (transponiert)
            blurPass(tmp, px, h, w, radius)       // vertikal → px (zurück transponiert)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Ein Box-Blur entlang der Zeilen, schreibt transponiert in [out]. */
    private fun blurPass(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            val row = y * w
            for (i in -r..r) {
                val c = inp[row + min(w - 1, max(0, i))]
                sa += c ushr 24; sr += (c shr 16) and 0xff; sg += (c shr 8) and 0xff; sb += c and 0xff
            }
            for (x in 0 until w) {
                out[x * h + y] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = inp[row + min(w - 1, x + r + 1)]
                val rem = inp[row + max(0, x - r)]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xff) - ((rem shr 16) and 0xff)
                sg += ((add shr 8) and 0xff) - ((rem shr 8) and 0xff)
                sb += (add and 0xff) - (rem and 0xff)
            }
        }
    }
}
