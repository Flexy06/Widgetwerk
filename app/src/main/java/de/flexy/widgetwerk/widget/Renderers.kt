package de.flexy.widgetwerk.widget

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import de.flexy.widgetwerk.R
import de.flexy.widgetwerk.data.CalendarRepo
import de.flexy.widgetwerk.data.ClockFont
import de.flexy.widgetwerk.data.ClockStyle
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.DateStyle
import de.flexy.widgetwerk.data.DecoStyle
import de.flexy.widgetwerk.data.PhotoStore
import de.flexy.widgetwerk.data.ShapeKind
import de.flexy.widgetwerk.data.Surface
import de.flexy.widgetwerk.data.WeatherRepo
import de.flexy.widgetwerk.glass.Art
import de.flexy.widgetwerk.glass.Rendered
import de.flexy.widgetwerk.glass.Shapes
import de.flexy.widgetwerk.glass.SurfaceRenderer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Baut die RemoteViews aller Widget-Arten. */
object Renderers {

    private const val PX = TypedValue.COMPLEX_UNIT_PX

    // ------------------------------------------------------------------ Hilfen

    internal fun RemoteViews.textPx(id: Int, px: Float) = setTextViewTextSize(id, PX, px)

    internal fun RemoteViews.tint(id: Int, color: Int) = setInt(id, "setColorFilter", color)

    internal fun RemoteViews.visible(id: Int, v: Boolean) = setViewVisibility(id, if (v) View.VISIBLE else View.GONE)

    internal fun RemoteViews.tap(r: RenderCtx, action: TapAction) {
        if (!r.preview) setOnClickPendingIntent(R.id.root, WidgetUpdater.tapIntent(r.ctx, r.id, action))
    }

    internal fun RemoteViews.formats(id: Int, f12: String, f24: String, force24: Boolean?) {
        setCharSequence(id, "setFormat12Hour", if (force24 == true) f24 else f12)
        setCharSequence(id, "setFormat24Hour", if (force24 == false) f12 else f24)
    }

