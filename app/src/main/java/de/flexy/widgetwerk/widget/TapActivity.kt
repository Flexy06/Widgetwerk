package de.flexy.widgetwerk.widget

import android.app.Activity
import android.app.ActivityOptions
import android.os.Build
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import de.flexy.widgetwerk.WidgetwerkApp
import de.flexy.widgetwerk.data.ConfigStore
import de.flexy.widgetwerk.data.WeatherRepo
import de.flexy.widgetwerk.ui.ConfigureActivity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Unsichtbares Trampolin für Taps auf Widgets.
 * Trick: Der Launcher hängt beim Tippen die Bildschirmposition des Widgets an (sourceBounds).
 * Damit wissen wir, welcher Teil des Hintergrundbilds hinter dem Widget liegt → echter Glas-Blur.
 */
class TapActivity : Activity() {

    companion object {
        const val EXTRA_ID = "widget_id"
        const val EXTRA_ACTION = "tap_action"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(EXTRA_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val action = runCatching { TapAction.valueOf(intent.getStringExtra(EXTRA_ACTION) ?: "") }.getOrDefault(TapAction.ALIGN)
        var rerender = false

        intent.sourceBounds?.let { b ->
            val old = ConfigStore.bounds(this, id)
            val moved = old == null || abs(old.left - b.left) > 2 || abs(old.top - b.top) > 2 ||
                abs(old.width() - b.width()) > 2 || abs(old.height() - b.height()) > 2
            if (moved) {
                ConfigStore.saveBounds(this, id, b)
                rerender = true
                if (old == null && action == TapAction.ALIGN) Toast.makeText(this, "Glass aligned ✨", Toast.LENGTH_SHORT).show()
            }
        }

        runCatching {
            when (action) {
                TapAction.ALIGN -> Unit
                TapAction.ALARMS -> startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                TapAction.CALENDAR -> {
                    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also {
                        ContentUris.appendId(it, System.currentTimeMillis())
                    }.build()
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                TapAction.WEATHER_REFRESH -> {
                    val cfg = ConfigStore.load(this, id)
                    if (cfg?.lat != null && cfg.lon != null) {
                        val app = applicationContext
                        WidgetwerkApp.scope.launch {
                            runCatching { WeatherRepo.fetch(cfg.lat, cfg.lon) }.onSuccess {
                                ConfigStore.saveWeather(app, id, it)
                                WidgetUpdater.request(app, id)
                            }
                        }
                        Toast.makeText(this, "Updating weather…", Toast.LENGTH_SHORT).show()
                    }
                }
                TapAction.PHOTO_NEXT -> {
                    ConfigStore.load(this, id)?.let { c ->
                        ConfigStore.save(this, id, c.copy(photoIndex = (c.photoIndex + 1).mod(maxOf(1, c.photos.size)), photoLastSwitch = System.currentTimeMillis()))
                    }
                    rerender = true
                }
                TapAction.DECO_SHUFFLE -> {
                    ConfigStore.load(this, id)?.let { ConfigStore.save(this, id, it.copy(seed = it.seed + 1)) }
                    rerender = true
                }
                TapAction.BATTERY -> startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                TapAction.MEDIA_OPEN -> {
                    val c = MediaHelper.active(this)
                    val pi = c?.sessionActivity
                    val sent = pi != null && runCatching {
                        // Der Player hat den Intent erstellt; wir erlauben explizit den Start aus dem Widget-Tap
                        val opts = ActivityOptions.makeBasic()
                        if (Build.VERSION.SDK_INT >= 34) {
                            @Suppress("DEPRECATION")
                            opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        }
                        pi.send(this, 0, null, null, null, null, opts.toBundle())
                    }.isSuccess
                    if (!sent) c?.packageName?.let { pkg ->
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                TapAction.CONFIGURE -> startActivity(
                    Intent(this, ConfigureActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        if (rerender) WidgetUpdater.request(this, id)
        finish()
    }
}
