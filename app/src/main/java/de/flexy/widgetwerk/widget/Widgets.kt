package de.flexy.widgetwerk.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import de.flexy.widgetwerk.WidgetwerkApp
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.PhotoStore
import de.flexy.widgetwerk.data.WidgetKind
import kotlinx.coroutines.launch

/** Gemeinsame Basis: alle Widgets werden über den [WidgetUpdater] gerendert. */
abstract class BaseWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetwerkApp.scope.launch {
            try {
                ids.forEach { WidgetUpdater.update(context, it, kind) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        // Größe geändert → neu rendern (Bitmaps passen sonst nicht mehr)
        val pending = goAsync()
        WidgetwerkApp.scope.launch {
            try { WidgetUpdater.update(context, id, kind) } finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { id ->
            ConfigStore.load(context, id)?.let { PhotoStore.deleteAll(context, it.photos) }
            ConfigStore.delete(context, id)
        }
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
    }
}

class ClockWidget : BaseWidget(WidgetKind.CLOCK)
class AnalogWidget : BaseWidget(WidgetKind.ANALOG)
class DateWidget : BaseWidget(WidgetKind.DATE)
class WeatherWidget : BaseWidget(WidgetKind.WEATHER)
class PhotoWidget : BaseWidget(WidgetKind.PHOTO)
class GlassWidget : BaseWidget(WidgetKind.GLASS)
class DecoWidget : BaseWidget(WidgetKind.DECO)
class CalendarWidget : BaseWidget(WidgetKind.CALENDAR)
class BatteryWidget : BaseWidget(WidgetKind.BATTERY)
class MediaWidget : BaseWidget(WidgetKind.MEDIA)
class LauncherWidget : BaseWidget(WidgetKind.LAUNCHER)
class CountdownWidget : BaseWidget(WidgetKind.COUNTDOWN)
class NoteWidget : BaseWidget(WidgetKind.NOTE)
class WorldWidget : BaseWidget(WidgetKind.WORLD)
class ProgressWidget : BaseWidget(WidgetKind.PROGRESS)
class SunWidget : BaseWidget(WidgetKind.SUN)
class GlanceWidget : BaseWidget(WidgetKind.GLANCE)
class MonthWidget : BaseWidget(WidgetKind.MONTH)
class TorchWidget : BaseWidget(WidgetKind.TORCH)
class StorageWidget : BaseWidget(WidgetKind.STORAGE)
class TimerWidget : BaseWidget(WidgetKind.TIMER)
class CounterWidget : BaseWidget(WidgetKind.COUNTER)

/** Nächster Wecker geändert → Uhren und "At a glance" neu zeichnen. */
class AlarmChangedReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        WidgetUpdater.requestKind(context, WidgetKind.CLOCK)
        WidgetUpdater.requestKind(context, WidgetKind.GLANCE)
    }
}

/** Zuordnung Widget-Art ↔ Provider-Klasse. */
object WidgetRegistry {
    fun providerClass(kind: WidgetKind): Class<out BaseWidget> = when (kind) {
        WidgetKind.CLOCK -> ClockWidget::class.java
        WidgetKind.ANALOG -> AnalogWidget::class.java
        WidgetKind.DATE -> DateWidget::class.java
        WidgetKind.WEATHER -> WeatherWidget::class.java
        WidgetKind.PHOTO -> PhotoWidget::class.java
        WidgetKind.GLASS -> GlassWidget::class.java
        WidgetKind.DECO -> DecoWidget::class.java
        WidgetKind.CALENDAR -> CalendarWidget::class.java
        WidgetKind.BATTERY -> BatteryWidget::class.java
        WidgetKind.MEDIA -> MediaWidget::class.java
        WidgetKind.LAUNCHER -> LauncherWidget::class.java
        WidgetKind.COUNTDOWN -> CountdownWidget::class.java
        WidgetKind.NOTE -> NoteWidget::class.java
        WidgetKind.WORLD -> WorldWidget::class.java
        WidgetKind.PROGRESS -> ProgressWidget::class.java
        WidgetKind.SUN -> SunWidget::class.java
        WidgetKind.GLANCE -> GlanceWidget::class.java
        WidgetKind.MONTH -> MonthWidget::class.java
        WidgetKind.TORCH -> TorchWidget::class.java
        WidgetKind.STORAGE -> StorageWidget::class.java
        WidgetKind.TIMER -> TimerWidget::class.java
        WidgetKind.COUNTER -> CounterWidget::class.java
    }

    fun component(ctx: Context, kind: WidgetKind) = ComponentName(ctx, providerClass(kind))

    fun ids(ctx: Context, kind: WidgetKind): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx, kind))

    /** Alle platzierten Widgets dieser App. */
    fun all(ctx: Context): List<Pair<Int, WidgetKind>> =
        WidgetKind.entries.flatMap { k -> ids(ctx, k).map { it to k } }

    fun kindOf(ctx: Context, id: Int): WidgetKind? {
        val cls = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className ?: return null
        return WidgetKind.entries.firstOrNull { providerClass(it).name == cls }
    }
}