    internal fun pattern(skeleton: String) = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)

    internal fun fontLayout(f: ClockFont) = when (f) {
        ClockFont.GOOGLE_SANS -> R.layout.tc_google
        ClockFont.THIN -> R.layout.tc_thin
        ClockFont.LIGHT -> R.layout.tc_light
        ClockFont.BOLD -> R.layout.tc_black
        ClockFont.CONDENSED -> R.layout.tc_condensed
        ClockFont.SERIF -> R.layout.tc_serif
        ClockFont.MONO -> R.layout.tc_mono
    }

    internal fun surface(r: RenderCtx, art: Bitmap? = null, square: Boolean = false): Rendered =
        SurfaceRenderer.render(r.ctx, r.cfg, r.pal, r.geo, art = art, seed = r.id + r.cfg.seed, square = square)

    internal fun views(r: RenderCtx, layout: Int) = RemoteViews(r.ctx.packageName, layout)

    // ------------------------------------------------------------------ Uhr

    fun clock(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val bg = surface(r)
        val v = views(r, R.layout.w_clock)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        v.removeAllViews(R.id.time)

        val pad = r.dp(24f)
        val showDate = cfg.showDate && cfg.clockStyle != ClockStyle.MINIMAL
        val w = r.geo.widthPx - pad; val h = r.geo.heightPx - pad
        val fontWidth = when (cfg.font) { ClockFont.CONDENSED -> 0.5f; ClockFont.MONO -> 0.62f; ClockFont.BOLD -> 0.64f; else -> 0.58f }

        when (cfg.clockStyle) {
            ClockStyle.STACKED -> {
                val dateH = if (showDate) min(h * 0.14f, r.dp(22f)) * 1.4f else 0f
                var size = min((h - dateH) / 2.05f, w / (2 * fontWidth + 0.1f)) * cfg.textScale
                size = size.coerceAtLeast(r.dp(16f))
                val hours = RemoteViews(r.ctx.packageName, fontLayout(cfg.font)).apply {
                    formats(R.id.tc, "hh", "HH", cfg.force24h)
                    textPx(R.id.tc, size)
                    setTextColor(R.id.tc, bg.accent)
                }
                val minutes = RemoteViews(r.ctx.packageName, fontLayout(cfg.font)).apply {
                    formats(R.id.tc, "mm", "mm", null)
                    textPx(R.id.tc, size)
                    setTextColor(R.id.tc, bg.text)
                    setViewLayoutMargin(R.id.tc, RemoteViews.MARGIN_TOP, -size * 0.16f, PX)
                }
                v.addView(R.id.time, hours)
                v.addView(R.id.time, minutes)
                v.textPx(R.id.date, (dateH / 1.4f).coerceAtLeast(r.dp(11f)))
            }
            ClockStyle.LINE, ClockStyle.MINIMAL -> {
                val dateH = if (showDate) min(h * 0.2f, r.dp(24f)) * 1.4f else 0f
                var size = min((h - dateH) / 1.15f, w / (4.4f * fontWidth + 0.3f)) * cfg.textScale
                size = size.coerceAtLeast(r.dp(16f))
                val time = RemoteViews(r.ctx.packageName, fontLayout(cfg.font)).apply {
                    formats(R.id.tc, "h:mm", "HH:mm", cfg.force24h)
                    textPx(R.id.tc, size)
                    setTextColor(R.id.tc, bg.text)
                }
                v.addView(R.id.time, time)
                v.textPx(R.id.date, (dateH / 1.4f).coerceAtLeast(r.dp(11f)))
            }
        }
        v.visible(R.id.date, showDate)
        val datePattern = pattern(if (r.wDp < 200) "EEEdMMM" else "EEEEdMMMM")
        v.formats(R.id.date, datePattern, datePattern, null)
        v.setTextColor(R.id.date, if (cfg.clockStyle == ClockStyle.STACKED) bg.textSecondary else bg.accent)
        v.tap(r, TapAction.ALARMS)
        return v
    }

    // ------------------------------------------------------------------ Analoguhr

    fun analog(r: RenderCtx): RemoteViews {
        val bg = surface(r, square = true)
        val v = views(r, R.layout.w_analog)
        val sq = min(r.geo.widthPx, r.geo.heightPx)
        val s = bg.scale
        // Markierungen direkt ins Hintergrundbild
        val face = RectF(0f, 0f, bg.bitmap.width.toFloat(), bg.bitmap.height.toFloat()).let { Shapes.square(it) }
        Art.drawDialMarks(Canvas(bg.bitmap), face, ColorUtils.setAlphaComponent(bg.text, 150), bg.accent)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)

        // Zeiger als Bitmaps in "Zifferblattgröße". Über die Bitmap-Dichte wird erreicht,
        // dass die AnalogClock sie in voller Größe zeichnet, auch wenn sie kleiner gerendert wurden.
        val hs = max(8, (sq * s).roundToInt())
        val dpi = r.ctx.resources.displayMetrics.densityDpi
        fun prep(b: Bitmap) = b.apply { density = max(1, (dpi * s).roundToInt()) }
        val hour = prep(Art.hand(hs, Art.Hand.HOUR, bg.accent, r.density * s))
        val minute = prep(Art.hand(hs, Art.Hand.MINUTE, bg.text, r.density * s))
        val dial = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        v.setIcon(R.id.analog, "setDial", Icon.createWithBitmap(dial))
        v.setIcon(R.id.analog, "setHourHand", Icon.createWithBitmap(hour))
        v.setIcon(R.id.analog, "setMinuteHand", Icon.createWithBitmap(minute))
        if (r.cfg.showSeconds) {
            val sec = prep(Art.hand(hs, Art.Hand.SECOND, if (r.pal.dark) r.pal.tertiary else r.pal.a3[7], r.density * s))
            v.setIcon(R.id.analog, "setSecondHand", Icon.createWithBitmap(sec))
        }
        v.tap(r, TapAction.ALARMS)
        return v
    }

    // ------------------------------------------------------------------ Datum

    fun date(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        if (cfg.dateStyle == DateStyle.HEADLINE) {
            val bg = surface(r)
            val v = views(r, R.layout.w_date_headline)
            v.setImageViewBitmap(R.id.bg, bg.bitmap)
            val big = min(r.geo.heightPx * 0.34f, r.geo.widthPx / 7.5f) * cfg.textScale
            v.textPx(R.id.dow, big * 0.42f)
            v.textPx(R.id.day, big)
            v.setTextColor(R.id.dow, bg.accent)
            v.setTextColor(R.id.day, bg.text)
            v.formats(R.id.dow, "EEEE", "EEEE", null)
            val p = pattern("dMMMM")
            v.formats(R.id.day, p, p, null)
            v.tap(r, TapAction.CALENDAR)
            return v
        }
        val bg = surface(r, square = true)
        val v = views(r, R.layout.w_date)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val sq = min(r.geo.widthPx, r.geo.heightPx) * cfg.textScale
        v.textPx(R.id.dow, sq * 0.12f)
        v.textPx(R.id.day, sq * 0.36f)
        v.textPx(R.id.month, sq * 0.095f)
        v.setTextColor(R.id.dow, bg.accent)
        v.setTextColor(R.id.day, bg.text)
        v.setTextColor(R.id.month, bg.textSecondary)
        v.tap(r, TapAction.CALENDAR)
        return v
    }

    // ------------------------------------------------------------------ Wetter

    fun weather(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val bg = surface(r)
        val v = views(r, R.layout.w_weather)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val data = if (r.id >= 0) ConfigStore.weather(r.ctx, r.id) else null
        fun t(c: Double) = if (cfg.fahrenheit) (c * 9 / 5 + 32).roundToInt() else c.roundToInt()

        val small = r.wDp < 200 || r.hDp < 100
        val tempSize = (min(r.hDp * 0.32f, if (small) r.wDp * 0.3f else 64f).coerceIn(26f, 64f)) * r.density * cfg.textScale
        v.textPx(R.id.temp, tempSize)
        v.textPx(R.id.cond, (tempSize * 0.3f).coerceIn(r.dp(12f), r.dp(17f)))
        v.textPx(R.id.meta, (tempSize * 0.26f).coerceIn(r.dp(11f), r.dp(14f)))
        v.setTextColor(R.id.temp, bg.text)
        v.setTextColor(R.id.cond, bg.accent)
        v.setTextColor(R.id.meta, bg.textSecondary)
        val iconDp = (tempSize / r.density * 1.25f).coerceIn(36f, 84f)
        v.setViewLayoutWidth(R.id.icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutHeight(R.id.icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)

        v.removeAllViews(R.id.hourly)
        if (data == null) {
            v.setTextViewText(R.id.temp, "--°")
            v.setTextViewText(R.id.cond, if (cfg.lat == null) "Tap to set location" else "Loading…")
            v.setTextViewText(R.id.meta, cfg.place ?: "Weather")
            v.setImageViewBitmap(R.id.icon, Art.weatherIcon(2, true, r.dp(iconDp).roundToInt(), bg.light))
            v.visible(R.id.hourly, false)
            v.tap(r, if (cfg.lat == null) TapAction.CONFIGURE else TapAction.WEATHER_REFRESH)
            return v
        }
        v.setTextViewText(R.id.temp, "${t(data.temp)}°")
        v.setTextViewText(R.id.cond, WeatherRepo.describe(data.code))
        val hl = "↑${t(data.high)}°  ↓${t(data.low)}°"
        v.setTextViewText(R.id.meta, if (small) hl else listOfNotNull(cfg.place, hl).joinToString("  ·  "))
        v.setImageViewBitmap(R.id.icon, Art.weatherIcon(data.code, data.isDay, r.dp(iconDp).roundToInt(), bg.light))

        val showHourly = cfg.showHourly && r.hDp >= 150 && data.hours.size > 1
        v.visible(R.id.hourly, showHourly)
        if (showHourly) {
            val n = (r.wDp / 58f).toInt().coerceIn(3, 7)
            val iconPx = r.dp(30f).roundToInt()
            val tf = if (DateFormat.is24HourFormat(r.ctx)) "HH" else "ha"
            data.hours.drop(1).take(n).forEach { hp ->
                val item = RemoteViews(r.ctx.packageName, R.layout.item_hour)
                item.setTextViewText(R.id.h_time, DateFormat.format(tf, hp.epoch * 1000).toString().lowercase())
                item.setTextViewText(R.id.h_temp, "${t(hp.temp)}°")
                item.setTextColor(R.id.h_time, bg.textSecondary)
                item.setTextColor(R.id.h_temp, bg.text)
                item.setImageViewBitmap(R.id.h_icon, Art.weatherIcon(hp.code, hp.isDay, iconPx, bg.light))
                v.addView(R.id.hourly, item)
            }
        }
        v.tap(r, TapAction.WEATHER_REFRESH)
        return v
    }

    // ------------------------------------------------------------------ Foto

    fun photo(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val v = views(r, R.layout.w_canvas)
        val name = cfg.photos.getOrNull(cfg.photoIndex.mod(max(1, cfg.photos.size)))
        val s = SurfaceRenderer.scaleFor(r.geo.widthPx, r.geo.heightPx)
        val rw = max(2, (r.geo.widthPx * s).roundToInt()); val rh = max(2, (r.geo.heightPx * s).roundToInt())
        val photo = name?.let { PhotoStore.load(r.ctx, it, maxSide = max(rw, rh)) }
        if (photo == null) {
            val bg = SurfaceRenderer.render(r.ctx, cfg.copy(surface = if (cfg.surface == Surface.ART) Surface.FROST else cfg.surface), r.pal, r.geo, seed = r.id)
            v.setImageViewBitmap(R.id.bg, bg.bitmap)
            v.setTextViewText(R.id.hint, "Tap to choose photos")
            v.setTextColor(R.id.hint, bg.text)
            v.visible(R.id.hint, true)
            v.tap(r, TapAction.CONFIGURE)
            return v
        }
        val full = RectF(0f, 0f, rw.toFloat(), rh.toFloat())
        val corner = cfg.cornerDp * r.density * s
        val bmp: Bitmap = if (cfg.photoFrame) {
            // Glas-Passepartout: Hintergrund aus dem Material (bei "Artwork" das geblurrte Foto), Foto eingesetzt
            val bg = SurfaceRenderer.render(r.ctx, cfg, r.pal, r.geo, art = photo, seed = r.id)
            val out = bg.bitmap
            val inset = r.dp(10f) * s
            val inner = if (cfg.shape.polar) Shapes.square(full).apply { inset(inset, inset) } else RectF(full).apply { inset(inset, inset) }
            val innerPath = Shapes.path(cfg.shape, inner, max(0f, corner - inset))
            // Foto auf Innenfläche zuschneiden (Cover-Ausschnitt bezogen auf das Innenrechteck)
            val pic = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
            Canvas(pic).drawBitmap(photo, SurfaceRenderer.coverRect(photo.width, photo.height, inner.width(), inner.height()), inner,
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            SurfaceRenderer.mask(pic, innerPath)
            Canvas(out).drawBitmap(pic, 0f, 0f, null)
            out
        } else {
            val path = Shapes.path(cfg.shape, full, corner)
            val out = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
            val target = if (cfg.shape.polar) Shapes.square(full) else full
            Canvas(out).drawBitmap(photo, SurfaceRenderer.coverRect(photo.width, photo.height, target.width(), target.height()), target,
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            if (cfg.grain) SurfaceRenderer.drawGrain(Canvas(out), full, 10)
            SurfaceRenderer.finish(out, path, cfg.rim, r.density * s)
            out
        }
        v.setImageViewBitmap(R.id.bg, bmp)
        v.visible(R.id.hint, false)
        v.tap(r, if (cfg.photos.size > 1) TapAction.PHOTO_NEXT else TapAction.ALIGN)
        return v
    }

    // ------------------------------------------------------------------ Glas

    fun glass(r: RenderCtx): RemoteViews {
        val bg = surface(r)
        val v = views(r, R.layout.w_canvas)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        v.tap(r, TapAction.ALIGN)
        return v
    }

    // ------------------------------------------------------------------ Deko

    fun deco(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val v = views(r, R.layout.w_canvas)
        val s = SurfaceRenderer.scaleFor(r.geo.widthPx, r.geo.heightPx)
        val rw = max(2, (r.geo.widthPx * s).roundToInt()); val rh = max(2, (r.geo.heightPx * s).roundToInt())
        val full = RectF(0f, 0f, rw.toFloat(), rh.toFloat())
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val tones2 = if (cfg.colorRole == de.flexy.widgetwerk.data.ColorRole.TERTIARY) r.pal.a1 else r.pal.a3
        val seed = cfg.seed * 7919 + r.id
        val bmp = when (cfg.decoStyle) {
            DecoStyle.SHAPE, DecoStyle.STACK -> {
                // Freistehende Formen ohne Panel
                val out = Art.deco(cfg.decoStyle, rw, rh, cfg.shape, tones, tones2, r.pal.dark, seed, r.density * s, null)
                if (cfg.decoStyle == DecoStyle.SHAPE) {
                    if (cfg.grain) {
                        SurfaceRenderer.drawGrain(Canvas(out), full, 14)
                    }
                    SurfaceRenderer.finish(out, Shapes.path(cfg.shape, full, cfg.cornerDp * r.density * s), cfg.rim, r.density * s)
                }
                out
            }
            else -> {
                val panel = if (cfg.surface == Surface.NONE) null
                else SurfaceRenderer.base(r.ctx, cfg, r.pal, r.geo, rw, rh, s, null, seed)
                val out = Art.deco(cfg.decoStyle, rw, rh, cfg.shape, tones, tones2, r.pal.dark, seed, r.density * s, panel)
                if (cfg.grain) SurfaceRenderer.drawGrain(Canvas(out), full, 12)
                SurfaceRenderer.finish(out, Shapes.path(cfg.shape, full, cfg.cornerDp * r.density * s), cfg.rim, r.density * s)
                out
            }
        }
        v.setImageViewBitmap(R.id.bg, bmp)
        v.tap(r, TapAction.DECO_SHUFFLE)
        return v
    }

    // ------------------------------------------------------------------ Kalender

    fun calendar(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val bg = surface(r)
        val v = views(r, R.layout.w_calendar)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val head = (min(r.hDp * 0.2f, 44f) * r.density * cfg.textScale).coerceAtLeast(r.dp(26f))
        v.textPx(R.id.cal_day, head)
        v.textPx(R.id.cal_dow, head * 0.36f)
        v.textPx(R.id.cal_month, head * 0.31f)
        v.setTextColor(R.id.cal_day, bg.accent)
        v.setTextColor(R.id.cal_dow, bg.text)
        v.setTextColor(R.id.cal_month, bg.textSecondary)
        v.removeAllViews(R.id.events)

        if (!CalendarRepo.hasPermission(r.ctx)) {
            v.setTextViewText(R.id.empty, "Tap to allow calendar access")
            v.setTextColor(R.id.empty, bg.textSecondary)
            v.visible(R.id.empty, true)
            v.tap(r, TapAction.CONFIGURE)
            return v
        }
        val rows = ((r.hDp - 32f - head / r.density - 14f) / 42f).toInt().coerceAtLeast(1)
        val events = CalendarRepo.upcoming(r.ctx, cfg.calendarDays, rows)
        v.visible(R.id.empty, events.isEmpty())
        if (events.isEmpty()) {
            v.setTextViewText(R.id.empty, "Nothing planned for the next ${cfg.calendarDays} days ✨")
            v.setTextColor(R.id.empty, bg.textSecondary)
        }
        events.forEachIndexed { i, e ->
            val item = RemoteViews(r.ctx.packageName, R.layout.item_event)
            item.tint(R.id.ev_bar, e.color or 0xFF000000.toInt())
            item.setTextViewText(R.id.ev_title, e.title)
            item.setTextViewText(R.id.ev_time, CalendarRepo.label(r.ctx, e))
            item.setTextColor(R.id.ev_title, bg.text)
            item.setTextColor(R.id.ev_time, bg.textSecondary)
            if (!r.preview) {
                val open = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
                item.setOnClickPendingIntent(R.id.ev_root, WidgetUpdater.activityIntent(r.ctx, r.id, 20 + i, open))
            }
            v.addView(R.id.events, item)
        }
        v.tap(r, TapAction.CALENDAR)
        return v
    }

    // ------------------------------------------------------------------ Akku

    fun battery(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val bg = surface(r)
        val v = views(r, R.layout.w_battery)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        val bm = r.ctx.getSystemService(BatteryManager::class.java)
        val sticky = r.ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL || (plugged && level >= 100)
        val tempC = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val wide = r.wDp > r.hDp * 1.5f
        val ringDp = (min(r.hDp, if (wide) r.hDp else r.wDp) - 28f).coerceAtLeast(40f)
        val ringPx = r.dp(ringDp).roundToInt()
        val low = level <= 15 && !charging
        val progress = if (low) (if (bg.light) 0xFFB3261E.toInt() else 0xFFF2B8B5.toInt()) else bg.accent
        val track = ColorUtils.setAlphaComponent(bg.text, 40)
        v.setImageViewBitmap(R.id.ring, Art.batteryRing(ringPx, level / 100f, charging, track, progress, r.density))
        v.setViewLayoutWidth(R.id.ring, ringDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutHeight(R.id.ring, ringDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setTextViewText(R.id.pct, "$level%")
        v.textPx(R.id.pct, ringPx * (if (charging) 0.2f else 0.22f) * cfg.textScale)
        // Beim Laden sitzt der Blitz oben im Ring → Zahl etwas nach unten
        v.setViewLayoutMargin(R.id.pct, RemoteViews.MARGIN_TOP, if (charging) ringPx * 0.22f else 0f, TypedValue.COMPLEX_UNIT_PX)
        v.setTextColor(R.id.pct, bg.text)

        v.visible(R.id.info, wide)
        if (wide) {
            val state = when {
                full -> "Fully charged"
                charging -> "Charging"
                plugged -> "Plugged in"
                low -> "Battery low"
                else -> "On battery"
            }
            val remaining = if (charging) bm.computeChargeTimeRemaining() else -1L
            val detail = buildList {
                if (remaining > 0) add("Full in " + duration(remaining))
                if (tempC > 0) add(String.format(Locale.getDefault(), "%.0f °C", tempC))
            }.joinToString("  ·  ")
            v.setTextViewText(R.id.bstate, state)
            v.setTextViewText(R.id.bdetail, detail)
            v.textPx(R.id.bstate, (ringPx * 0.18f).coerceIn(r.dp(15f), r.dp(24f)))
            v.textPx(R.id.bdetail, (ringPx * 0.12f).coerceIn(r.dp(12f), r.dp(15f)))
            v.setTextColor(R.id.bstate, bg.text)
            v.setTextColor(R.id.bdetail, bg.textSecondary)
        }
        v.tap(r, TapAction.BATTERY)
        return v
    }

    private fun duration(ms: Long): String {
        val min = (ms / 60_000).toInt()
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }

    // ------------------------------------------------------------------ Medien

    /** Hintergrund zum Albumcover zwischenspeichern – der Fortschritt aktualisiert alle paar Sekunden. */
    private var mediaBgKey: Any? = null
    private var mediaBg: Rendered? = null

    fun media(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val access = MediaHelper.hasAccess(r.ctx)
        val c = if (access) MediaHelper.active(r.ctx) else null
        val art = MediaHelper.art(c)
        val key = listOf(r.id, art?.generationId, art?.width, cfg, r.geo, r.pal.hash)
        val bg = (if (key == mediaBgKey) mediaBg else null) ?: surface(r, art = art).also { if (!r.preview) { mediaBgKey = key; mediaBg = it } }
        val v = views(r, R.layout.w_media)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)

        val showArt = r.wDp >= 220
        val artDp = (r.hDp - 28f).coerceIn(48f, 150f)
        v.visible(R.id.art, showArt)
        if (showArt) {
            val px = r.dp(artDp).roundToInt()
            val (_, container, onContainer) = r.pal.role(cfg.colorRole)
            val bmp = if (art != null) {
                val p = Shapes.path(ShapeKind.ROUNDED, RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.2f)
                Art.shaped(art, px, px, p)
            } else Art.placeholderArt(px, container, onContainer)
            v.setImageViewBitmap(R.id.art, bmp)
            v.setViewLayoutWidth(R.id.art, artDp, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(R.id.art, artDp, TypedValue.COMPLEX_UNIT_DIP)
        }
        val title = MediaHelper.title(c)
        v.setTextViewText(R.id.title, when {
            !access -> "Now playing"
            title != null -> title
            else -> "Nothing playing"
        })
        v.setTextViewText(R.id.artist, when {
            !access -> "Tap to enable notification access"
            c != null -> MediaHelper.artist(c) ?: MediaHelper.appLabel(r.ctx, c) ?: ""
            else -> "Start music to see it here"
        })
        v.setTextColor(R.id.title, bg.text)
        v.setTextColor(R.id.artist, bg.textSecondary)
        v.textPx(R.id.title, (r.hDp * 0.1f).coerceIn(14f, 19f) * r.density)
        v.textPx(R.id.artist, (r.hDp * 0.075f).coerceIn(12f, 14f) * r.density)

        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val btn = if (bg.light) tones[8] else tones[3]
        val playing = MediaHelper.isPlaying(c)

        // Fortschritt (Welle + Tippzonen zum Springen + live laufende Zeit)
        val duration = MediaHelper.duration(c)
        val position = MediaHelper.position(c).coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
        val showProgress = cfg.mediaProgress && c != null && duration > 0 && r.hDp >= 150
        v.visible(R.id.progress_wrap, showProgress)
        v.visible(R.id.times, showProgress && r.hDp >= 175)
        v.removeAllViews(R.id.seek)
        if (showProgress) {
            val textDp = r.wDp - 28f - (if (showArt) artDp + 14f else 0f)
            v.setImageViewBitmap(R.id.progress, Art.progressWave(r.dp(textDp).roundToInt(), r.dp(22f).roundToInt(),
                position / duration.toFloat(), playing, ColorUtils.setAlphaComponent(bg.text, 50), btn, r.density))
            v.setChronometer(R.id.elapsed, android.os.SystemClock.elapsedRealtime() - position, null, playing)
            v.setTextColor(R.id.elapsed, bg.textSecondary)
            v.setTextViewText(R.id.duration, DateUtils.formatElapsedTime(duration / 1000))
            v.setTextColor(R.id.duration, bg.textSecondary)
            if (!r.preview) for (i in 0 until SEEK_CELLS) {
                val cell = RemoteViews(r.ctx.packageName, R.layout.item_seek)
                cell.setOnClickPendingIntent(R.id.seek_cell, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.SEEK, 6 + i) {
                    it.putExtra(ActionReceiver.EXTRA_FRACTION, (i + 0.5f) / SEEK_CELLS)
                })
                v.addView(R.id.seek, cell)
            }
        }

        // Steuerung
        val onBtn = if (ColorUtils.calculateLuminance(btn) > 0.45) Color.BLACK else Color.WHITE
        val playPx = r.dp(50f).roundToInt()  // wird von ImageView passend skaliert
        val shape = Bitmap.createBitmap(playPx, playPx, Bitmap.Config.ARGB_8888)
        Canvas(shape).drawPath(Shapes.path(ShapeKind.COOKIE9, RectF(0f, 0f, playPx.toFloat(), playPx.toFloat()), 0f),
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = btn })
        v.setImageViewBitmap(R.id.play_bg, shape)
        v.setImageViewResource(R.id.play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        v.tint(R.id.play, onBtn)
        v.tint(R.id.prev, bg.text)
        v.tint(R.id.next, bg.text)
        // Kompakt bei 1 Zeile Höhe: Knöpfe passend verkleinern statt abschneiden
        val avail = r.hDp - 28f
        val showArtist = avail >= 22f + 18f + 4f + 40f
        var controlsDp = avail - 4f - 22f - (if (showArtist) 18f else 0f) - (if (showProgress) 22f + 6f else 0f) - (if (showProgress && r.hDp >= 175) 14f else 0f)
        val showTitle = controlsDp >= 30f || !access
        if (!showTitle) controlsDp = avail - 4f
        v.visible(R.id.title, showTitle)
        v.visible(R.id.artist, showArtist || !access)
        val playDp = controlsDp.coerceIn(26f, 50f)
        val smallDp = (playDp * 0.78f).coerceAtMost(38f)
        v.setViewLayoutWidth(R.id.play_frame, playDp, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutHeight(R.id.play_frame, playDp, TypedValue.COMPLEX_UNIT_DIP)
        listOf(R.id.prev, R.id.next, R.id.custom1, R.id.custom2).forEach {
            v.setViewLayoutWidth(it, smallDp, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(it, smallDp, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewPadding(it, r.dp(smallDp * 0.2f).roundToInt(), r.dp(smallDp * 0.2f).roundToInt(), r.dp(smallDp * 0.2f).roundToInt(), r.dp(smallDp * 0.2f).roundToInt())
        }
        val pp = r.dp(playDp * 0.26f).roundToInt()
        v.setViewPadding(R.id.play, pp, pp, pp, pp)
        v.visible(R.id.controls, access)

        // Zusatzknöpfe des Players (z. B. Spotify-Herz)
        val actions = if (cfg.mediaActions && c != null) MediaHelper.customActions(c).take(2) else emptyList()
        listOf(R.id.custom1, R.id.custom2).forEachIndexed { i, vid ->
            val a = actions.getOrNull(i)
            val icon = a?.let { MediaHelper.actionIcon(r.ctx, c!!, it, r.dp(24f).roundToInt()) }
            v.visible(vid, icon != null)
            if (a != null && icon != null) {
                v.setImageViewBitmap(vid, icon)
                v.tint(vid, if (i == 0) btn else bg.text)
                v.setContentDescription(vid, a.name)
                if (!r.preview) v.setOnClickPendingIntent(vid, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.CUSTOM, 4 + i) {
                    it.putExtra(ActionReceiver.EXTRA_CUSTOM, a.action)
                })
            }
        }
        if (!r.preview) {
            v.setOnClickPendingIntent(R.id.play, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.PLAY_PAUSE, 1))
            v.setOnClickPendingIntent(R.id.prev, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.PREV, 2))
            v.setOnClickPendingIntent(R.id.next, WidgetUpdater.actionIntent(r.ctx, r.id, ActionReceiver.NEXT, 3))
        }
        v.tap(r, if (access) TapAction.MEDIA_OPEN else TapAction.CONFIGURE)
        return v
    }

    private const val SEEK_CELLS = 12

    // ------------------------------------------------------------------ App-Dock

    fun launcher(r: RenderCtx): RemoteViews {
        val cfg = r.cfg
        val bg = surface(r)
        val v = views(r, R.layout.w_launcher)
        v.setImageViewBitmap(R.id.bg, bg.bitmap)
        v.removeAllViews(R.id.apps)
        v.visible(R.id.dock_date, cfg.dockDate && r.hDp >= 100)
        v.setTextColor(R.id.dock_date, bg.text)
        val dp = pattern("EEEEdMMMM")
        v.formats(R.id.dock_date, dp, dp, null)

        val pm = r.ctx.packageManager
        val apps = cfg.apps.mapNotNull { ComponentName.unflattenFromString(it) }
        v.visible(R.id.hint, apps.isEmpty())
        if (apps.isEmpty()) {
            v.setTextViewText(R.id.hint, "Tap to pick your apps")
            v.setTextColor(R.id.hint, bg.text)
            v.tap(r, TapAction.CONFIGURE)
            return v
        }
        val dateDp = if (cfg.dockDate && r.hDp >= 100) 30f else 0f
        val labelDp = if (cfg.dockLabels) 18f else 0f
        val iconDp = min(min(r.hDp - 20f - dateDp - labelDp, r.wDp / apps.size - 14f), 64f).coerceAtLeast(24f)
        val iconPx = r.dp(iconDp).roundToInt()
        val tones = SurfaceRenderer.tonesFor(r.pal, cfg.colorRole)
        val container = if (r.pal.dark) tones[9] else tones[3]
        val onContainer = if (r.pal.dark) tones[3] else tones[10]
        apps.forEachIndexed { i, cn ->
            val drawable = runCatching { pm.getActivityIcon(cn) }.getOrNull() ?: return@forEachIndexed
            val item = RemoteViews(r.ctx.packageName, R.layout.item_app)
            item.setImageViewBitmap(R.id.app_icon, Art.appIcon(drawable, iconPx, cfg.themedIcons, container, onContainer))
            item.setViewLayoutWidth(R.id.app_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            item.setViewLayoutHeight(R.id.app_icon, iconDp, TypedValue.COMPLEX_UNIT_DIP)
            val label = runCatching { pm.getActivityInfo(cn, 0).loadLabel(pm) }.getOrNull()
            item.setContentDescription(R.id.app_icon, label)
            item.visible(R.id.app_label, cfg.dockLabels)
            if (cfg.dockLabels) {
                item.setTextViewText(R.id.app_label, label)
                item.setTextColor(R.id.app_label, bg.text)
            }
            if (!r.preview) {
                val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                item.setOnClickPendingIntent(R.id.app_cell, WidgetUpdater.activityIntent(r.ctx, r.id, 20 + i, launch))
            }
            v.addView(R.id.apps, item)
        }
        v.tap(r, TapAction.ALIGN)
        return v
    }
}
