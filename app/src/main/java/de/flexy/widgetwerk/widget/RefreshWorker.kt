package de.flexy.widgetwerk.widget

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.flexy.widgetwerk.data.CalendarRepo
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.WeatherRepo
import de.flexy.widgetwerk.data.WidgetKind
import de.flexy.widgetwerk.glass.Palette
import de.flexy.widgetwerk.glass.WallpaperSource
import java.util.concurrent.TimeUnit

/**
 * Regelmäßige Aktualisierung (alle 30 min): Wetter, Diashow, Akku, Kalender,
 * Wallpaper- und Farbwechsel. Uhren laufen über TextClock/AnalogClock ganz ohne Updates.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (WidgetRegistry.all(ctx).isEmpty()) return Result.success()

        // Wallpaper oder dynamische Farben geändert?
        WallpaperSource.refreshSystemIfChanged(ctx)
        val hash = Palette.of(ctx).hash
        if (hash != ConfigStore.global(ctx).paletteHash) ConfigStore.updateGlobal(ctx) { it.copy(paletteHash = hash) }

        // Wetter
        WidgetRegistry.ids(ctx, WidgetKind.WEATHER).forEach { id ->
            val cfg = ConfigStore.load(ctx, id) ?: return@forEach
            val lat = cfg.lat ?: return@forEach
            val lon = cfg.lon ?: return@forEach
            val age = System.currentTimeMillis() - (ConfigStore.weather(ctx, id)?.fetchedAt ?: 0)
            if (age > 25 * 60_000) runCatching { WeatherRepo.fetch(lat, lon) }.onSuccess { ConfigStore.saveWeather(ctx, id, it) }
        }

        // Diashow
        WidgetRegistry.ids(ctx, WidgetKind.PHOTO).forEach { id ->
            val cfg = ConfigStore.load(ctx, id) ?: return@forEach
            if (cfg.photos.size > 1 && cfg.slideshowMinutes > 0 &&
                System.currentTimeMillis() - cfg.photoLastSwitch >= cfg.slideshowMinutes * 60_000L - 60_000L
            ) {
                ConfigStore.save(ctx, id, cfg.copy(photoIndex = (cfg.photoIndex + 1) % cfg.photos.size, photoLastSwitch = System.currentTimeMillis()))
            }
        }

        WidgetUpdater.updateAll(ctx)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "widgetwerk-refresh"
        private const val NOW = "widgetwerk-refresh-now"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
            CalendarWatcher.schedule(ctx)
        }

        /** Sofort einmal (z. B. nach Ortsauswahl). */
        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/** Reagiert auf Änderungen im Kalender (Content-URI-Trigger) und plant sich danach neu ein. */
class CalendarWatcher(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        WidgetRegistry.ids(ctx, WidgetKind.CALENDAR).forEach { WidgetUpdater.update(ctx, it, WidgetKind.CALENDAR) }
        schedule(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE) // nach diesem Lauf wieder lauschen
        return Result.success()
    }

    companion object {
        private const val NAME = "widgetwerk-calendar"
        fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            if (!CalendarRepo.hasPermission(ctx) || WidgetRegistry.ids(ctx, WidgetKind.CALENDAR).isEmpty()) return
            val req = OneTimeWorkRequestBuilder<CalendarWatcher>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
                        .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                        .build()
                ).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME, policy, req)
        }
    }
}
