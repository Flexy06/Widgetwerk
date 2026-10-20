package de.flexy.widgetwerk.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import de.flexy.widgetwerk.data.ColorRole
import de.flexy.widgetwerk.data.Surface
import de.flexy.widgetwerk.data.Tint
import de.flexy.widgetwerk.data.WidgetConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Ergebnis: Hintergrund-Bitmap + dazu passende Textfarben. */
class Rendered(
    val bitmap: Bitmap,
    val light: Boolean,       // heller Hintergrund → dunkle Schrift
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val scale: Float,         // Bitmap-Pixel pro Widget-Pixel
)

/** Maße eines Widgets in echten Bildschirmpixeln. */
data class WidgetGeometry(val widthPx: Int, val heightPx: Int, val density: Float, val screenBounds: Rect?)

object SurfaceRenderer {

    /** Obergrenze für Hintergrund-Bitmaps (RemoteViews haben ein Speicherlimit). */
    const val MAX_PIXELS = 750_000

    fun scaleFor(w: Int, h: Int, maxPixels: Int = MAX_PIXELS): Float =
        min(1f, sqrt(maxPixels.toFloat() / max(1, w * h)))

    fun render(
        ctx: Context,
        cfg: WidgetConfig,
        pal: Palette,
        geo: WidgetGeometry,
        art: Bitmap? = null,
        seed: Int = 0,
        square: Boolean = false,   // Form quadratisch zentrieren (Zifferblatt, Datum)
    ): Rendered {
        val s = scaleFor(geo.widthPx, geo.heightPx)
        val rw = max(2, (geo.widthPx * s).roundToInt()); val rh = max(2, (geo.heightPx * s).roundToInt())
        val bmp = base(ctx, cfg, pal, geo, rw, rh, s, art, seed)
        val full = RectF(0f, 0f, rw.toFloat(), rh.toFloat())
        val shapePath = Shapes.path(cfg.shape, if (square) Shapes.square(full) else full, cfg.cornerDp * geo.density * s)
        // Helligkeit VOR der Maske messen (sonst zählt die Transparenz außerhalb der Form mit)
        val lum = luminance(ctx, cfg, bmp, geo)
        finish(bmp, shapePath, cfg.rim && cfg.surface != Surface.NONE, geo.density * s)
        return colorsFor(cfg, pal, lum, bmp, s)
    }

    /** Textfarben passend zur gemessenen Helligkeit. */
    fun colorsFor(cfg: WidgetConfig, pal: Palette, lum: Float, bmp: Bitmap, s: Float): Rendered {
        val tones = tonesFor(pal, cfg.colorRole)
        if (cfg.surface == Surface.ACCENT) {
            val (_, _, onC) = pal.role(cfg.colorRole)
            val accent = if (pal.dark) tones[3] else tones[9]
            return Rendered(bmp, !pal.dark, onC, ColorUtils.setAlphaComponent(onC, 0xC0), accent, s)
        }
        val light = lum > 0.5f
        return if (light) Rendered(bmp, true, pal.n1[11], ColorUtils.setAlphaComponent(pal.n2[10], 0xD0), tones[8], s)
        else Rendered(bmp, false, pal.n1[1], ColorUtils.setAlphaComponent(pal.n2[2], 0xD0), tones[4], s)
    }

    fun tonesFor(pal: Palette, role: ColorRole): IntArray = when (role) {
        ColorRole.PRIMARY -> pal.a1
        ColorRole.SECONDARY -> pal.a2
        ColorRole.TERTIARY -> pal.a3
        ColorRole.NEUTRAL -> pal.n2
    }

    /** Ungemaskierter Inhalt: Material + Tönung + Korn. */
    fun base(
        ctx: Context, cfg: WidgetConfig, pal: Palette, geo: WidgetGeometry,
        rw: Int, rh: Int, s: Float, art: Bitmap?, seed: Int,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dst = RectF(0f, 0f, rw.toFloat(), rh.toFloat())
        var tintable = true
        when (cfg.surface) {
            Surface.GLASS -> {
                val wall = WallpaperSource.get(ctx)
                if (wall != null) drawGlass(ctx, c, wall, geo, cfg.blur, dst)
                else drawFrost(c, pal, dst, seed, cfg.blur)
            }
            Surface.FROST -> drawFrost(c, pal, dst, seed, cfg.blur)
            Surface.ART -> if (art != null) drawSmooth(c, Blur.blurWhole(art, cfg.blur), dst, cover = true)
            else drawFrost(c, pal, dst, seed, cfg.blur)
            Surface.TONAL -> { c.drawColor(if (pal.dark) pal.n1[10] else pal.n1[1]); tintable = false }
            Surface.ACCENT -> { c.drawColor(pal.role(cfg.colorRole).second); tintable = false }
            Surface.NONE -> return bmp
        }
        if (tintable) {
            val tint = when (cfg.tint) {
                Tint.AUTO -> if (pal.dark) Color.BLACK else Color.WHITE
                Tint.LIGHT -> Color.WHITE
                Tint.DARK -> Color.BLACK
                Tint.COLOR -> pal.role(cfg.colorRole).second
            }
            // Milchglas: Grundtönung + einstellbarer Anteil
            val a = (0.06f + cfg.tintStrength * 0.8f).coerceIn(0f, 0.92f)
            c.drawColor(ColorUtils.setAlphaComponent(tint, (a * 255).roundToInt()))
            // Sanfter Glanz von oben
            c.drawRect(dst, Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, rh.toFloat(), Color.argb(26, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            })
        }
        if (cfg.grain) drawGrain(c, dst, if (cfg.surface == Surface.TONAL || cfg.surface == Surface.ACCENT) 10 else 18)
        return bmp
    }

