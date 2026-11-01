package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * TV68 additions: family photos as the wallpaper, a kids mode locked by a PIN, and the weather place.
 * Everything is stored in the launcher's own SharedPreferences; the PIN only as a salted SHA-256 hash.
 */
final class Family {
    private Family() {}

    static final String KIDS_MODE = "kids_mode";
    static final String KIDS_APPS = "kids_apps";
    static final String PHOTO_MINUTES = "photo_minutes";
    private static final String PIN_HASH = "pin_hash";
    private static final String PIN_SALT = "pin_salt";

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    // ---------- Photos ----------

    /** Folders scanned for family photos: Pictures/TVHome on the TV, and TVHome at the root of any USB drive. */
    static List<File> photoFolders() {
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TVHome"));
        File[] volumes = new File("/storage").listFiles();
        if (volumes != null) for (File v : volumes) {
            if (!"emulated".equals(v.getName()) && !"self".equals(v.getName())) dirs.add(new File(v, "TVHome"));
        }
        return dirs;
    }

    static List<File> photos() {
        List<File> out = new ArrayList<>();
        for (File dir : photoFolders()) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && f.length() > 0 && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")))
                    out.add(f);
            }
        }
        Collections.sort(out);
        return out;
    }

    // ---------- Kids mode ----------

    static boolean kidsMode(Context ctx) { return prefs(ctx).getBoolean(KIDS_MODE, false); }

    static void setKidsMode(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KIDS_MODE, on).remove(UNLOCK_UNTIL).apply();
    }

    // ---------- Per-app timed unlock (kids mode) ----------

    private static final String APP_UNLOCK = "app_unlock_";
    private static final java.util.Map<String, Runnable> APP_RELOCKS = new java.util.HashMap<>();

    /** In kids mode: may this app open without the PIN right now? */
    static boolean appAllowed(Context ctx, String pkg) {
        return !kidsLocked(ctx) || kidsApps(ctx).contains(pkg)
                || System.currentTimeMillis() < prefs(ctx).getLong(APP_UNLOCK + pkg, 0);
    }

    static long appUnlockUntil(Context ctx, String pkg) { return prefs(ctx).getLong(APP_UNLOCK + pkg, 0); }

    /** Opens one app for some minutes; when they run out Home comes back to the front. */
    static void unlockAppFor(Context ctx, String pkg, int minutes) {
        final Context app = ctx.getApplicationContext();
        final long until = System.currentTimeMillis() + minutes * 60_000L;
        prefs(app).edit().putLong(APP_UNLOCK + pkg, until).apply();
        Runnable old = APP_RELOCKS.remove(pkg);
        if (old != null) MAIN.removeCallbacks(old);
        Runnable relock = () -> {
            APP_RELOCKS.remove(pkg);
            if (appAllowed(app, pkg)) return;
            try {
                app.startActivity(new android.content.Intent(app, MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        };
        APP_RELOCKS.put(pkg, relock);
        MAIN.postDelayed(relock, minutes * 60_000L + 500);
    }

    static final int[] UNLOCK_MINUTES = {15, 30, 60, 120};
    /** Passed to the callback of pinAndDuration when "Settings" was chosen instead of a time. */
    static final int CHOSE_SETTINGS = -1;

    /**
     * One dialog: the PIN field and, under it, one button per time limit (plus Settings when asked).
     * The callback gets the minutes, or CHOSE_SETTINGS, only when the PIN is right.
     */
    static void pinAndDuration(Activity a, String title, boolean withSettings, java.util.function.IntConsumer onChoice) {
        float dp = a.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding((int) (24 * dp), (int) (8 * dp), (int) (24 * dp), (int) (8 * dp));
        EditText pin = pinField(a, a.getString(R.string.pin_hint));
        box.addView(pin);
        android.widget.TextView hint = new android.widget.TextView(a);
        hint.setText(R.string.choose_time_hint);
        hint.setPadding(0, (int) (12 * dp), 0, (int) (4 * dp));
        box.addView(hint);
        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        box.addView(row);
        final AlertDialog dialog = new AlertDialog.Builder(a).setTitle(title).setView(box)
                .setNegativeButton(android.R.string.cancel, null).create();
        java.util.List<Integer> choices = new java.util.ArrayList<>();
        for (int m : UNLOCK_MINUTES) choices.add(m);
        if (withSettings) choices.add(CHOSE_SETTINGS);
        for (int m : choices) {
            android.widget.Button b = new android.widget.Button(a);
            b.setAllCaps(false);
            b.setText(m == CHOSE_SETTINGS ? a.getString(R.string.tv68_settings_short)
                    : m < 60 ? a.getString(R.string.unlock_minutes, m) : a.getString(R.string.unlock_hours, m / 60));
            b.setOnClickListener(v -> {
                if (!checkPin(a, pin.getText().toString())) {
                    Toast.makeText(a, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
                    pin.setText("");
                    pin.requestFocus();
                    return;
                }
                dialog.dismiss();
                onChoice.accept(m);
            });
            row.addView(b, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // After typing the PIN, the keyboard's Done/Enter moves straight to the first time button.
        pin.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        pin.setOnEditorActionListener((v, actionId, ev) -> { row.getChildAt(0).requestFocus(); return true; });
        dialog.show();
        pin.requestFocus();
    }

    /** PIN and how long in one dialog; runs onOpen once the app is unlocked. */
    static void askToOpenApp(Activity a, String pkg, String label, Runnable onOpen) {
        if (!hasPin(a)) { onOpen.run(); return; }
        pinAndDuration(a, a.getString(R.string.app_locked_title, label), false, minutes -> {
            unlockAppFor(a, pkg, minutes);
            onOpen.run();
        });
    }

    // ---------- Timed unlock ----------

    static final String UNLOCK_UNTIL = "kids_unlock_until";
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable pendingRelock;

    /** Kids mode is on and not unlocked for a while: Home shows only the kids' apps. */
    static boolean kidsLocked(Context ctx) {
        return kidsMode(ctx) && System.currentTimeMillis() >= unlockUntil(ctx);
    }

    /** End of the current timed unlock, or 0. */
    static long unlockUntil(Context ctx) { return prefs(ctx).getLong(UNLOCK_UNTIL, 0); }

    static void unlockFor(Context ctx, int minutes) {
        prefs(ctx).edit().putLong(UNLOCK_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply();
        scheduleRelock(ctx);
    }

    static void relockNow(Context ctx) {
        prefs(ctx).edit().remove(UNLOCK_UNTIL).apply();
        if (pendingRelock != null) MAIN.removeCallbacks(pendingRelock);
    }

    /**
     * When the unlock runs out, bring Home back to the front so the kids' screen returns even if an app
     * is open. Best effort: if Android has stopped the launcher by then, the lock applies on the next Home press.
     */
    static void scheduleRelock(Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (pendingRelock != null) MAIN.removeCallbacks(pendingRelock);
        long wait = unlockUntil(app) - System.currentTimeMillis();
        if (!kidsMode(app) || wait <= 0) return;
        pendingRelock = () -> {
            if (!kidsLocked(app)) return;
            try {
                app.startActivity(new android.content.Intent(app, MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        };
        MAIN.postDelayed(pendingRelock, wait + 500);
    }

    static Set<String> kidsApps(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(KIDS_APPS, Collections.emptySet()));
    }

    static void setKidsApps(Context ctx, Set<String> apps) { prefs(ctx).edit().putStringSet(KIDS_APPS, apps).apply(); }

    // ---------- PIN ----------

    static boolean hasPin(Context ctx) { return prefs(ctx).contains(PIN_HASH); }

    static void setPin(Context ctx, String pin) {
        String salt = UUID.randomUUID().toString();
        prefs(ctx).edit().putString(PIN_SALT, salt).putString(PIN_HASH, hash(salt + pin)).apply();
    }

    static void clearPin(Context ctx) { prefs(ctx).edit().remove(PIN_HASH).remove(PIN_SALT).apply(); }

    static boolean checkPin(Context ctx, String pin) {
        String salt = prefs(ctx).getString(PIN_SALT, null);
        return salt != null && hash(salt + pin).equals(prefs(ctx).getString(PIN_HASH, null));
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static EditText pinField(Context ctx, String hint) {
        EditText e = new EditText(ctx);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint(hint);
        return e;
    }

    /** Asks for the PIN; runs onOk when it matches (or at once when no PIN is set). */
    static void verifyPin(Activity a, String title, Runnable onOk) {
        if (!hasPin(a)) { onOk.run(); return; }
        EditText in = pinField(a, a.getString(R.string.pin_hint));
        new AlertDialog.Builder(a).setTitle(title).setView(in)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (checkPin(a, in.getText().toString())) onOk.run();
                    else Toast.makeText(a, R.string.pin_wrong, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Sets a new PIN, typed twice. */
    static void createPin(Activity a, Runnable onDone) {
        EditText in = pinField(a, a.getString(R.string.pin_new_hint));
        new AlertDialog.Builder(a).setTitle(R.string.pin_set).setView(in)
                .setPositiveButton(R.string.next, (d, w) -> {
                    String p = in.getText().toString();
                    if (p.length() < 4 || p.length() > 8) { Toast.makeText(a, R.string.pin_length, Toast.LENGTH_SHORT).show(); return; }
                    EditText again = pinField(a, a.getString(R.string.pin_again));
                    new AlertDialog.Builder(a).setTitle(R.string.pin_again).setView(again)
                            .setPositiveButton(android.R.string.ok, (d2, w2) -> {
                                if (again.getText().toString().equals(p)) { setPin(a, p); onDone.run(); }
                                else Toast.makeText(a, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                            }).setNegativeButton(android.R.string.cancel, null).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
