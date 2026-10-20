package de.flexy.widgetwerk.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import de.flexy.widgetwerk.data.DecoStyle
import de.flexy.widgetwerk.data.ShapeKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Gezeichnete Grafiken: Wetter-Icons, Akku-Ring, Uhrzeiger, Deko, App-Icons. */
object Art {

    private fun aa() = Paint(Paint.ANTI_ALIAS_FLAG)

    // ------------------------------------------------------------------ Wetter

    private const val SUN_A = 0xFFFFD25E.toInt()
    private const val SUN_B = 0xFFFF9A3D.toInt()
    private const val MOON = 0xFFFFE6B0.toInt()
    private const val RAIN = 0xFF6FB1FF.toInt()

    fun weatherIcon(code: Int, isDay: Boolean, sizePx: Int, lightBg: Boolean): Bitmap {
        val s = sizePx.toFloat()
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cloud = if (lightBg) 0xFFFFFFFF.toInt() else 0xFFF2F2F7.toInt()
        val cloudShade = if (lightBg) 0xFFD7DBE6.toInt() else 0xFFC9CCD6.toInt()
        val dark = code == 3 || code >= 45
        when {
            code <= 1 -> if (isDay) sun(c, s * 0.5f, s * 0.5f, s * 0.42f) else moon(c, s * 0.5f, s * 0.5f, s * 0.36f)
            code == 2 -> {
                if (isDay) sun(c, s * 0.62f, s * 0.36f, s * 0.3f) else moon(c, s * 0.62f, s * 0.36f, s * 0.24f)
                cloud(c, s * 0.08f, s * 0.38f, s * 0.82f, s * 0.84f, cloud, cloudShade, lightBg)
            }
            else -> {
                val top = if (code == 3) 0.22f else 0.12f
                cloud(c, s * 0.06f, s * top, s * 0.94f, s * (top + 0.5f), if (dark) cloudShade else cloud, cloudShade, lightBg)
                val y0 = s * (top + 0.56f)
                when (code) {
                    45, 48 -> fog(c, s, y0)
                    in 51..67, in 80..82 -> rain(c, s, y0, heavy = code in listOf(65, 82, 67))
                    in 71..77, 85, 86 -> snow(c, s, y0)
                    in 95..99 -> bolt(c, s * 0.5f, y0 - s * 0.06f, s * 0.3f)
                }
            }
        }
        return bmp
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Shapes.path(ShapeKind.SUNNY, RectF(cx - r, cy - r, cx + r, cy + r), 0f)
        c.drawPath(p, aa().apply { shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, SUN_A, SUN_B, Shader.TileMode.CLAMP) })
        c.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.35f, aa().apply {
            shader = RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r * 0.35f, Color.argb(90, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.4f, r * 0.85f, Path.Direction.CW) }
        p.op(cut, Path.Op.DIFFERENCE)
        c.drawPath(p, aa().apply { color = MOON })
    }

    private fun cloud(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int, shade: Int, lightBg: Boolean) {
        val w = r - l; val h = b - t
        val p = Path()
        p.addRoundRect(RectF(l, t + h * 0.45f, r, b), h * 0.28f, h * 0.28f, Path.Direction.CW)
        p.addCircle(l + w * 0.32f, t + h * 0.52f, h * 0.3f, Path.Direction.CW)
        p.addCircle(l + w * 0.58f, t + h * 0.38f, h * 0.38f, Path.Direction.CW)
        p.addCircle(l + w * 0.8f, t + h * 0.6f, h * 0.24f, Path.Direction.CW)
        val merged = Path().apply { op(p, Path.Op.UNION) }
        if (lightBg) {
            // dezenter Schatten, damit weiße Wolken auf hellem Glas sichtbar bleiben
            c.save(); c.translate(0f, h * 0.05f)
            c.drawPath(merged, aa().apply { color = Color.argb(40, 0, 0, 0) })
            c.restore()
        }
        c.drawPath(merged, aa().apply { shader = LinearGradient(0f, t, 0f, b, col, shade, Shader.TileMode.CLAMP) })
    }

    private fun rain(c: Canvas, s: Float, y0: Float, heavy: Boolean) {
        val p = aa().apply { color = RAIN; strokeWidth = s * 0.065f; strokeCap = Paint.Cap.ROUND }
        val xs = if (heavy) listOf(0.28f, 0.44f, 0.6f, 0.76f) else listOf(0.34f, 0.52f, 0.7f)
        xs.forEachIndexed { i, x ->
            val yy = y0 + (i % 2) * s * 0.06f
            c.drawLine(s * x, yy, s * x - s * 0.06f, yy + s * 0.16f, p)
        }
    }

    private fun snow(c: Canvas, s: Float, y0: Float) {
        val p = aa().apply { color = Color.WHITE }
        listOf(0.32f to 0f, 0.5f to 0.08f, 0.68f to 0f, 0.41f to 0.16f, 0.59f to 0.17f).forEach { (x, dy) ->
            c.drawCircle(s * x, y0 + s * dy + s * 0.03f, s * 0.045f, p)
        }
    }

    private fun fog(c: Canvas, s: Float, y0: Float) {
        val p = aa().apply { color = Color.argb(200, 220, 225, 235); strokeWidth = s * 0.06f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(s * 0.18f, y0 + s * 0.02f, s * 0.7f, y0 + s * 0.02f, p)
        c.drawLine(s * 0.32f, y0 + s * 0.14f, s * 0.84f, y0 + s * 0.14f, p)
    }

    private fun bolt(c: Canvas, cx: Float, top: Float, h: Float) {
        val w = h * 0.55f
        val p = Path().apply {
            moveTo(cx + w * 0.15f, top)
            lineTo(cx - w * 0.45f, top + h * 0.58f)
            lineTo(cx - w * 0.02f, top + h * 0.58f)
            lineTo(cx - w * 0.2f, top + h)
            lineTo(cx + w * 0.5f, top + h * 0.36f)
            lineTo(cx + w * 0.06f, top + h * 0.36f)
            close()
        }
        c.drawPath(p, aa().apply { shader = LinearGradient(0f, top, 0f, top + h, SUN_A, SUN_B, Shader.TileMode.CLAMP); pathEffect = android.graphics.CornerPathEffect(h * 0.06f) })
    }

    // ------------------------------------------------------------------ Akku

    /** Ring mit welliger Fortschrittslinie (M3 Expressive "wavy progress"). */
    fun batteryRing(sizePx: Int, level: Float, charging: Boolean, track: Int, progress: Int, dp: Float): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = max(4f, 6.5f * dp)
        val amp = 2.6f * dp
        val r = sizePx / 2f - stroke - amp
        val cx = sizePx / 2f
        c.drawCircle(cx, cx, r, aa().apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = track })
        val sweep = (360f * level.coerceIn(0f, 1f))
        if (sweep > 0.5f) {
            val waves = 14
            val p = Path()
            val steps = (sweep * 2).toInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val deg = -90f + sweep * i / steps
                val t = Math.toRadians(deg.toDouble())
                val rr = r + amp * sin(waves * (t + PI / 2)).toFloat()
                val x = cx + rr * cos(t).toFloat(); val y = cx + rr * sin(t).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            c.drawPath(p, aa().apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = progress })
        }
        if (charging) {
            // kleiner Blitz oben auf dem Ring
            val h = stroke * 2.6f
            val bx = cx; val by = cx - r - h / 2
            c.drawCircle(bx, cx - r, h * 0.62f, aa().apply { color = progress })
            val p = Path().apply {
                val w = h * 0.5f; val top = by + h * 0.12f; val hh = h * 0.76f
                moveTo(bx + w * 0.1f, top); lineTo(bx - w * 0.4f, top + hh * 0.56f); lineTo(bx - w * 0.02f, top + hh * 0.56f)
                lineTo(bx - w * 0.12f, top + hh); lineTo(bx + w * 0.42f, top + hh * 0.38f); lineTo(bx + w * 0.04f, top + hh * 0.38f); close()
            }
            c.drawPath(p, aa().apply { color = if (ColorUtils.calculateLuminance(progress) > 0.5) Color.BLACK else Color.WHITE })
        }
        return bmp
    }

    // ------------------------------------------------------------------ Analoguhr

    enum class Hand { HOUR, MINUTE, SECOND }

    /** Zeiger-Bitmap in voller Zifferblattgröße, zeigt auf 12 Uhr. */
    fun hand(sizePx: Int, which: Hand, color: Int, dp: Float): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sizePx / 2f; val r = sizePx / 2f
        val p = aa().apply { this.color = color; strokeCap = Paint.Cap.ROUND }
        when (which) {
            Hand.HOUR -> { p.strokeWidth = r * 0.13f; c.drawLine(cx, cx, cx, cx - r * 0.42f, p) }
            Hand.MINUTE -> { p.strokeWidth = r * 0.075f; c.drawLine(cx, cx, cx, cx - r * 0.66f, p) }
            Hand.SECOND -> {
                p.strokeWidth = max(2f, r * 0.02f); c.drawLine(cx, cx + r * 0.1f, cx, cx - r * 0.7f, p)
                c.drawCircle(cx, cx - r * 0.7f, r * 0.045f, p)
            }
        }
        if (which != Hand.SECOND) c.drawCircle(cx, cx, r * 0.06f, p)
        return bmp
    }

    /** Markierungen fürs Zifferblatt direkt auf den Hintergrund. */
    fun drawDialMarks(c: Canvas, rect: RectF, color: Int, accent: Int) {
        val cx = rect.centerX(); val cy = rect.centerY(); val r = min(rect.width(), rect.height()) / 2f
        val p = aa()
        for (i in 0 until 12) {
            val a = 2 * PI * i / 12 - PI / 2
            val big = i % 3 == 0
            p.color = if (i == 0) accent else color
            val rr = r * 0.8f
            c.drawCircle(cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat(), if (big) r * 0.045f else r * 0.022f, p)
        }
    }

    // ------------------------------------------------------------------ Deko

    /**
     * Rein dekorative Motive. [tones] ist eine Tonpalette (0..12), [panel] optional
     * der vorgerenderte Hintergrund (Glas etc.), auf den gezeichnet wird.
     */
    fun deco(style: DecoStyle, w: Int, h: Int, shape: ShapeKind, tones: IntArray, tones2: IntArray, dark: Boolean, seed: Int, dp: Float, panel: Bitmap?): Bitmap {
        val bmp = panel ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val lo = if (dark) 7 else 3      // Container-Ton
        val hi = if (dark) 4 else 6      // kräftiger Ton
        when (style) {
            DecoStyle.SHAPE -> {
                val path = Shapes.path(shape, full, 40 * dp)
                c.drawPath(path, aa().apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), tones[lo], tones[hi], Shader.TileMode.CLAMP)
                })
                // innere Form als Echo
                val sq = Shapes.square(full)
                val inner = RectF(sq).apply { inset(sq.width() * 0.3f, sq.height() * 0.3f) }
                inner.offset(sq.width() * (rnd.nextFloat() - 0.5f) * 0.2f, sq.height() * (rnd.nextFloat() - 0.5f) * 0.2f)
                c.drawPath(Shapes.path(Shapes.decorative[rnd.nextInt(Shapes.decorative.size)], inner, 0f), aa().apply {
                    color = ColorUtils.setAlphaComponent(tones2[if (dark) 3 else 8], 150)
                })
            }
            DecoStyle.ORBS -> {
                repeat(5) {
                    val r = (0.25f + rnd.nextFloat() * 0.35f) * max(w, h)
                    val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
                    val col = (if (it % 2 == 0) tones else tones2)[3 + rnd.nextInt(5)]
                    c.drawCircle(x, y, r, aa().apply { shader = RadialGradient(x, y, r, col, ColorUtils.setAlphaComponent(col, 0), Shader.TileMode.CLAMP) })
                }
            }
            DecoStyle.STACK -> {
                val sq = Shapes.square(full)
                val n = 3
                repeat(n) { i ->
                    val size = sq.width() * (0.62f - i * 0.1f)
                    val x = sq.left + rnd.nextFloat() * (sq.width() - size)
                    val y = sq.top + rnd.nextFloat() * (sq.height() - size)
                    val kind = Shapes.decorative[rnd.nextInt(Shapes.decorative.size)]
                    val col = listOf(tones[hi], tones2[lo + if (dark) -1 else 1], tones[lo])[i]
                    c.drawPath(Shapes.path(kind, RectF(x, y, x + size, y + size), 0f), aa().apply { color = col })
                }
            }
            DecoStyle.WAVES -> {
                val bands = 5
                val amp = h / 18f
                val freq = 1.5f + rnd.nextFloat() * 1.5f
                for (b in bands downTo 1) {
                    val base = h * b / (bands + 1f)
                    val p = Path().apply {
                        moveTo(0f, h.toFloat())
                        var x = 0f
                        while (x <= w) {
                            lineTo(x, base + amp * sin((x / w * freq * 2 * PI) + b).toFloat()); x += 4f
                        }
                        lineTo(w.toFloat(), h.toFloat()); close()
                    }
                    val tone = (if (b % 2 == 0) tones else tones2)[(if (dark) 9 - b else 2 + b).coerceIn(1, 11)]
                    c.drawPath(p, aa().apply { color = ColorUtils.setAlphaComponent(tone, 230) })
                }
            }
            DecoStyle.DOTS -> {
                val step = 18 * dp
                val cols = (w / step).toInt(); val rows = (h / step).toInt()
                val ox = (w - (cols - 1) * step) / 2; val oy = (h - (rows - 1) * step) / 2
                val angle = rnd.nextFloat() * 2 * PI
                for (yy in 0 until rows) for (xx in 0 until cols) {
                    val nx = xx / max(1f, cols - 1f); val ny = yy / max(1f, rows - 1f)
                    val g = ((nx * cos(angle) + ny * sin(angle)).toFloat() + 1.4f) / 2.8f
                    val r = step * 0.42f * g.coerceIn(0.12f, 1f)
                    c.drawCircle(ox + xx * step, oy + yy * step, r, aa().apply { color = if ((xx + yy) % 5 == 0) tones2[hi] else tones[hi] })
                }
            }
            DecoStyle.RINGS -> {
                val cx = w * (0.3f + rnd.nextFloat() * 0.4f); val cy = h * (0.3f + rnd.nextFloat() * 0.4f)
                val maxR = max(w, h).toFloat()
                val sw = 14 * dp
                var r = sw
                var i = 0
                while (r < maxR) {
                    val start = rnd.nextFloat() * 360f
                    val sweep = 120f + rnd.nextFloat() * 200f
                    c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, aa().apply {
                        style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND
                        color = (if (i % 2 == 0) tones else tones2)[if (dark) 4 + (i % 3) else 4 + (i % 4)]
                    })
                    r += sw * 1.7f; i++
                }
            }
        }
        return bmp
    }

    // ------------------------------------------------------------------ Bilder

    /** Bild als "cover" in Form schneiden. */
    fun shaped(src: Bitmap, w: Int, h: Int, path: Path): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(src, SurfaceRenderer.coverRect(src.width, src.height, w.toFloat(), h.toFloat()), RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        SurfaceRenderer.mask(out, path)
        return out
    }

    /** App-Icon; "themed" = monochromes Icon auf Tonfläche wie beim Pixel-Launcher. */
    fun appIcon(d: Drawable, sizePx: Int, themed: Boolean, container: Int, onContainer: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val mono = if (android.os.Build.VERSION.SDK_INT >= 33) (d as? AdaptiveIconDrawable)?.monochrome else null
        if (themed && mono != null) {
            c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, aa().apply { color = container })
            // Adaptive Icons haben 1/6 Rand → etwas größer zeichnen
            val inset = -sizePx * 0.18f
            mono.setBounds(inset.toInt(), inset.toInt(), (sizePx - inset).toInt(), (sizePx - inset).toInt())
            mono.setTint(onContainer)
            mono.draw(c)
        } else if (d is AdaptiveIconDrawable) {
            val path = Path().apply { addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW) }
            c.save(); c.clipPath(path)
            val inset = -sizePx * 0.18f
            d.background?.let { it.setBounds(inset.toInt(), inset.toInt(), (sizePx - inset).toInt(), (sizePx - inset).toInt()); it.draw(c) }
            d.foreground?.let { it.setBounds(inset.toInt(), inset.toInt(), (sizePx - inset).toInt(), (sizePx - inset).toInt()); it.draw(c) }
            c.restore()
            SurfaceRenderer.mask(out, path) // weiche Kante
        } else {
            c.drawBitmap(d.toBitmap(sizePx, sizePx), 0f, 0f, null)
        }
        return out
    }

    /** Platzhalter-Cover (Notenzeichen in Cookie-Form). */
    fun placeholderArt(sizePx: Int, container: Int, onContainer: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val s = sizePx.toFloat()
        c.drawPath(Shapes.path(ShapeKind.COOKIE9, RectF(0f, 0f, s, s), 0f), aa().apply { color = container })
        val p = aa().apply { color = onContainer; strokeWidth = s * 0.07f; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(s * 0.42f, s * 0.64f, s * 0.11f, p)
        c.drawLine(s * 0.52f, s * 0.64f, s * 0.52f, s * 0.3f, p)
        c.drawLine(s * 0.52f, s * 0.3f, s * 0.66f, s * 0.38f, p)
        return out
    }
}
