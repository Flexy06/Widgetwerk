package de.flexy.widgetwerk.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import de.flexy.widgetwerk.R
import de.flexy.widgetwerk.WidgetwerkApp
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.WidgetConfig
import de.flexy.widgetwerk.data.WidgetKind
import de.flexy.widgetwerk.glass.Palette
import de.flexy.widgetwerk.glass.WidgetGeometry
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

/** Alles, was ein Renderer braucht. [preview] = Vorschau in der App (keine Klick-Intents). */
class RenderCtx(
    val ctx: Context,
    val id: Int,
    val kind: WidgetKind,
    val cfg: WidgetConfig,
    val geo: WidgetGeometry,
    val pal: Palette,
    val preview: Boolean,
) {
    val density get() = geo.density
    val wDp get() = geo.widthPx / geo.density
    val hDp get() = geo.heightPx / geo.density
    fun dp(v: Float) = v * geo.density
}

/** Was beim Tippen auf ein Widget passiert. */
enum class TapAction { ALIGN, ALARMS, CALENDAR, WEATHER_REFRESH, PHOTO_NEXT, DECO_SHUFFLE, BATTERY, MEDIA_OPEN, CONFIGURE }

object WidgetUpdater {
    private const val TAG = "Widgetwerk"
    private val lock = Mutex()   // Rendern nacheinander → begrenzter Speicherbedarf

    /** Asynchron neu rendern (aus UI, Activities, Services). */
    fun request(ctx: Context, id: Int) {
        val app = ctx.applicationContext
        WidgetwerkApp.scope.launch { WidgetRegistry.kindOf(app, id)?.let { update(app, id, it) } }
    }

    fun requestKind(ctx: Context, kind: WidgetKind) {
        val app = ctx.applicationContext
        WidgetwerkApp.scope.launch { WidgetRegistry.ids(app, kind).forEach { update(app, it, kind) } }
    }

    fun requestAll(ctx: Context) {
        val app = ctx.applicationContext
        WidgetwerkApp.scope.launch { updateAll(app) }
    }

    suspend fun updateAll(ctx: Context) {
        WidgetRegistry.all(ctx).forEach { (id, kind) -> update(ctx, id, kind) }
    }

    suspend fun update(ctx: Context, id: Int, kind: WidgetKind): Unit = lock.withLock {
        val cfg = ConfigStore.loadOrDefault(ctx, id, kind)
        val views = try {
            build(ctx, id, kind, cfg, geometry(ctx, id, kind), preview = false)
        } catch (t: Throwable) {
            Log.e(TAG, "Render failed for $kind #$id", t)
            RemoteViews(ctx.packageName, R.layout.w_loading)
        }
        runCatching { AppWidgetManager.getInstance(ctx).updateAppWidget(id, views) }
            .onFailure { Log.e(TAG, "updateAppWidget failed", it) }
        Unit
    }

    fun build(ctx: Context, id: Int, kind: WidgetKind, cfg: WidgetConfig, geo: WidgetGeometry, preview: Boolean): RemoteViews {
        val r = RenderCtx(ctx, id, kind, cfg, geo, Palette.of(ctx), preview)
        return when (kind) {
            WidgetKind.CLOCK -> Renderers.clock(r)
            WidgetKind.ANALOG -> Renderers.analog(r)
            WidgetKind.DATE -> Renderers.date(r)
            WidgetKind.WEATHER -> Renderers.weather(r)
            WidgetKind.PHOTO -> Renderers.photo(r)
            WidgetKind.GLASS -> Renderers.glass(r)
            WidgetKind.DECO -> Renderers.deco(r)
            WidgetKind.CALENDAR -> Renderers.calendar(r)
            WidgetKind.BATTERY -> Renderers.battery(r)
            WidgetKind.MEDIA -> Renderers.media(r)
            WidgetKind.LAUNCHER -> Renderers.launcher(r)
            WidgetKind.COUNTDOWN -> MoreRenderers.countdown(r)
            WidgetKind.NOTE -> MoreRenderers.note(r)
            WidgetKind.WORLD -> MoreRenderers.world(r)
            WidgetKind.PROGRESS -> MoreRenderers.progress(r)
            WidgetKind.SUN -> MoreRenderers.sun(r)
        }
    }

    /** Standardgröße (dp) je Art – für Vorschauen und wenn der Launcher noch nichts gemeldet hat. */
    fun defaultDp(kind: WidgetKind): Pair<Float, Float> = when (kind) {
        WidgetKind.CLOCK, WidgetKind.WEATHER, WidgetKind.MEDIA -> 340f to 170f
        WidgetKind.CALENDAR -> 340f to 260f
        WidgetKind.LAUNCHER -> 340f to 84f
        WidgetKind.SUN -> 340f to 170f
        WidgetKind.NOTE, WidgetKind.WORLD -> 250f to 170f
        else -> 165f to 165f
    }

    /** Tatsächliche Größe + Bildschirmposition eines platzierten Widgets. */
    fun geometry(ctx: Context, id: Int, kind: WidgetKind): WidgetGeometry {
        val d = ctx.resources.displayMetrics.density
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
        var wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        var hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        if (wDp <= 0f || hDp <= 0f) defaultDp(kind).let { wDp = it.first; hDp = it.second }
        var w = (wDp * d).roundToInt(); var h = (hDp * d).roundToInt()
        var bounds: Rect? = ConfigStore.bounds(ctx, id)
        if (bounds != null) {
            if (abs(bounds.width() - w) < w * 0.2f && abs(bounds.height() - h) < h * 0.2f) {
                // Launcher-Maße beim Tippen sind exakt → bevorzugen
                w = bounds.width(); h = bounds.height()
            } else {
                // Größe geändert: Position übernehmen, Größe neu
                bounds = Rect(bounds.left, bounds.top, bounds.left + w, bounds.top + h)
            }
        }
        return WidgetGeometry(w, h, d, bounds)
    }

    fun previewGeometry(ctx: Context, id: Int?, kind: WidgetKind): WidgetGeometry {
        if (id != null && id != AppWidgetManager.INVALID_APPWIDGET_ID && WidgetRegistry.kindOf(ctx, id) != null) return geometry(ctx, id, kind)
        val d = ctx.resources.displayMetrics.density
        val (w, h) = defaultDp(kind)
        return WidgetGeometry((w * d).roundToInt(), (h * d).roundToInt(), d, null)
    }

    // ------------------------------------------------------------------ Intents

    fun tapIntent(ctx: Context, id: Int, action: TapAction): PendingIntent {
        val i = Intent(ctx, TapActivity::class.java)
            .setData(Uri.parse("widgetwerk://tap/$id/${action.name}"))
            .putExtra(TapActivity.EXTRA_ID, id)
            .putExtra(TapActivity.EXTRA_ACTION, action.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        // MUTABLE, damit der Launcher die Bildschirmposition (sourceBounds) einfügen kann
        return PendingIntent.getActivity(ctx, id * 64, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    fun actionIntent(ctx: Context, id: Int, action: String, slot: Int, configure: (Intent) -> Unit = {}): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("widgetwerk://action/$id/$action/$slot"))
            .putExtra(TapActivity.EXTRA_ID, id)
        configure(i)
        return PendingIntent.getBroadcast(ctx, id * 64 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun activityIntent(ctx: Context, id: Int, slot: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            ctx, id * 64 + slot, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
