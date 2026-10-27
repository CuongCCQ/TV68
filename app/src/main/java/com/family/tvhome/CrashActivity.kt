package com.family.tvhome

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            setBackgroundColor(Color.BLACK); setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(48, 48, 48, 48)
            text = "TVHome gặp lỗi. Chụp màn hình này gửi để sửa:\n\n" +
                    (intent.getStringExtra("trace") ?: "").take(3000)
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }
}
