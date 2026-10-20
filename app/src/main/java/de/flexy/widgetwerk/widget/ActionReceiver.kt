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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = MediaHelper.active(context)
        val t = c?.transportControls
        when (intent.action) {
            PLAY_PAUSE -> if (MediaHelper.isPlaying(c)) t?.pause() else t?.play()
            NEXT -> t?.skipToNext()
            PREV -> t?.skipToPrevious()
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
