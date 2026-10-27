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

    static void setKidsMode(Context ctx, boolean on) { prefs(ctx).edit().putBoolean(KIDS_MODE, on).apply(); }

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
