package com.family.tvhome

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.family.tvhome.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        refresh()

        b.btnPin.setOnClickListener {
            if (prefs.hasPin()) {
                PinDialog.verify(this, prefs, "Nhập mật khẩu cũ") {
                    AlertDialog.Builder(this).setTitle("Mật khẩu trẻ em")
                        .setItems(arrayOf("Đổi mật khẩu", "Xoá mật khẩu")) { _, i ->
                            if (i == 0) PinDialog.create(this, prefs) { refresh() }
                            else { prefs.clearPin(); prefs.kidsMode = false; refresh() }
                        }.show()
                }
            } else PinDialog.create(this, prefs) { refresh() }
        }

        b.btnKidsApps.setOnClickListener { pickApps("Ứng dụng cho bé", prefs.kidsApps) { prefs.kidsApps = it } }
        b.btnHidden.setOnClickListener { pickApps("App bị ẩn (bỏ tick để hiện lại)", prefs.hiddenApps) { prefs.hiddenApps = it } }

        b.btnCity.setOnClickListener {
            val input = EditText(this).apply { hint = "Tên thành phố, ví dụ: Hà Nội"; setText(prefs.cityName) }
            AlertDialog.Builder(this).setTitle("Vị trí thời tiết").setView(input)
                .setPositiveButton("Tìm") { _, _ -> searchCity(input.text.toString()) }
                .setNegativeButton("Huỷ", null).show()
        }

        b.btnSlide.setOnClickListener {
            val choices = arrayOf(10, 30, 60, 120, 300)
            AlertDialog.Builder(this).setTitle("Đổi ảnh nền mỗi")
                .setItems(choices.map { if (it < 60) "$it giây" else "${it / 60} phút" }.toTypedArray()) { _, i ->
                    prefs.slideSeconds = choices[i]; refresh()
                }.show()
        }

        b.btnPhotoHelp.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Cách thêm ảnh gia đình")
                .setMessage("Chép ảnh (jpg/png) vào thư mục tên TVHome:\n\n" +
                        "• Trong bộ nhớ TV: Pictures/TVHome\n" +
                        "• Hoặc tạo thư mục TVHome ở gốc USB rồi cắm vào TV\n\n" +
                        "Có thể dùng app quản lý file (X-plore, Send Files to TV) để chép từ điện thoại sang TV.\n\n" +
                        "Thư mục đang quét:\n" + Photos.folders().joinToString("\n") { it.path })
                .setPositiveButton("OK", null).show()
        }

        b.btnSystem.setOnClickListener { Apps.openSystemSettings(this) }
        b.btnDefaultHome.setOnClickListener {
            try { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
            catch (e: Exception) {
                Toast.makeText(this, "TV này không có mục chọn Home. Bấm nút Home và chọn TVHome → Luôn luôn.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refresh() {
        b.btnPin.text = if (prefs.hasPin()) "🔒 Mật khẩu trẻ em: đã đặt (đổi / xoá)" else "🔒 Đặt mật khẩu trẻ em"
        b.btnCity.text = "📍 Thời tiết: ${prefs.cityName}"
        val s = prefs.slideSeconds
        b.btnSlide.text = "🖼️ Đổi ảnh nền mỗi: " + if (s < 60) "$s giây" else "${s / 60} phút"
    }

    private fun pickApps(title: String, current: Set<String>, save: (Set<String>) -> Unit) {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { Apps.load(this@SettingsActivity) }
            val checked = BooleanArray(apps.size) { apps[it].pkg in current }
            AlertDialog.Builder(this@SettingsActivity).setTitle(title)
                .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
                .setPositiveButton("Lưu") { _, _ ->
                    save(apps.indices.filter { checked[it] }.map { apps[it].pkg }.toSet())
                }.setNegativeButton("Huỷ", null).show()
        }
    }

    private fun searchCity(q: String) {
        if (q.isBlank()) return
        lifecycleScope.launch {
            val places = Weather.search(q)
            if (places.isEmpty()) { Toast.makeText(this@SettingsActivity, "Không tìm thấy", Toast.LENGTH_SHORT).show(); return@launch }
            AlertDialog.Builder(this@SettingsActivity).setTitle("Chọn địa điểm")
                .setItems(places.map { it.name }.toTypedArray()) { _, i ->
                    val p = places[i]
                    prefs.cityName = p.name.substringBefore(","); prefs.lat = p.lat; prefs.lon = p.lon
                    refresh()
                }.show()
        }
    }
}
