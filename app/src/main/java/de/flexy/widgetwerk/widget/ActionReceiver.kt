package de.flexy.widgetwerk.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.flexy.widgetwerk.WidgetwerkApp
import de.flexy.widgetwerk.data.WidgetKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Buttons innerhalb von Widgets (Mediensteuerung). */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val PLAY_PAUSE = "de.flexy.widgetwerk.PLAY_PAUSE"
        const val NEXT = "de.flexy.widgetwerk.NEXT"
        const val PREV = "de.flexy.widgetwerk.PREV"
        const val SEEK = "de.flexy.widgetwerk.SEEK"
        const val CUSTOM = "de.flexy.widgetwerk.CUSTOM"
        const val FLASH = "de.flexy.widgetwerk.FLASH"
        const val EXTRA_FRACTION = "fraction"
        const val EXTRA_CUSTOM = "custom_action"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = MediaHelper.active(context)
        val t = c?.transportControls
        when (intent.action) {
            PLAY_PAUSE -> if (MediaHelper.isPlaying(c)) t?.pause() else t?.play()
            NEXT -> t?.skipToNext()
            PREV -> t?.skipToPrevious()
            SEEK -> {
                val d = MediaHelper.duration(c)
                if (d > 0) t?.seekTo((d * intent.getFloatExtra(EXTRA_FRACTION, 0f)).toLong())
            }
            CUSTOM -> intent.getStringExtra(EXTRA_CUSTOM)?.let { a -> t?.sendCustomAction(a, null) }
        }
        // Falls der Live-Dienst nicht läuft: kurz warten und selbst aktualisieren
        val pending = goAsync()
        val app = context.applicationContext
        WidgetwerkApp.scope.launch {
            try {
                delay(600)
                WidgetRegistry.ids(app, WidgetKind.MEDIA).forEach { WidgetUpdater.update(app, it, WidgetKind.MEDIA) }
            } finally {
                pending.finish()
            }
        }
    }
}