    /** Wallpaper-Ausschnitt an der Widget-Position blurren. */
    private fun drawGlass(ctx: Context, c: Canvas, wall: Bitmap, geo: WidgetGeometry, blur: Float, dst: RectF) {
        val k = WallpaperSource.cacheScale(ctx, wall)
        val b = geo.screenBounds
        val region = if (b != null) RectF(b.left * k, b.top * k, b.right * k, b.bottom * k)
        else {
            // Noch nicht kalibriert: Bildmitte als Schätzung
            val w = geo.widthPx * k; val h = geo.heightPx * k
            RectF(wall.width / 2f - w / 2, wall.height * 0.55f - h / 2, wall.width / 2f + w / 2, wall.height * 0.55f + h / 2)
        }
        drawSmooth(c, Blur.blurRegion(wall, region, blur), dst, cover = false)
    }

    /**
     * Kleines Bild weich hochskalieren: erst ×4 mit Filter + nachblurren, dann aufs Ziel.
     * Verhindert die typischen "Rauten"-Artefakte beim direkten Hochskalieren.
     */
    fun drawSmooth(c: Canvas, small: Bitmap, dst: RectF, cover: Boolean) {
        val mw = (small.width * 4).coerceAtMost(400); val mh = (small.height * 4).coerceAtMost(400)
        val mid = Bitmap.createScaledBitmap(small, max(2, mw), max(2, mh), true)
        Blur.boxBlur(mid, 3, 2)
        val src = if (cover) coverRect(mid.width, mid.height, dst.width(), dst.height()) else null
        c.drawBitmap(mid, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
    }

    fun coverRect(sw: Int, sh: Int, dw: Float, dh: Float): Rect {
        val scale = max(dw / sw, dh / sh)
        val cw = (dw / scale).roundToInt(); val ch = (dh / scale).roundToInt()
        val l = (sw - cw) / 2; val t = (sh - ch) / 2
        return Rect(l, t, l + cw, t + ch)
    }

    /** Milchglas ohne Wallpaper: weiche Farbflecken aus der dynamischen Palette. */
    fun drawFrost(c: Canvas, pal: Palette, dst: RectF, seed: Int, blur: Float) {
        val w = 64; val h = max(16, (64 * dst.height() / dst.width()).roundToInt().coerceAtMost(160))
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val sc = Canvas(small)
        sc.drawColor(if (pal.dark) pal.n1[10] else pal.n1[2])
        val rnd = Random(seed * 31 + 7)
        val colors = if (pal.dark) listOf(pal.a1[8], pal.a3[8], pal.a2[7], pal.a1[6])
        else listOf(pal.a1[4], pal.a3[3], pal.a2[3], pal.a1[3])
        colors.forEach { col ->
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
            val r = (0.45f + rnd.nextFloat() * 0.5f) * max(w, h)
            sc.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, r, col, ColorUtils.setAlphaComponent(col, 0), Shader.TileMode.CLAMP)
            })
        }
        Blur.boxBlur(small, (2 + blur * 4).roundToInt(), 3)
        drawSmooth(c, small, dst, cover = false)
    }

    private val noise: Bitmap by lazy {
        val n = 96
        val rnd = Random(42)
        val px = IntArray(n * n) {
            val v = rnd.nextInt(256)
            Color.argb(rnd.nextInt(256), v, v, v)
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    fun drawGrain(c: Canvas, dst: RectF, alpha: Int) {
        c.drawRect(dst, Paint().apply {
            shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            this.alpha = alpha
        })
    }

    /** Lichtkante zeichnen und auf die Form maskieren. */
    fun finish(bmp: Bitmap, path: Path, rim: Boolean, dp: Float) {
        val c = Canvas(bmp)
        if (rim) {
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.6f * dp   // die äußere Hälfte fällt durch die Maske weg
                shader = LinearGradient(0f, 0f, w * 0.6f, h, Color.argb(150, 255, 255, 255), Color.argb(28, 255, 255, 255), Shader.TileMode.CLAMP)
            })
        }
        mask(bmp, path)
    }

    /** Alles außerhalb von [path] transparent machen (mit Kantenglättung). */
    fun mask(bmp: Bitmap, path: Path) {
        val c = Canvas(bmp)
        val inverse = Path(path).apply { fillType = Path.FillType.INVERSE_WINDING }
        c.drawPath(inverse, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    /** Mittlere Helligkeit des Hintergrunds (bei "None" das Wallpaper dahinter). */
    private fun luminance(ctx: Context, cfg: WidgetConfig, bmp: Bitmap, geo: WidgetGeometry): Float {
        if (cfg.surface == Surface.NONE) {
            val wall = WallpaperSource.get(ctx) ?: return 0f // ohne Wallpaper: helle Schrift
            val b = geo.screenBounds ?: return averageLum(wall)
            val k = WallpaperSource.cacheScale(ctx, wall)
            val r = Rect((b.left * k).toInt(), (b.top * k).toInt(), (b.right * k).toInt(), (b.bottom * k).toInt())
            if (!r.intersect(0, 0, wall.width, wall.height) || r.width() < 1 || r.height() < 1) return averageLum(wall)
            return averageLum(Bitmap.createBitmap(wall, r.left, r.top, r.width(), r.height()))
        }
        return averageLum(bmp)
    }

    fun averageLum(b: Bitmap): Float {
        val t = Bitmap.createScaledBitmap(b, 8, 8, true)
        var sum = 0f; var n = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = t.getPixel(x, y)
            if (Color.alpha(p) < 40) continue
            sum += Color.luminance(p); n++
        }
        return if (n == 0) 0f else sum / n
    }
}
