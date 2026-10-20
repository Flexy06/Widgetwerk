package de.flexy.widgetwerk.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?,
)

/** Nächste Termine aus den Kalendern des Telefons. */
object CalendarRepo {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun upcoming(ctx: Context, days: Int, limit: Int = 12): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 24 * 3600_000L) // ganztägige Termine von heute mitnehmen
            ContentUris.appendId(it, now + days * 24 * 3600_000L)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = mutableListOf<CalEvent>()
        runCatching {
            ctx.contentResolver.query(
                uri, proj,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(2); var end = c.getLong(3)
                    if (allDay) {
                        // Ganztägig: in UTC gespeichert → in lokale Tagesgrenzen umrechnen
                        begin = utcDayToLocal(begin); end = utcDayToLocal(end)
                    }
                    if (end <= now) continue
                    out += CalEvent(c.getLong(0), c.getString(1) ?: "(No title)", begin, end, allDay, c.getInt(5), c.getString(6))
                }
            }
        }
        return out.sortedWith(compareBy({ it.begin }, { !it.allDay })).take(limit)
    }

    private fun utcDayToLocal(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** "Today · 10:00 – 11:30", "Tomorrow · All day", "Fri 24 · 18:00" */
    fun label(ctx: Context, e: CalEvent): String {
        val tf = android.text.format.DateFormat.getTimeFormat(ctx)
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val dayIdx = Math.floorDiv(e.begin - today, 24 * 3600_000L)
        val day = when {
            e.begin <= System.currentTimeMillis() && !e.allDay -> "Now"
            dayIdx <= 0L -> "Today"
            dayIdx == 1L -> "Tomorrow"
            else -> android.text.format.DateFormat.format("EEE d", Date(e.begin)).toString()
        }
        val time = if (e.allDay) "All day" else "${tf.format(Date(e.begin))} – ${tf.format(Date(e.end))}"
        return "$day · $time"
    }
}
