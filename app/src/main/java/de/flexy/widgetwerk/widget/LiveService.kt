package de.flexy.widgetwerk.widget

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import de.flexy.widgetwerk.WidgetwerkApp
import de.flexy.widgetwerk.data.WidgetKind
import de.flexy.widgetwerk.glass.WallpaperSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optionaler "Live-Dienst" (Benachrichtigungszugriff). Läuft dauerhaft, weil das System ihn gebunden hält:
 * – Medien-Widget sofort aktualisieren
 * – Akku-Widget bei jeder Prozentänderung
 * – alle Widgets bei Wallpaper-/Farb-/Dark-Mode-Wechsel neu rendern
 * Benachrichtigungen selbst werden nicht gelesen.
 */
class LiveService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var lastBattery = Pair(-1, -1)
    private var uiMode = 0
    private val jobs = mutableMapOf<String, Job>()

    /** Mehrfache Ereignisse zusammenfassen. */
    private fun debounce(key: String, ms: Long, block: suspend () -> Unit) {
        jobs[key]?.cancel()
        jobs[key] = WidgetwerkApp.scope.launch { delay(ms); block() }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list ?: emptyList())
        mediaChanged()
    }

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (Pair(level, status) == lastBattery) return
            lastBattery = Pair(level, status)
            debounce("battery", 800) { WidgetRegistry.ids(applicationContext, WidgetKind.BATTERY).forEach { WidgetUpdater.update(applicationContext, it, WidgetKind.BATTERY) } }
        }
    }

    private val colors = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) themeChanged(wallpaper = true)
    }

    override fun onListenerConnected() {
        uiMode = resources.configuration.uiMode
        sessions = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, LiveService::class.java)
        runCatching {
            sessions?.addOnActiveSessionsChangedListener(sessionListener, me, main)
            attach(sessions?.getActiveSessions(me) ?: emptyList())
        }
        registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        runCatching { WallpaperManager.getInstance(this).addOnColorsChangedListener(colors, main) }
        mediaChanged()
    }

    override fun onListenerDisconnected() = cleanup()

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionListener) }
        callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        callbacks.clear()
        runCatching { unregisterReceiver(battery) }
        runCatching { WallpaperManager.getInstance(this).removeOnColorsChangedListener(colors) }
    }

    private fun attach(list: List<MediaController>) {
        callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        callbacks.clear()
        list.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = mediaChanged()
                override fun onMetadataChanged(metadata: MediaMetadata?) = mediaChanged()
                override fun onSessionDestroyed() = mediaChanged()
            }
            c.registerCallback(cb, main)
            callbacks[c] = cb
        }
    }

    private fun mediaChanged() = debounce("media", 350) {
        WidgetRegistry.ids(applicationContext, WidgetKind.MEDIA).forEach { WidgetUpdater.update(applicationContext, it, WidgetKind.MEDIA) }
    }

    private fun themeChanged(wallpaper: Boolean) = debounce("theme", 1500) {
        if (wallpaper) WallpaperSource.refreshSystemIfChanged(applicationContext)
        WidgetUpdater.updateAll(applicationContext)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode
            themeChanged(wallpaper = false)
        }
    }
}
