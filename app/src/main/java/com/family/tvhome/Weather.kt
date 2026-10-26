package com.family.tvhome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Thời tiết từ open-meteo.com — miễn phí, không cần API key, không gửi dữ liệu cá nhân. */
object Weather {
    data class Now(val tempC: Int, val desc: String, val emoji: String, val hiC: Int, val loC: Int)
    data class Place(val name: String, val lat: Float, val lon: Float)

    suspend fun fetch(lat: Float, lon: Float): Now? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            val j = JSONObject(get(url))
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            val (desc, emoji) = describe(cur.getInt("weather_code"))
            Now(
                cur.getDouble("temperature_2m").toInt(), desc, emoji,
                daily.getJSONArray("temperature_2m_max").getDouble(0).toInt(),
                daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()
            )
        } catch (e: Exception) { null }
    }

    /** Tìm toạ độ theo tên thành phố (geocoding của open-meteo). */
    suspend fun search(city: String): List<Place> = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(city, "UTF-8")
            val j = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=5&language=vi"))
            val arr = j.optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val name = listOfNotNull(o.optString("name"), o.optString("admin1", null), o.optString("country", null))
                    .filter { s -> s.isNotBlank() }.joinToString(", ")
                Place(name, o.getDouble("latitude").toFloat(), o.getDouble("longitude").toFloat())
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "Trời quang" to "☀️"
        1 -> "Ít mây" to "🌤️"
        2 -> "Có mây" to "⛅"
        3 -> "Nhiều mây" to "☁️"
        45, 48 -> "Sương mù" to "🌫️"
        51, 53, 55, 56, 57 -> "Mưa phùn" to "🌦️"
        61, 63, 65, 66, 67 -> "Mưa" to "🌧️"
        71, 73, 75, 77 -> "Tuyết" to "🌨️"
        80, 81, 82 -> "Mưa rào" to "🌧️"
        95, 96, 99 -> "Dông" to "⛈️"
        else -> "—" to "🌡️"
    }
}
