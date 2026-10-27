# TV68 — a family launcher for Android TV

A clean, ad-free home screen for Android TV / Google TV, built on the launcher from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT, see `LICENSE-cleanyourtv`) with a few
family-oriented additions:

- 🖼️ **Family photos as the wallpaper** — drop pictures into a `TVHome` folder (on a USB stick, or
  `Pictures/TVHome` on the TV) and they rotate; the interval is chosen in Launcher settings
- 🌤️ **Weather** under the clock, from [open-meteo.com](https://open-meteo.com) (free, no API key); pick the city in Launcher settings
- 👶 **Kids mode** locked by a PIN — only the apps you allow are shown, and every way into settings asks for the PIN first
- 🚫 **No ads, no tracking.** The only network calls are the weather requests. The PIN is stored as a salted SHA-256 hash

Everything else — the black Apple-TV-style home, the dock, the app grid, themes — is CleanYourTV as shipped.

## Install

1. On the TV, enable *Developer options* and *USB/Network debugging*, note its IP address.
2. On a computer on the same Wi-Fi (with [adb](https://developer.android.com/tools/releases/platform-tools) installed):

```bash
TV=192.168.1.4   # your TV's IP
curl -L -o TVHome.apk https://github.com/CuongCCQ/TV68/raw/main/release/TVHome.apk
adb connect $TV:5555
adb -s $TV:5555 install -r TVHome.apk
adb -s $TV:5555 shell am start -n com.gathin.tvhome/.MainActivity
```

3. Make it the home screen. On sets that never ask which launcher to use (Sony Bravia, for one), disable
   the stock launcher — it is only disabled, never removed:

```bash
# find the stock launcher's package
adb -s $TV:5555 shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
# Android TV:  com.google.android.tvlauncher    Google TV:  com.google.android.apps.tv.launcherx
adb -s $TV:5555 shell pm disable-user --user 0 com.google.android.tvlauncher
```

To go back: `adb shell pm enable com.google.android.tvlauncher`.

Without a computer, the **Downloader** app on the TV also works: enter code **9453086**, install, press Home.

## Family photos

Copy JPG/PNG/WebP files into a folder named `TVHome` at the root of a USB stick, or push them over adb:

```bash
adb -s $TV:5555 shell mkdir -p /sdcard/Pictures/TVHome
adb -s $TV:5555 push "/path/to/photos/." /sdcard/Pictures/TVHome/
```

## Kids mode

Open Launcher settings (the gear pill) → *Apps for kids* → tick the allowed apps → *Kids mode: On* → set a
4–8 digit PIN. The gear turns into 👶 and asks for the PIN before opening settings; turn kids mode off there.

## Build

Every push to `main` builds the APK with GitHub Actions and commits it to `release/TVHome.apk`, so the
link above is always the latest build. Locally: open the folder in Android Studio, or `./gradlew assembleRelease`.

## What changed from CleanYourTV

`Family.java` and `Weather.java` are new; small hooks in `Config.java`, `MainActivity.java`,
`LauncherSettingsActivity.java`, the two layouts, `strings.xml` and the manifest (INTERNET and photo-read
permissions). Everything else is upstream.

---

# TV68 — Launcher Android TV cho gia đình (tiếng Việt)

Dựa trên launcher của CleanYourTV, thêm: ảnh gia đình làm nền (thư mục `TVHome` trên USB hoặc
`Pictures/TVHome`), thời tiết dưới đồng hồ, chế độ trẻ em khoá bằng PIN. Không quảng cáo, không theo dõi.

Cài: trên TV mở app **Downloader**, nhập mã **9453086** → Install → bấm Home → chọn tvhome. Sony không
hỏi chọn launcher thì tắt launcher gốc bằng adb như hướng dẫn tiếng Anh ở trên.
