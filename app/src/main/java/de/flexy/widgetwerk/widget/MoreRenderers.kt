package de.flexy.widgetwerk.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import de.flexy.widgetwerk.R
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.ProgressStyle
import de.flexy.widgetwerk.glass.Rendered
import de.flexy.widgetwerk.glass.SurfaceRenderer
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Zusätzliche Widgets: Countdown, Notiz, Weltuhr, Zeit-Fortschritt, Sonne & Mond. */
object MoreRenderers {

    private val sans = Typeface.create("google-sans", Typeface.NORMAL)
    private fun font(weight: Int) = Typeface.create(sans, weight, false)

    // ------------------------------------------------------------------ Countdown

    fun countdown(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id, square = cfg.shape.polar)
        val v = views(r, R.layout.w_countdown)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val target = cfg.countdownEpochDay?.let { LocalDate.ofEpochDay(it) }
        val today = LocalDate.now()
        val days = target?.let { ChronoUnit.DAYS.between(today, it) }
        val sq = min(r.geo.widthPx, r.geo.heightPx) * cfg.textScale
        v.textPx(R.id.cd_number, sq * 0.34f)
        v.textPx(R.id.cd_unit, sq * 0.085f)
        v.textPx(R.id.cd_title, sq * 0.08f)
        v.setTextColor(R.id.cd_number, bg.text)
        v.setTextColor(R.id.cd_unit, bg.accent)
        v.setTextColor(R.id.cd_title, bg.textSecondary)
        when {
            days == null -> {
                v.setTextViewText(R.id.cd_number, "?")
                v.setTextViewText(R.id.cd_unit, "Tap to set a date")
            }
            days == 0L -> {
                v.setTextViewText(R.id.cd_number, "🎉")
                v.setTextViewText(R.id.cd_unit, "TODAY")
            }
            days > 0 -> {
                v.setTextViewText(R.id.cd_number, days.toString())
                v.setTextViewText(R.id.cd_unit, if (days == 1L) "DAY TO GO" else "DAYS TO GO")
            }
            else -> {
                v.setTextViewText(R.id.cd_number, abs(days).toString())
                v.setTextViewText(R.id.cd_unit, if (days == -1L) "DAY SINCE" else "DAYS SINCE")
            }
        }
        v.setTextViewText(R.id.cd_title, cfg.countdownTitle)
        v.tap(r, TapAction.CONFIGURE)
        v
    }

    // ------------------------------------------------------------------ Notiz

    fun note(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val v = views(r, R.layout.w_note)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val text = cfg.noteText.ifBlank { "Tap to write a note" }
        v.setTextViewText(R.id.note_text, text)
        v.setTextColor(R.id.note_text, if (cfg.noteText.isBlank()) bg.textSecondary else bg.text)
        // Schriftgröße grob nach Textlänge und Fläche
        val area = r.wDp * r.hDp
        val base = (kotlin.math.sqrt(area / max(12f, text.length.toFloat())) * 0.55f).coerceIn(13f, 34f)
        v.textPx(R.id.note_text, base * r.density * cfg.textScale)
        v.setInt(R.id.note_text, "setGravity", if (cfg.noteCentered) Gravity.CENTER else Gravity.START or Gravity.TOP)
        v.tap(r, TapAction.CONFIGURE)
        v
    }

    // ------------------------------------------------------------------ Weltuhr

    fun world(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val v = views(r, R.layout.w_world)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val zone = runCatching { ZoneId.of(cfg.zoneId) }.getOrDefault(ZoneId.of("UTC"))
        val label = cfg.zoneLabel?.takeIf { it.isNotBlank() } ?: cityOf(cfg.zoneId)
        v.setTextViewText(R.id.wc_label, label)
        v.setString(R.id.wc_time, "setTimeZone", zone.id)
        v.formats(R.id.wc_time, "h:mm", "HH:mm", cfg.force24h)
        // Zeitverschiebung zu hier
        val now = ZonedDateTime.now()
        val diffMin = (zone.rules.getOffset(now.toInstant()).totalSeconds - now.offset.totalSeconds) / 60
        val diff = when {
            diffMin == 0 -> "Same time as here"
            diffMin % 60 == 0 -> "%+d h".format(diffMin / 60)
            else -> "%+d:%02d h".format(diffMin / 60, abs(diffMin % 60))
        }
        val day = now.withZoneSameInstant(zone).toLocalDate()
        val dayTxt = when (ChronoUnit.DAYS.between(now.toLocalDate(), day)) {
            1L -> "Tomorrow"; -1L -> "Yesterday"; else -> "Today"
        }
        v.setTextViewText(R.id.wc_diff, "$dayTxt · $diff")
        val big = min(r.geo.heightPx * 0.36f, r.geo.widthPx / 4.2f) * cfg.textScale
        v.textPx(R.id.wc_time, big)
        v.textPx(R.id.wc_label, (big * 0.32f).coerceAtLeast(r.dp(12f)))
        v.textPx(R.id.wc_diff, (big * 0.28f).coerceAtLeast(r.dp(11f)))
        v.setTextColor(R.id.wc_label, bg.accent)
        v.setTextColor(R.id.wc_time, bg.text)
        v.setTextColor(R.id.wc_diff, bg.textSecondary)
        v.tap(r, TapAction.ALARMS)
        v
    }

    fun cityOf(zoneId: String) = zoneId.substringAfterLast('/').replace('_', ' ')

    // ------------------------------------------------------------------ Zeit-Fortschritt

    /** Anteil von Tag, Woche, Monat, Jahr (0..1). */
    fun fractions(): List<Pair<String, Float>> {
        val now = ZonedDateTime.now()
        val dayF = (now.toLocalTime().toSecondOfDay()) / 86_400f
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val dow = ((now.dayOfWeek.value - first.value + 7) % 7)
        val weekF = (dow + dayF) / 7f
        val monthF = (now.dayOfMonth - 1 + dayF) / now.toLocalDate().lengthOfMonth()
        val yearF = (now.dayOfYear - 1 + dayF) / now.toLocalDate().lengthOfYear()
        return listOf("Day" to dayF, "Week" to weekF, "Month" to monthF, now.year.toString() to yearF)
    }

    fun progress(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val b = bg.bitmap
        val c = Canvas(b)
        val s = bg.scale
        val dp = r.density * s
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val ringColors = if (bg.light) listOf(tones[8], r.pal.a3[7], r.pal.a2[7], tones[6]) else listOf(tones[3], r.pal.a3[3], r.pal.a2[4], tones[5])
        val track = ColorUtils.setAlphaComponent(bg.text, 36)
        val fr = fractions()
        val w = b.width.toFloat(); val h = b.height.toFloat()
        val pad = 16 * dp
        if (cfg.progressStyle == ProgressStyle.RINGS) {
            val wide = w > h * 1.5f
            val size = min(if (wide) h else w, h) - 2 * pad
            val cx = pad + size / 2; val cy = h / 2
            val ccx = if (wide) cx else w / 2
            val sw = size / 2 / 6.2f
            fr.reversed().forEachIndexed { i, (_, f) -> // außen = Jahr
                val rad = size / 2 - sw / 2 - i * sw * 1.3f
                val oval = RectF(ccx - rad, cy - rad, ccx + rad, cy + rad)
                c.drawArc(oval, 0f, 360f, false, stroke(track, sw))
                c.drawArc(oval, -90f, 360f * f, false, stroke(ringColors[3 - i], sw))
            }
            val year = fr.last()
            text(c, "${(year.second * 100).roundToInt()}%", ccx, cy + size * 0.05f, size * 0.15f, bg.text, 600, Paint.Align.CENTER)
            text(c, year.first, ccx, cy + size * 0.16f, size * 0.07f, bg.textSecondary, 500, Paint.Align.CENTER)
            if (wide) {
                val x = pad * 2 + size
                val lineH = (h - 2 * pad) / 4f
                fr.forEachIndexed { i, (label, f) ->
                    val y = pad + lineH * (i + 0.7f)
                    c.drawCircle(x + 5 * dp, y - lineH * 0.18f, 5 * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColors[i] })
                    text(c, label, x + 16 * dp, y, lineH * 0.42f, bg.text, 500, Paint.Align.LEFT)
                    text(c, "${(f * 100).roundToInt()}%", w - pad, y, lineH * 0.42f, bg.accent, 600, Paint.Align.RIGHT)
                }
            }
        } else {
            val rows = fr.size
            val lineH = (h - 2 * pad) / rows
            fr.forEachIndexed { i, (label, f) ->
                val top = pad + i * lineH
                val ts = min(lineH * 0.34f, 16 * dp)
                text(c, label, pad, top + ts * 1.1f, ts, bg.text, 600, Paint.Align.LEFT)
                text(c, "${(f * 100).roundToInt()}%", w - pad, top + ts * 1.1f, ts, bg.accent, 600, Paint.Align.RIGHT)
                val barY = top + ts * 1.1f + lineH * 0.3f
                val bar = de.flexy.widgetwerk.glass.Art.progressWave((w - 2 * pad).toInt(), (lineH * 0.4f).toInt().coerceAtLeast(4),
                    f, true, track, ringColors[i], dp)
                c.drawBitmap(bar, pad, barY - lineH * 0.2f, null)
            }
        }
        val v = views(r, R.layout.w_canvas)
        v.setImageViewBitmap(R.id.bg, b)
        v.tap(r, TapAction.ALIGN)
        v
    }

    private fun stroke(col: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; color = col
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, col: Int, weight: Int, align: Paint.Align) {
        c.drawText(t, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; color = col; typeface = font(weight); textAlign = align
        })
    }

    // ------------------------------------------------------------------ Sonne & Mond

    fun sun(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val b = bg.bitmap
        val c = Canvas(b)
        val dp = r.density * bg.scale
        val w = b.width.toFloat(); val h = b.height.toFloat()
        val pad = 18 * dp
        val data = if (r.id >= 0) ConfigStore.weather(r.ctx, r.id) else null
        val v = views(r, R.layout.w_canvas)
        if (data == null || data.sunrise == 0L) {
            v.setImageViewBitmap(R.id.bg, b)
            v.setTextViewText(R.id.hint, if (cfg.lat == null) "Tap to set your location" else "Loading…")
            v.setTextColor(R.id.hint, bg.text)
            v.setViewVisibility(R.id.hint, android.view.View.VISIBLE)
            v.tap(r, if (cfg.lat == null) TapAction.CONFIGURE else TapAction.WEATHER_REFRESH)
            return@with v
        }
        val tf = DateFormat.getTimeFormat(r.ctx)
        val now = System.currentTimeMillis() / 1000
        val rise = data.sunrise; val set = data.sunset
        val dayF = ((now - rise).toFloat() / max(1, set - rise)).coerceIn(-0.05f, 1.05f)

        // Bogen
        val moonSize = min(h * 0.32f, w * 0.18f)
        val arcW = w - 2 * pad - moonSize - pad
        val arcH = h * 0.42f
        val baseY = h * 0.62f
        val left = pad; val right = pad + arcW
        val track = ColorUtils.setAlphaComponent(bg.text, 70)
        val arc = Path().apply {
            moveTo(left, baseY)
            cubicTo(left + arcW * 0.25f, baseY - arcH * 1.33f, left + arcW * 0.75f, baseY - arcH * 1.33f, right, baseY)
        }
        c.drawPath(arc, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f * dp; color = track; pathEffect = DashPathEffect(floatArrayOf(5 * dp, 5 * dp), 0f)
        })
        c.drawLine(left - 4 * dp, baseY, right + 4 * dp, baseY, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * dp; color = track })
        // Sonne auf dem Bogen (Bezier-Punkt)
        val t = dayF.coerceIn(0f, 1f)
        val sx = bez(left, left + arcW * 0.25f, left + arcW * 0.75f, right, t)
        val sy = bez(baseY, baseY - arcH * 1.33f, baseY - arcH * 1.33f, baseY, t)
        val isDay = now in rise..set
        val sunR = 11 * dp
        if (isDay) {
            c.drawCircle(sx, sy, sunR * 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(sx, sy, sunR * 2f, Color.argb(120, 255, 200, 80), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
            })
            val sp = de.flexy.widgetwerk.glass.Shapes.path(de.flexy.widgetwerk.data.ShapeKind.SUNNY, RectF(sx - sunR, sy - sunR, sx + sunR, sy + sunR), 0f)
            c.drawPath(sp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC94D.toInt() })
        }
        val ts = min(h * 0.1f, 15 * dp)
        text(c, "↑ " + tf.format(Date(rise * 1000)), left, baseY + ts * 1.6f, ts, bg.text, 600, Paint.Align.LEFT)
        text(c, tf.format(Date(set * 1000)) + " ↓", right, baseY + ts * 1.6f, ts, bg.text, 600, Paint.Align.RIGHT)
        val status = when {
            now < rise -> "Sunrise in " + dur(rise - now)
            now <= set -> dur(set - now) + " of daylight left"
            data.sunriseTomorrow > now -> "Sunrise in " + dur(data.sunriseTomorrow - now)
            else -> "Night"
        }
        text(c, status, left, baseY + ts * 3.1f, ts * 0.9f, bg.textSecondary, 500, Paint.Align.LEFT)
        text(c, cfg.place ?: "", left, pad + ts, ts * 0.95f, bg.accent, 600, Paint.Align.LEFT)

        // Mond
        val phase = moonPhase(System.currentTimeMillis())
        val mcx = w - pad - moonSize / 2; val mcy = h * 0.42f
        drawMoon(c, mcx, mcy, moonSize / 2, phase, bg.light)
        text(c, moonName(phase), mcx, mcy + moonSize / 2 + ts * 1.5f, ts * 0.85f, bg.textSecondary, 500, Paint.Align.CENTER)

        v.setImageViewBitmap(R.id.bg, b)
        v.tap(r, TapAction.WEATHER_REFRESH)
        v
    }

    private fun bez(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val u = 1 - t
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
    }

    private fun dur(sec: Long): String {
        val m = (sec / 60).toInt()
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /** Mondphase 0..1 (0 = Neumond, 0.5 = Vollmond). Referenz-Neumond 6.1.2000 18:14 UTC. */
    fun moonPhase(millis: Long): Float {
        val days = (millis - 947_182_440_000L) / 86_400_000.0
        val p = (days % 29.530588853) / 29.530588853
        return (if (p < 0) p + 1 else p).toFloat()
    }

    fun moonName(p: Float) = when {
        p < 0.03f || p > 0.97f -> "New moon"
        p < 0.22f -> "Waxing crescent"
        p < 0.28f -> "First quarter"
        p < 0.47f -> "Waxing gibbous"
        p < 0.53f -> "Full moon"
        p < 0.72f -> "Waning gibbous"
        p < 0.78f -> "Last quarter"
        else -> "Waning crescent"
    }

    private fun drawMoon(c: Canvas, cx: Float, cy: Float, r: Float, p: Float, light: Boolean) {
        val dark = if (light) Color.argb(60, 0, 0, 0) else Color.argb(70, 255, 255, 255)
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark })
        // Beleuchteter Teil: Halbkreis + Ellipse (Terminator)
        val lit = Path()
        val waxing = p < 0.5f
        val k = cos(2 * PI * p).toFloat() // 1 = Neumond, -1 = Vollmond
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        lit.arcTo(oval, -90f, if (waxing) 180f else -180f)
        val ex = r * k
        val ell = RectF(cx - abs(ex), cy - r, cx + abs(ex), cy + r)
        // Terminator zurück nach oben
        val sweep = if (waxing) (if (k > 0) -180f else 180f) else (if (k > 0) 180f else -180f)
        lit.arcTo(ell, 90f, sweep)
        lit.close()
        c.drawPath(lit, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EBD0.toInt() })
    }
}
