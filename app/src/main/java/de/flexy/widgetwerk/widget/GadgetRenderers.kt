package de.flexy.widgetwerk.widget

import android.app.usage.StorageStatsManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.AlarmClock
import android.text.format.Formatter
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import de.flexy.widgetwerk.R
import de.flexy.widgetwerk.data.CalendarRepo
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.ShapeKind
import de.flexy.widgetwerk.data.Surface
import de.flexy.widgetwerk.data.WeatherRepo
import de.flexy.widgetwerk.glass.Art
import de.flexy.widgetwerk.glass.Shapes
import de.flexy.widgetwerk.glass.SurfaceRenderer
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Weitere Widgets: At a glance, Monat, Taschenlampe, Speicher, Timer, Zähler. */
object GadgetRenderers {

    private val sans = Typeface.create("google-sans", Typeface.NORMAL)
    private fun paint(size: Float, col: Int, weight: Int, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = col; typeface = Typeface.create(sans, weight, false); textAlign = align }

    // ------------------------------------------------------------------ At a glance

    fun glance(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val v = views(r, R.layout.w_glance)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val big = (min(r.hDp * 0.2f, r.wDp / 11f) * cfg.textScale).coerceIn(16f, 40f) * r.density
        val p = pattern("EEEEdMMMM")
        v.formats(R.id.gl_date, p, p, null)
        v.textPx(R.id.gl_date, big)
        v.setTextColor(R.id.gl_date, bg.text)

        // Zeile 1: Wetter
        val wx = if (r.id >= 0) ConfigStore.weather(r.ctx, r.id) else null
        val small = big * 0.6f
        if (wx != null) {
            val t = if (cfg.fahrenheit) (wx.temp * 9 / 5 + 32).roundToInt() else wx.temp.roundToInt()
            v.setImageViewBitmap(R.id.gl_wx_icon, Art.weatherIcon(wx.code, wx.isDay, (small * 1.5f).roundToInt(), bg.light))
            v.setTextViewText(R.id.gl_wx, "$t°  ·  ${WeatherRepo.describe(wx.code)}")
            v.visible(R.id.gl_wx_icon, true)
        } else {
            v.setTextViewText(R.id.gl_wx, if (cfg.lat == null) "Tap to set your location" else "Loading weather…")
            v.visible(R.id.gl_wx_icon, false)
        }
        v.textPx(R.id.gl_wx, small)
        v.setTextColor(R.id.gl_wx, bg.text)
        v.setViewLayoutWidth(R.id.gl_wx_icon, small * 1.5f, TypedValue.COMPLEX_UNIT_PX)
        v.setViewLayoutHeight(R.id.gl_wx_icon, small * 1.5f, TypedValue.COMPLEX_UNIT_PX)

        // Zeile 2: nächster Termin (heute/morgen) oder Wecker
        val event = CalendarRepo.upcoming(r.ctx, 1, 1).firstOrNull()
        val alarm = Gadgets.nextAlarm(r.ctx)
        when {
            event != null -> {
                v.setImageViewResource(R.id.gl_icon2, R.drawable.ic_event)
                v.setTextViewText(R.id.gl_line2, event.title + "  ·  " + CalendarRepo.label(r.ctx, event).substringAfter("· "))
            }
            alarm != null -> {
                v.setImageViewResource(R.id.gl_icon2, R.drawable.ic_alarm)
                v.setTextViewText(R.id.gl_line2, "Alarm $alarm")
            }
        }
        v.visible(R.id.gl_row2, (event != null || alarm != null) && r.hDp >= 110)
        v.tint(R.id.gl_icon2, bg.accent)
        v.textPx(R.id.gl_line2, small * 0.92f)
        v.setTextColor(R.id.gl_line2, bg.textSecondary)
        v.tap(r, if (cfg.lat == null) TapAction.CONFIGURE else TapAction.CALENDAR)
        v
    }

    // ------------------------------------------------------------------ Monat

    fun month(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val b = bg.bitmap
        val c = Canvas(b)
        val dp = r.density * bg.scale
        val w = b.width.toFloat(); val h = b.height.toFloat()
        val pad = 14 * dp
        val today = LocalDate.now()
        val first = today.withDayOfMonth(1)
        val wf = WeekFields.of(Locale.getDefault())
        val offset = (first.dayOfWeek.value - wf.firstDayOfWeek.value + 7) % 7
        val rows = (offset + today.lengthOfMonth() + 6) / 7
        val zone = ZoneId.systemDefault()
        val events = CalendarRepo.eventDays(r.ctx, first.atStartOfDay(zone).toInstant().toEpochMilli(),
            first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli())

        val headerH = min(h * 0.16f, 26 * dp)
        val title = first.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }
        c.drawText(title, pad, pad + headerH * 0.75f, paint(headerH * 0.8f, bg.accent, 600, Paint.Align.LEFT))
        c.drawText(today.year.toString(), w - pad, pad + headerH * 0.75f, paint(headerH * 0.6f, bg.textSecondary, 500, Paint.Align.RIGHT))

