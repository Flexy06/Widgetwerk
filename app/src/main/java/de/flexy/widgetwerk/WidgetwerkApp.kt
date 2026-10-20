package de.flexy.widgetwerk

import android.app.Application
import de.flexy.widgetwerk.widget.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WidgetwerkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }

    companion object {
        /** Prozessweiter Scope für Widget-Rendering im Hintergrund. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
