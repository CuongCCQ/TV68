package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * TV68 addition: current weather from open-meteo.com (free, no key, nothing personal sent beyond the
 * chosen place's coordinates). Refreshed every 20 minutes while Home is showing.
 */
final class Weather {
    private Weather() {}

    static final String KEY_NAME = "weather_name", KEY_LAT = "weather_lat", KEY_LON = "weather_lon";
    private static final long REFRESH_MS = 20 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Place {
        final String name; final float lat, lon;
        Place(String name, float lat, float lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }

    static Place place(Context ctx) {
        SharedPreferences p = Family.prefs(ctx);
        return new Place(p.getString(KEY_NAME, "Hà Nội"), p.getFloat(KEY_LAT, 21.0285f), p.getFloat(KEY_LON, 105.8542f));
    }

    static void setPlace(Context ctx, Place pl) {
        Family.prefs(ctx).edit().putString(KEY_NAME, pl.name).putFloat(KEY_LAT, pl.lat).putFloat(KEY_LON, pl.lon).apply();
    }

    /** Keeps the view current; stop() cancels the next refresh. */
    static final class Ticker {
        private final TextView view;
        private boolean running;
        private final Runnable tick = this::refresh;

        Ticker(TextView view) { this.view = view; }

        void start() { running = true; refresh(); }

        void stop() { running = false; MAIN.removeCallbacks(tick); }

        private void refresh() {
            MAIN.removeCallbacks(tick);
            final Place pl = place(view.getContext());
            new Thread(() -> {
                final String text = fetch(pl);
                MAIN.post(() -> {
                    if (!running) return;
                    if (text != null) view.setText(text);
                    MAIN.postDelayed(tick, REFRESH_MS);
                });
            }, "weather").start();
        }
    }

    private static String fetch(Place pl) {
        try {
            JSONObject j = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + pl.lat
                    + "&longitude=" + pl.lon + "&current=temperature_2m,weather_code"
                    + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"));
            JSONObject cur = j.getJSONObject("current");
            JSONObject daily = j.getJSONObject("daily");
            String[] d = describe(cur.getInt("weather_code"));
            return d[1] + " " + Math.round(cur.getDouble("temperature_2m")) + "°  " + d[0] + "  ·  " + pl.name + "  "
                    + Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)) + "°/"
                    + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)) + "°";
        } catch (Exception e) {
            return null;
        }
    }

    /** Place search by name (open-meteo geocoding). Call off the main thread. */
    static List<Place> search(String city) {
        List<Place> out = new ArrayList<>();
        try {
            JSONObject j = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=vi&name="
                    + URLEncoder.encode(city, "UTF-8")));
            JSONArray arr = j.optJSONArray("results");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                StringBuilder name = new StringBuilder(o.optString("name"));
                if (o.has("admin1")) name.append(", ").append(o.optString("admin1"));
                if (o.has("country")) name.append(", ").append(o.optString("country"));
                out.add(new Place(name.toString(), (float) o.getDouble("latitude"), (float) o.getDouble("longitude")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192]; int n; StringBuilder sb = new StringBuilder();
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private static String[] describe(int code) {
        switch (code) {
            case 0: return new String[]{"Trời quang", "☀️"};
            case 1: return new String[]{"Ít mây", "🌤️"};
            case 2: return new String[]{"Có mây", "⛅"};
            case 3: return new String[]{"Nhiều mây", "☁️"};
            case 45: case 48: return new String[]{"Sương mù", "🌫️"};
            case 51: case 53: case 55: case 56: case 57: return new String[]{"Mưa phùn", "🌦️"};
            case 61: case 63: case 65: case 66: case 67: return new String[]{"Mưa", "🌧️"};
            case 71: case 73: case 75: case 77: return new String[]{"Tuyết", "🌨️"};
            case 80: case 81: case 82: return new String[]{"Mưa rào", "🌧️"};
            case 95: case 96: case 99: return new String[]{"Dông", "⛈️"};
            default: return new String[]{"", "🌡️"};
        }
    }
}
