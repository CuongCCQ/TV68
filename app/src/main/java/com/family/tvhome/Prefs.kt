package com.family.tvhome

import android.content.Context
import java.security.MessageDigest

/** Lưu cài đặt. Mã PIN chỉ lưu dạng băm SHA-256 + salt, không lưu PIN thật. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

    var kidsMode: Boolean
        get() = sp.getBoolean("kids_mode", false)
        set(v) = sp.edit().putBoolean("kids_mode", v).apply()

    var hiddenApps: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** Các app bé được dùng khi bật chế độ trẻ em */
    var kidsApps: Set<String>
        get() = sp.getStringSet("kids_apps", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("kids_apps", v).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favs", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("favs", v).apply()

    var cityName: String
        get() = sp.getString("city", "Hà Nội") ?: "Hà Nội"
        set(v) = sp.edit().putString("city", v).apply()
    var lat: Float
        get() = sp.getFloat("lat", 21.0285f)
        set(v) = sp.edit().putFloat("lat", v).apply()
    var lon: Float
        get() = sp.getFloat("lon", 105.8542f)
        set(v) = sp.edit().putFloat("lon", v).apply()

    /** Giây giữa hai ảnh nền */
    var slideSeconds: Int
        get() = sp.getInt("slide_sec", 30)
        set(v) = sp.edit().putInt("slide_sec", v).apply()

    fun hasPin() = sp.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        sp.edit().putString("pin_salt", salt).putString("pin_hash", hash(pin, salt)).apply()
    }

    fun clearPin() = sp.edit().remove("pin_hash").remove("pin_salt").apply()

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pin_salt", null) ?: return false
        return sp.getString("pin_hash", null) == hash(pin, salt)
    }

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }
}
