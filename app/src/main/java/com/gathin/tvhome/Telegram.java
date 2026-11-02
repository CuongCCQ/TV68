package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * TV68: parents approve a locked app from anywhere through a Telegram bot of their own.
 *
 * The TV long-polls the Bot API (no server of ours, nothing else is contacted). A parent links a chat by sending
 * "/link CODE" (the code is shown in Launcher settings); only linked chats are obeyed. When a kid asks for a
 * locked app, every linked chat gets a message with 15 min / 30 min / 1 h / 2 h / Deny buttons.
 * The bot token is pushed over adb (TelegramConfig) and never leaves the TV.
 */
final class Telegram {
    private Telegram() {}

    private static final String TAG = "TV68Telegram";
    static final String KEY_TOKEN = "tg_token", KEY_CHATS = "tg_chats", KEY_CODE = "tg_link_code", KEY_OFFSET = "tg_offset";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Long> lastAsk = new HashMap<>();

    static SharedPreferences prefs(Context c) { return Family.prefs(c); }

    static String token(Context c) { return prefs(c).getString(KEY_TOKEN, null); }

    static Set<String> chats(Context c) { return new HashSet<>(prefs(c).getStringSet(KEY_CHATS, new HashSet<>())); }

    static boolean linked(Context c) { return token(c) != null && !chats(c).isEmpty(); }

    /** The code a parent sends as "/link CODE"; made once and kept until relinked. */
    static String linkCode(Context c) {
        String code = prefs(c).getString(KEY_CODE, null);
        if (code == null) {
            code = String.valueOf(100000 + new Random().nextInt(900000));
            prefs(c).edit().putString(KEY_CODE, code).apply();
        }
        return code;
    }

    static void setToken(Context c, String token) {
        prefs(c).edit().putString(KEY_TOKEN, token).remove(KEY_OFFSET).remove(KEY_CHATS).remove(KEY_CODE).apply();
    }

    static void unlinkAll(Context c) { prefs(c).edit().remove(KEY_CHATS).remove(KEY_CODE).apply(); }

    // ---------- Asking ----------

    /** Sends the request for an app to every linked chat. Returns false if nothing is linked or asked too often. */
    static boolean askParents(Context ctx, String pkg, String label) {
        final Context app = ctx.getApplicationContext();
        if (!linked(app)) return false;
        Long last = lastAsk.get(pkg);
        long now = SystemClock.elapsedRealtime();
        if (last != null && now - last < 30_000) return true;
        lastAsk.put(pkg, now);
        new Thread(() -> {
            try {
                JSONArray row1 = new JSONArray(), row2 = new JSONArray();
                for (int m : Family.UNLOCK_MINUTES)
                    row1.put(new JSONObject().put("text", m < 60 ? m + " phút" : (m / 60) + " giờ").put("callback_data", "u|" + m + "|" + pkg));
                row2.put(new JSONObject().put("text", "❌ Từ chối").put("callback_data", "d|0|" + pkg));
                JSONObject markup = new JSONObject().put("inline_keyboard", new JSONArray().put(row1).put(row2));
                for (String chat : chats(app)) {
                    call(app, "sendMessage", new JSONObject().put("chat_id", chat)
                            .put("text", "📺 Bé muốn mở " + label + " trên TV. Cho phép trong bao lâu?")
                            .put("reply_markup", markup));
                }
            } catch (Exception e) { Log.w(TAG, "ask", e); }
        }, "tg-ask").start();
        return true;
    }

    // ---------- Polling ----------

    private static Poller running;

    /**
     * Starts the one poller of this process if it is not running yet. Called by Home and by KidsGuardService:
     * the service keeps the process alive in the background, Home makes sure polling starts even without it.
     */
    static synchronized void ensurePolling(Context ctx) {
        if (running != null && running.isAlive()) return;
        running = new Poller(ctx);
        running.start();
    }

    static final class Poller extends Thread {
        private final Context app;
        private volatile boolean running = true;

        Poller(Context ctx) { super("tg-poll"); app = ctx.getApplicationContext(); setDaemon(true); }

        void quit() { running = false; interrupt(); }

