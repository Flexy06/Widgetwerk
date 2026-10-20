package de.flexy.widgetwerk.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Einfacher Speicher: ein JSON-Eintrag pro Widget in SharedPreferences. */
object ConfigStore {
    private const val PREFS = "widgetwerk"
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _changes = MutableStateFlow(0)
    /** Zähler, der bei jeder Änderung hochzählt – die App-UI beobachtet ihn. */
    val changes: StateFlow<Int> = _changes

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notifyChanged() { _changes.value++ }

    // ---------- Widget-Konfiguration ----------

    fun load(ctx: Context, id: Int): WidgetConfig? =
        prefs(ctx).getString("cfg_$id", null)?.let { runCatching { json.decodeFromString<WidgetConfig>(it) }.getOrNull() }

    fun loadOrDefault(ctx: Context, id: Int, kind: WidgetKind): WidgetConfig =
        load(ctx, id)?.takeIf { it.kind == kind } ?: WidgetConfig.defaults(kind).also { save(ctx, id, it) }

    fun save(ctx: Context, id: Int, cfg: WidgetConfig) {
        prefs(ctx).edit().putString("cfg_$id", json.encodeToString(cfg)).apply()
        notifyChanged()
    }

    fun delete(ctx: Context, id: Int) {
        prefs(ctx).edit()
            .remove("cfg_$id").remove("bounds_$id").remove("wx_$id")
            .apply()
        notifyChanged()
    }

    // ---------- Position auf dem Bildschirm (für den Glas-Blur) ----------

    /** Bildschirm-Rechteck des Widgets, wie es der Launcher beim letzten Tippen gemeldet hat. */
    fun bounds(ctx: Context, id: Int): Rect? =
        prefs(ctx).getString("bounds_$id", null)?.let { Rect.unflattenFromString(it) }

    fun saveBounds(ctx: Context, id: Int, r: Rect) {
        prefs(ctx).edit().putString("bounds_$id", r.flattenToString()).apply()
        notifyChanged()
    }

    fun clearBounds(ctx: Context, id: Int) {
        prefs(ctx).edit().remove("bounds_$id").apply()
        notifyChanged()
    }

    // ---------- Wetter-Cache ----------

    fun weather(ctx: Context, id: Int): WeatherData? =
        prefs(ctx).getString("wx_$id", null)?.let { runCatching { json.decodeFromString<WeatherData>(it) }.getOrNull() }

    fun saveWeather(ctx: Context, id: Int, w: WeatherData) {
        prefs(ctx).edit().putString("wx_$id", json.encodeToString(w)).apply()
        notifyChanged()
    }

    // ---------- Globale Einstellungen ----------

    fun global(ctx: Context): GlobalSettings =
        prefs(ctx).getString("global", null)?.let { runCatching { json.decodeFromString<GlobalSettings>(it) }.getOrNull() }
            ?: GlobalSettings()

    fun saveGlobal(ctx: Context, g: GlobalSettings) {
        prefs(ctx).edit().putString("global", json.encodeToString(g)).apply()
        notifyChanged()
    }

    inline fun updateGlobal(ctx: Context, f: (GlobalSettings) -> GlobalSettings) = saveGlobal(ctx, f(global(ctx)))
}
