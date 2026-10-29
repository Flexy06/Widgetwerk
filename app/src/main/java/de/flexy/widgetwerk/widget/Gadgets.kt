package de.flexy.widgetwerk.widget

import android.app.AlarmManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.text.format.DateFormat
import java.util.Date

/** Kleine Helfer für Wecker und Taschenlampe. */
object Gadgets {

    /** Nächster Wecker als Text ("07:00" heute/morgen, sonst "Mo 07:00") oder null. */
    fun nextAlarm(ctx: Context): String? {
        val t = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: return null
        val time = DateFormat.getTimeFormat(ctx).format(Date(t))
        return if (t - System.currentTimeMillis() < 20 * 3600_000L) time
        else DateFormat.format("EEE", t).toString() + " " + time
    }

    // ---------------------------------------------------------------- Taschenlampe

    private const val PREFS = "torch"

    fun torchCamera(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(CameraManager::class.java)
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    fun torchOn(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun setTorchState(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    /** Umschalten; braucht keine Kamera-Berechtigung. */
    fun toggleTorch(ctx: Context): Boolean {
        val id = torchCamera(ctx) ?: return false
        val on = !torchOn(ctx)
        return runCatching {
            ctx.getSystemService(CameraManager::class.java).setTorchMode(id, on)
            setTorchState(ctx, on)
            true
        }.getOrDefault(false)
    }
}
