package com.family.tvhome

import android.app.Application
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

/** Bắt mọi lỗi chưa xử lý và hiện lên màn hình để dễ chẩn đoán (không gửi đi đâu). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val i = Intent(this, CrashActivity::class.java)
                    .putExtra("trace", sw.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(i)
            } catch (_: Exception) { }
            default?.uncaughtException(t, e)
        }
    }
}