        @Override
        public void run() {
            while (running) {
                try {
                    if (token(app) == null) { sleep(30_000); continue; }
                    long offset = prefs(app).getLong(KEY_OFFSET, 0);
                    JSONObject res = call(app, "getUpdates", new JSONObject().put("offset", offset).put("timeout", 50)
                            .put("allowed_updates", new JSONArray().put("message").put("callback_query")));
                    JSONArray updates = res == null ? null : res.optJSONArray("result");
                    if (updates == null) { sleep(10_000); continue; }
                    for (int i = 0; i < updates.length(); i++) {
                        JSONObject u = updates.getJSONObject(i);
                        prefs(app).edit().putLong(KEY_OFFSET, u.getLong("update_id") + 1).apply();
                        try { handle(app, u); } catch (Exception e) { Log.w(TAG, "handle", e); }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "poll", e);
                    try { sleep(15_000); } catch (InterruptedException ie) { return; }
                }
            }
        }
    }

    private static void handle(Context app, JSONObject u) throws Exception {
        if (u.has("message")) {
            JSONObject msg = u.getJSONObject("message");
            String chat = String.valueOf(msg.getJSONObject("chat").getLong("id"));
            String text = msg.optString("text", "").trim();
            if (text.startsWith("/link")) {
                if (text.endsWith(linkCode(app))) {
                    Set<String> c = chats(app); c.add(chat);
                    prefs(app).edit().putStringSet(KEY_CHATS, c).apply();
                    reply(app, chat, "✅ Đã liên kết với TV. Khi bé xin mở app bị khoá, bạn sẽ nhận tin ở đây.\nLệnh: /lock khoá lại tất cả ngay.");
                } else reply(app, chat, "Mã liên kết không đúng.");
                return;
            }
            if (!chats(app).contains(chat)) return;
            if (text.startsWith("/lock")) {
                MAIN.post(() -> Family.relockAll(app));
                reply(app, chat, "🔒 Đã khoá lại tất cả.");
            } else if (text.startsWith("/status")) {
                reply(app, chat, Family.kidsMode(app) ? "Chế độ trẻ em: bật" : "Chế độ trẻ em: tắt");
            }
            return;
        }
        JSONObject cq = u.optJSONObject("callback_query");
        if (cq == null) return;
        String chat = String.valueOf(cq.getJSONObject("message").getJSONObject("chat").getLong("id"));
        int messageId = cq.getJSONObject("message").getInt("message_id");
        String from = cq.getJSONObject("from").optString("first_name", "");
        call(app, "answerCallbackQuery", new JSONObject().put("callback_query_id", cq.getString("id")));
        if (!chats(app).contains(chat)) return;
        String[] parts = cq.optString("data", "").split("\\|", 3);
        if (parts.length < 3) return;
        String pkg = parts[2];
        String label = label(app, pkg);
        String result;
        if ("u".equals(parts[0])) {
            final int minutes = Integer.parseInt(parts[1]);
            MAIN.post(() -> {
                Family.unlockAppFor(app, pkg, minutes);
                Family.dismissPinDialog();
                open(app, pkg);
            });
            result = "✅ " + from + " đã cho mở " + label + " " + (minutes < 60 ? minutes + " phút" : (minutes / 60) + " giờ") + ".";
        } else {
            MAIN.post(Family::dismissPinDialog);
            result = "❌ " + from + " đã từ chối " + label + ".";
        }
        // The same answer for every linked parent, so nobody taps an old request twice.
        call(app, "editMessageText", new JSONObject().put("chat_id", chat).put("message_id", messageId).put("text", result));
    }

    private static void open(Context app, String pkg) {
        PackageManager pm = app.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        try { app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }

    private static String label(Context app, String pkg) {
        try {
            PackageManager pm = app.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) { return pkg; }
    }

    private static void reply(Context app, String chat, String text) throws Exception {
        call(app, "sendMessage", new JSONObject().put("chat_id", chat).put("text", text));
    }

    private static JSONObject call(Context app, String method, JSONObject body) throws Exception {
        String token = token(app);
        if (token == null) return null;
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.telegram.org/bot" + token + "/" + method).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(65_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes("UTF-8")); }
        int code = c.getResponseCode();
        try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream()) {
            if (in == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally { c.disconnect(); }
    }
}
