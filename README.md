# TVHome — Launcher Android TV cho gia đình

Màn hình chính tối giản cho Android TV / Google TV:

- 🖼️ **Ảnh gia đình** làm nền, tự chuyển (slideshow)
- 🕒 **Đồng hồ** lớn + ngày tháng tiếng Việt
- 🌤️ **Thời tiết** (open-meteo.com — miễn phí, không cần API key)
- 📱 **Lưới app** gọn: ghim app hay dùng, ẩn app không dùng
- 👶 **Chế độ trẻ em** khoá bằng mật khẩu: chỉ hiện app bạn cho phép, không vào được Cài đặt
- 🚫 **Không quảng cáo, không tracker, không thu thập dữ liệu.** Quyền duy nhất: Internet (thời tiết) và đọc ảnh.

Toàn bộ mã nguồn trong repo này — ~600 dòng Kotlin, tự kiểm tra được.

## Cài đặt

### Cách 1: Tải APK từ GitHub Actions
Mỗi lần push lên `main`, workflow **Build APK** tự build. Vào tab *Actions* → chọn lần chạy mới nhất → tải `TVHome-apk`.

### Cách 2: Build bằng Android Studio
Mở thư mục này bằng Android Studio (Hedgehog trở lên) → *Build → Build APK(s)*.

### Cài lên TV
1. Chép `app-release.apk` vào USB, cắm vào TV, dùng app quản lý file để cài (hoặc dùng *Send Files to TV* từ điện thoại).
2. Cho phép "Cài ứng dụng không rõ nguồn gốc" khi được hỏi.
3. Bấm nút **Home** trên remote → chọn **TVHome** → *Luôn luôn*.
   - Một số TV (Xiaomi, TCL) không hỏi: vào *Cài đặt → Ứng dụng → TVHome → Cài đặt → Màn hình chính*, hoặc cài thêm app *Launcher Manager*.

## Thêm ảnh gia đình
Chép ảnh (jpg/png/webp) vào thư mục tên **`TVHome`**:
- bộ nhớ TV: `Pictures/TVHome`, hoặc
- thư mục `TVHome` ở gốc USB rồi cắm vào TV.

## Chế độ trẻ em
1. Trên màn hình chính, giữ nút OK trên một app → *Cho phép bé dùng* (hoặc vào Cài đặt → Ứng dụng cho bé).
2. Bấm **👶 Chế độ trẻ em** → đặt PIN 4–8 số.
3. Muốn thoát phải nhập PIN. PIN chỉ lưu dạng băm SHA-256, không lưu PIN thật.

## Yêu cầu
Android TV 6.0 trở lên.