        val gridTop = pad + headerH * 1.3f
        val cellW = (w - 2 * pad) / 7f
        val cellH = (h - gridTop - pad) / (rows + 0.7f)
        val fs = min(cellW, cellH) * 0.42f
        // Wochentage
        for (i in 0 until 7) {
            val dow = wf.firstDayOfWeek.plus(i.toLong())
            val name = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            c.drawText(name, pad + cellW * (i + 0.5f), gridTop + cellH * 0.45f, paint(fs * 0.85f, bg.textSecondary, 600))
        }
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val todayFill = if (bg.light) tones[8] else tones[3]
        val onToday = if (ColorUtils.calculateLuminance(todayFill) > 0.45) Color.BLACK else Color.WHITE
        for (d in 1..today.lengthOfMonth()) {
            val idx = offset + d - 1
            val cx = pad + cellW * (idx % 7 + 0.5f)
            val cy = gridTop + cellH * (idx / 7 + 1.2f)
            val isToday = d == today.dayOfMonth
            if (isToday) {
                val s = min(cellW, cellH) * 0.92f
                c.drawPath(Shapes.path(ShapeKind.COOKIE9, RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2), 0f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = todayFill })
            }
            val past = d < today.dayOfMonth
            val col = when { isToday -> onToday; past -> ColorUtils.setAlphaComponent(bg.text, 110); else -> bg.text }
            c.drawText(d.toString(), cx, cy + fs * 0.35f, paint(fs, col, if (isToday) 700 else 500))
            if (!isToday && today.withDayOfMonth(d) in events) {
                c.drawCircle(cx, cy + fs * 0.85f, 1.8f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.accent })
            }
        }
        val v = views(r, R.layout.w_canvas)
        v.setImageViewBitmap(R.id.bg, b)
        v.tap(r, TapAction.CALENDAR)
        v
    }

    // ------------------------------------------------------------------ Taschenlampe

    fun torch(r: RenderCtx): RemoteViews = with(Renderers) {
        val on = Gadgets.torchOn(r.ctx)
        val cfg = if (on) r.cfg.copy(surface = Surface.ACCENT) else r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id, square = true)
        if (on) {
            // Leuchtender Kern
            val b = bg.bitmap
            val cx = b.width / 2f; val cy = b.height / 2f; val rad = min(cx, cy) * 0.75f
            Canvas(b).drawCircle(cx, cy, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(cx, cy, rad, Color.argb(110, 255, 255, 255), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
            })
        }
        val v = views(r, R.layout.w_torch)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val iconDp = (min(r.wDp, r.hDp) * 0.4f).coerceIn(20f, 72f)
        v.setViewLayoutWidth(R.id.torch_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutHeight(R.id.torch_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        v.tint(R.id.torch_icon, if (on) bg.text else ColorUtils.setAlphaComponent(bg.text, 200))
        v.setContentDescription(R.id.torch_icon, if (on) "Flashlight on" else "Flashlight off")
        v.tap(r, TapAction.TORCH)
        v
    }

    // ------------------------------------------------------------------ Speicher

    fun storage(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val b = bg.bitmap
        val c = Canvas(b)
        val dp = r.density * bg.scale
        val st = StatFs(Environment.getDataDirectory().path)
        val free = st.availableBytes
        // Nenngröße (z. B. 256 GB) statt der formatierten Partitionsgröße
        val total = runCatching { r.ctx.getSystemService(StorageStatsManager::class.java).getTotalBytes(StorageManager.UUID_DEFAULT) }
            .getOrDefault(st.totalBytes)
        val used = (total - free).coerceAtLeast(0)
        val f = used.toFloat() / total.coerceAtLeast(1)
        val w = b.width.toFloat(); val h = b.height.toFloat()
        val pad = 16 * dp
        val wide = w > h * 1.5f
        val ring = (min(if (wide) h else w, h) - 2 * pad - (if (wide) 0f else 22 * dp)).toInt().coerceAtLeast(10)
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val prog = if (f > 0.9f) (if (bg.light) 0xFFB3261E.toInt() else 0xFFF2B8B5.toInt()) else if (bg.light) tones[8] else tones[3]
        val ringBmp = Art.batteryRing(ring, f, false, ColorUtils.setAlphaComponent(bg.text, 40), prog, dp)
        val rx = if (wide) pad else (w - ring) / 2
        val ry = if (wide) (h - ring) / 2 else pad
        c.drawBitmap(ringBmp, rx, ry, null)
        c.drawText("${(f * 100).roundToInt()}%", rx + ring / 2f, ry + ring / 2f + ring * 0.08f, paint(ring * 0.22f, bg.text, 600))
        val freeTxt = Formatter.formatShortFileSize(r.ctx, free)
        val totalTxt = Formatter.formatShortFileSize(r.ctx, total)
        if (wide) {
            val x = pad * 2 + ring
            c.drawText("Storage", x, h / 2 - ring * 0.12f, paint(ring * 0.17f, bg.text, 600, Paint.Align.LEFT))
            c.drawText("$freeTxt free of $totalTxt", x, h / 2 + ring * 0.14f, paint(ring * 0.12f, bg.textSecondary, 500, Paint.Align.LEFT))
        } else {
            c.drawText("$freeTxt free", w / 2, h - pad * 0.9f, paint(13 * dp, bg.textSecondary, 500))
        }
        val v = views(r, R.layout.w_canvas)
        v.setImageViewBitmap(R.id.bg, b)
        v.tap(r, TapAction.STORAGE)
        v
    }

    // ------------------------------------------------------------------ Timer

    fun timer(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val v = views(r, R.layout.w_timer)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        v.setTextColor(R.id.tm_title, bg.text)
        v.visible(R.id.tm_title, r.wDp >= 260)
        v.removeAllViews(R.id.tm_presets)
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val shapes = listOf(ShapeKind.COOKIE9, ShapeKind.SUNNY, ShapeKind.CLOVER, ShapeKind.COOKIE6, ShapeKind.FLOWER)
        val sizeDp = min(r.hDp - 20f, 56f).coerceAtLeast(30f)
        val px = r.dp(sizeDp).roundToInt()
        cfg.timerPresets.take(5).forEachIndexed { i, minutes ->
            val fill = if (bg.light) tones[3 + (i % 2)] else tones[8 - (i % 2)]
            val shape = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            Canvas(shape).drawPath(Shapes.path(shapes[i % shapes.size], RectF(0f, 0f, px.toFloat(), px.toFloat()), 0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            val cell = RemoteViews(r.ctx.packageName, R.layout.item_timer)
            cell.setImageViewBitmap(R.id.tm_shape, shape)
            cell.setViewLayoutWidth(R.id.tm_shape, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
            cell.setViewLayoutHeight(R.id.tm_shape, sizeDp, TypedValue.COMPLEX_UNIT_DIP)
            cell.setTextViewText(R.id.tm_label, if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}h" else "$minutes′")
            cell.setTextColor(R.id.tm_label, if (ColorUtils.calculateLuminance(fill) > 0.45) Color.BLACK else Color.WHITE)
            cell.textPx(R.id.tm_label, r.dp(sizeDp * 0.3f))
            if (!r.preview) {
                val start = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "$minutes min")
                cell.setOnClickPendingIntent(R.id.tm_cell, WidgetUpdater.activityIntent(r.ctx, r.id, 20 + i, start))
            }
            v.addView(R.id.tm_presets, cell)
        }
        v.tap(r, TapAction.TIMERS)
        v
    }

    // ------------------------------------------------------------------ Zähler

    fun counter(r: RenderCtx): RemoteViews = with(Renderers) {
        val cfg = r.cfg
        val today = LocalDate.now().toEpochDay()
        val value = if (cfg.counterDaily && cfg.counterDay != today) 0 else cfg.counterValue
        val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, seed = r.id)
        val v = views(r, R.layout.w_counter)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val accent = if (bg.light) tones[8] else tones[3]
        val ringDp = (min(r.hDp - 24f, r.wDp - 120f)).coerceIn(48f, 140f)
        val ringPx = r.dp(ringDp).roundToInt()
        val goal = cfg.counterGoal.coerceAtLeast(1)
        v.setImageViewBitmap(R.id.ct_ring, Art.batteryRing(ringPx, value / goal.toFloat(), value >= goal,
            ColorUtils.setAlphaComponent(bg.text, 40), accent, r.density))
        v.setViewLayoutWidth(R.id.ct_ring, ringDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutHeight(R.id.ct_ring, ringDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setTextViewText(R.id.ct_value, if (cfg.counterGoal > 0) "$value/${cfg.counterGoal}" else "$value")
        v.setTextViewText(R.id.ct_label, cfg.counterLabel)
        v.textPx(R.id.ct_value, ringPx * 0.22f)
        v.textPx(R.id.ct_label, ringPx * 0.11f)
        v.setTextColor(R.id.ct_value, bg.text)
        v.setTextColor(R.id.ct_label, bg.textSecondary)
        val plusPx = r.dp(44f).roundToInt()
        val plusBg = Bitmap.createBitmap(plusPx, plusPx, Bitmap.Config.ARGB_8888)
        Canvas(plusBg).drawPath(Shapes.path(ShapeKind.COOKIE9, RectF(0f, 0f, plusPx.toFloat(), plusPx.toFloat()), 0f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        v.setImageViewBitmap(R.id.ct_plus_bg, plusBg)
        v.tint(R.id.ct_plus, if (ColorUtils.calculateLuminance(accent) > 0.45) Color.BLACK else Color.WHITE)
        v.tint(R.id.ct_minus, bg.text)
        if (!r.preview) {
            v.setOnClickPendingIntent(R.id.ct_plus, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.COUNT, 1) { it.putExtra(ActionReceiver.EXTRA_DELTA, 1) })
            v.setOnClickPendingIntent(R.id.ct_minus, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.COUNT, 2) { it.putExtra(ActionReceiver.EXTRA_DELTA, -1) })
        }
        v.tap(r, TapAction.CONFIGURE)
        v
    }
}
