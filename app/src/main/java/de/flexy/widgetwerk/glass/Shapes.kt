package de.flexy.widgetwerk.glass

import android.graphics.Path
import android.graphics.RectF
import de.flexy.widgetwerk.data.ShapeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Expressive Formen als android.graphics.Path.
 * Polare Formen (Cookie, Clover, Sunny …) werden als Kurve r(θ) abgetastet – bei Widgetgröße
 * sieht das völlig glatt aus und braucht keine Zusatzbibliothek.
 */
object Shapes {

    /** Pfad für eine Form im Rechteck [rect]. Polare Formen werden quadratisch zentriert. */
    fun path(kind: ShapeKind, rect: RectF, cornerPx: Float): Path {
        val p = Path()
        when (kind) {
            ShapeKind.ROUNDED -> {
                val r = min(cornerPx, min(rect.width(), rect.height()) / 2f)
                p.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            ShapeKind.PILL -> {
                val r = min(rect.width(), rect.height()) / 2f
                p.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            ShapeKind.ARCH -> {
                // Oben voll gerundet (Bogen), unten moderater Radius
                val top = min(rect.width(), rect.height()) / 2f
                val bottom = min(cornerPx, top)
                p.addRoundRect(rect, floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom), Path.Direction.CW)
            }
            else -> polar(kind, square(rect), p)
        }
        return p
    }

    fun square(rect: RectF): RectF {
        val s = min(rect.width(), rect.height())
        val cx = rect.centerX(); val cy = rect.centerY()
        return RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
    }

    /** Normierte Radiusfunktion (Maximalwert ≈ 1). */
    private fun radius(kind: ShapeKind, t: Double): Double = when (kind) {
        ShapeKind.CIRCLE -> 1.0
        ShapeKind.COOKIE4 -> wave(t, 4, 0.10)
        ShapeKind.COOKIE6 -> wave(t, 6, 0.075)
        ShapeKind.COOKIE9 -> wave(t, 9, 0.055)
        ShapeKind.COOKIE12 -> wave(t, 12, 0.04)
        ShapeKind.SUNNY -> wave(t, 8, 0.07, sharp = 0.6)
        ShapeKind.BURST -> wave(t, 12, 0.10, sharp = 0.8)
        ShapeKind.CLOVER -> {
            // 4 runde Blätter mit tiefen Einkerbungen
            val c = abs(cos(2 * t))
            0.62 + 0.38 * c.pow(0.55)
        }
        ShapeKind.FLOWER -> {
            val c = abs(cos(4 * t))
            0.72 + 0.28 * c.pow(0.7)
        }
        else -> 1.0
    }

    /** Sinuswelle um den Rand; [sharp] < 1 macht die Spitzen runder/breiter. */
    private fun wave(t: Double, n: Int, amp: Double, sharp: Double = 1.0): Double {
        val c = cos(n * t)
        val shaped = sign(c) * abs(c).pow(sharp)
        return 1.0 - amp + amp * shaped
    }

    private fun polar(kind: ShapeKind, r: RectF, p: Path) {
        val cx = r.centerX().toDouble(); val cy = r.centerY().toDouble()
        val half = r.width() / 2.0
        when (kind) {
            ShapeKind.SQUIRCLE -> {
                // Superellipse |x|^n + |y|^n = 1
                val n = 4.0
                val steps = 240
                for (i in 0..steps) {
                    val t = 2 * PI * i / steps
                    val ct = cos(t); val st = sin(t)
                    val x = half * sign(ct) * abs(ct).pow(2 / n)
                    val y = half * sign(st) * abs(st).pow(2 / n)
                    if (i == 0) p.moveTo((cx + x).toFloat(), (cy + y).toFloat()) else p.lineTo((cx + x).toFloat(), (cy + y).toFloat())
                }
                p.close(); return
            }
            ShapeKind.GEM -> {
                // Abgerundetes Sechseck (hochkant)
                roundedPolygon(p, cx, cy, half, 6, PI / 2, 0.28); return
            }
            else -> Unit
        }
        val steps = 360
        // Startwinkel so, dass Formen "aufrecht" stehen
        val start = -PI / 2
        for (i in 0..steps) {
            val t = start + 2 * PI * i / steps
            val rr = half * radius(kind, t - start)
            val x = cx + rr * cos(t); val y = cy + rr * sin(t)
            if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
    }

    /** Regelmäßiges Polygon mit runden Ecken (Rundung als Anteil der Kantenlänge). */
    private fun roundedPolygon(p: Path, cx: Double, cy: Double, rad: Double, n: Int, rot: Double, round: Double) {
        val pts = (0 until n).map { val a = rot + 2 * PI * it / n; Pair(cx + rad * cos(a), cy + rad * sin(a)) }
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]; val cur = pts[i]; val next = pts[(i + 1) % n]
            val ax = cur.first + (prev.first - cur.first) * round; val ay = cur.second + (prev.second - cur.second) * round
            val bx = cur.first + (next.first - cur.first) * round; val by = cur.second + (next.second - cur.second) * round
            if (i == 0) p.moveTo(ax.toFloat(), ay.toFloat()) else p.lineTo(ax.toFloat(), ay.toFloat())
            p.quadTo(cur.first.toFloat(), cur.second.toFloat(), bx.toFloat(), by.toFloat())
        }
        p.close()
    }

    /** Für Deko: zufällige, aber hübsche Form wählen. */
    val decorative = listOf(
        ShapeKind.COOKIE4, ShapeKind.COOKIE6, ShapeKind.COOKIE9, ShapeKind.COOKIE12, ShapeKind.CLOVER,
        ShapeKind.SUNNY, ShapeKind.BURST, ShapeKind.FLOWER, ShapeKind.GEM, ShapeKind.SQUIRCLE, ShapeKind.CIRCLE,
    )
}
