package com.family.tvhome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.family.tvhome.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppItem> = emptyList()
    private var slideJob: Job? = null
    private var weatherJob: Job? = null
    private var photoIndex = 0

    private val vi = Locale("vi", "VN")
    private val timeFmt = SimpleDateFormat("HH:mm", vi)
    private val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", vi)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        Photos.ensureFolder(this)

        adapter = AppAdapter(onClick = { launch(it) }, onLongClick = { appMenu(it) })
        b.grid.layoutManager = GridLayoutManager(this, 6)
        b.grid.adapter = adapter

        b.btnSettings.setOnClickListener {
            PinDialog.verify(this, prefs, "Mật khẩu để vào cài đặt") {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        b.btnKids.setOnClickListener { toggleKids() }

        requestPhotoPermission()
        lifecycleScope.launch { tickClock() }
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
        startSlideshow()
        startWeather()
        b.btnSettings.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        slideJob?.cancel(); weatherJob?.cancel()
    }

    /** Ngăn phím Back thoát khỏi màn hình chính. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { b.btnSettings.requestFocus(); return true }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Apps ----------
    private fun reloadApps() {
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { Apps.load(this@MainActivity) }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val kids = prefs.kidsMode
        val favs = prefs.favorites
        val visible = allApps.filter { a ->
            if (kids) a.pkg in prefs.kidsApps else a.pkg !in prefs.hiddenApps
        }.sortedWith(compareBy({ it.pkg !in favs }, { it.label.lowercase() }))
        adapter.items = visible
        b.kidsBadge.visibility = if (kids) View.VISIBLE else View.GONE
        b.btnKids.text = if (kids) "👶 Thoát chế độ trẻ em" else "👶 Chế độ trẻ em"
        b.btnSettings.visibility = if (kids) View.GONE else View.VISIBLE
        b.empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        b.empty.text = if (kids) "Chưa chọn app nào cho bé.\nVào Cài đặt → Ứng dụng cho bé." else "Không có app nào."
    }

    private fun launch(app: AppItem) {
        try { startActivity(app.launch) } catch (e: Exception) {
            Toast.makeText(this, "Không mở được ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appMenu(app: AppItem) {
        if (prefs.kidsMode) return
        val isFav = app.pkg in prefs.favorites
        val isKid = app.pkg in prefs.kidsApps
        val opts = arrayOf(
            if (isFav) "Bỏ ghim" else "Ghim lên đầu",
            if (isKid) "Bỏ khỏi danh sách cho bé" else "Cho phép bé dùng",
            "Ẩn app này",
            "Thông tin / Gỡ cài đặt"
        )
        AlertDialog.Builder(this).setTitle(app.label).setItems(opts) { _, i ->
            when (i) {
                0 -> prefs.favorites = prefs.favorites.toMutableSet().apply { if (isFav) remove(app.pkg) else add(app.pkg) }
                1 -> prefs.kidsApps = prefs.kidsApps.toMutableSet().apply { if (isKid) remove(app.pkg) else add(app.pkg) }
                2 -> prefs.hiddenApps = prefs.hiddenApps + app.pkg
                3 -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${app.pkg}")))
            }
            applyFilter()
        }.show()
    }

    // ---------- Kids mode ----------
    private fun toggleKids() {
        if (prefs.kidsMode) {
            PinDialog.verify(this, prefs, "Nhập mật khẩu để thoát") { prefs.kidsMode = false; applyFilter() }
        } else {
            if (!prefs.hasPin()) {
                PinDialog.create(this, prefs) { prefs.kidsMode = true; applyFilter() }
            } else { prefs.kidsMode = true; applyFilter() }
        }
    }

    // ---------- Clock ----------
    private suspend fun tickClock() {
        while (true) {
            val now = Date()
            b.clock.text = timeFmt.format(now)
            b.date.text = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    // ---------- Weather ----------
    private fun startWeather() {
        weatherJob?.cancel()
        weatherJob = lifecycleScope.launch {
            while (true) {
                val w = Weather.fetch(prefs.lat, prefs.lon)
                if (w != null) {
                    b.weather.text = "${w.emoji} ${w.tempC}°  ${w.desc}"
                    b.weatherSub.text = "${prefs.cityName} · ${w.loC}° / ${w.hiC}°"
                } else {
                    b.weather.text = "🌡️ --°"
                    b.weatherSub.text = prefs.cityName
                }
                delay(20 * 60 * 1000L)
            }
        }
    }

    // ---------- Photo slideshow ----------
    private fun startSlideshow() {
        slideJob?.cancel()
        slideJob = lifecycleScope.launch {
            while (true) {
                val files = withContext(Dispatchers.IO) { Photos.list() }
                if (files.isEmpty()) {
                    b.bg.setImageDrawable(null)
                    b.hint.visibility = View.VISIBLE
                    delay(10_000); continue
                }
                b.hint.visibility = View.GONE
                photoIndex %= files.size
                val f = files[photoIndex++]
                val bmp = withContext(Dispatchers.IO) {
                    Photos.decode(f, b.rootView.width.coerceAtLeast(1280), b.rootView.height.coerceAtLeast(720))
                }
                if (bmp != null) {
                    b.bg.animate().alpha(0f).setDuration(600).withEndAction {
                        b.bg.setImageBitmap(bmp)
                        b.bg.animate().alpha(1f).setDuration(600).start()
                    }.start()
                }
                delay(prefs.slideSeconds * 1000L)
            }
        }
    }

    // ---------- Permission ----------
    private fun requestPhotoPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
                   else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(perm), 1)
    }
}
