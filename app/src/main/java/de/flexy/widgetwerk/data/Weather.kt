package de.flexy.widgetwerk.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

@Serializable
data class HourPoint(val epoch: Long, val temp: Double, val code: Int, val isDay: Boolean)

@Serializable
data class WeatherData(
    val fetchedAt: Long,
    val temp: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val humidity: Int,
    val wind: Double,
    val hours: List<HourPoint>,
    val sunrise: Long = 0,     // Unix-Sekunden (heute)
    val sunset: Long = 0,
    val sunriseTomorrow: Long = 0,
)

@Serializable
data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Wetter über Open-Meteo – kostenlos, ohne API-Key. */
object WeatherRepo {

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Widgetwerk/1.0 (Android)")
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun fetch(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
            "&timezone=auto&timeformat=unixtime&forecast_days=2"
        val root = ConfigStore.json.parseToJsonElement(get(url)).jsonObject
        val cur = root["current"]!!.jsonObject
        val daily = root["daily"]!!.jsonObject
        val hourly = root["hourly"]!!.jsonObject
        val times = hourly["time"]!!.jsonArray
        val temps = hourly["temperature_2m"]!!.jsonArray
        val codes = hourly["weather_code"]!!.jsonArray
        val days = hourly["is_day"]!!.jsonArray
        val now = System.currentTimeMillis() / 1000
        val hours = times.indices
            .filter { times[it].jsonPrimitive.content.toLong() > now - 1800 }
            .take(12)
            .map { i ->
                HourPoint(
                    epoch = times[i].jsonPrimitive.content.toLong(),
                    temp = temps[i].jsonPrimitive.double,
                    code = codes[i].jsonPrimitive.int,
                    isDay = days[i].jsonPrimitive.int == 1,
                )
            }
        WeatherData(
            fetchedAt = System.currentTimeMillis(),
            temp = cur.num("temperature_2m"),
            feelsLike = cur.num("apparent_temperature"),
            code = cur["weather_code"]!!.jsonPrimitive.int,
            isDay = cur["is_day"]!!.jsonPrimitive.int == 1,
            high = (daily["temperature_2m_max"] as JsonArray)[0].jsonPrimitive.double,
            low = (daily["temperature_2m_min"] as JsonArray)[0].jsonPrimitive.double,
            humidity = cur.num("relative_humidity_2m").toInt(),
            wind = cur.num("wind_speed_10m"),
            hours = hours,
            sunrise = (daily["sunrise"] as JsonArray)[0].jsonPrimitive.content.toLong(),
            sunset = (daily["sunset"] as JsonArray)[0].jsonPrimitive.content.toLong(),
            sunriseTomorrow = (daily["sunrise"] as JsonArray).getOrNull(1)?.jsonPrimitive?.content?.toLong() ?: 0,
        )
    }

    private fun JsonObject.num(k: String) = this[k]!!.jsonPrimitive.double

    /** Ortssuche (Open-Meteo Geocoding). */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language
        val root = ConfigStore.json.parseToJsonElement(
            get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=$lang&format=json")
        ).jsonObject
        val results = root["results"]?.jsonArray ?: return@withContext emptyList()
        results.map { it.jsonObject }.map { o ->
            Place(
                name = o["name"]!!.jsonPrimitive.content,
                detail = listOfNotNull(o["admin1"]?.jsonPrimitive?.content, o["country"]?.jsonPrimitive?.content).joinToString(", "),
                lat = o["latitude"]!!.jsonPrimitive.double,
                lon = o["longitude"]!!.jsonPrimitive.double,
            )
        }
    }

    /** Aktuelle Position (nur im Vordergrund aufgerufen, Berechtigung muss bereits erteilt sein). */
    @SuppressLint("MissingPermission")
    suspend fun currentPlace(ctx: Context): Place? = withContext(Dispatchers.IO) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val loc: Location? = withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
                else LocationManager.GPS_PROVIDER
                lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { cont.resume(it) }
            }
        } ?: runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }.getOrNull()
        loc ?: return@withContext null
        val name = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: "Current location"
        Place(name, "", loc.latitude, loc.longitude)
    }

    /** WMO-Wettercode → Kurztext. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunder & hail"
        else -> "—"
    }
}
