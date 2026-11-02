package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.Set;

/**
 * TV68: closes the gap the remote's app buttons (YouTube, Netflix…) and voice search leave in kids mode. They open
 * an app without going through Home, so this service watches which app comes to the front; when it is a locked
 * one, it sends the TV Home and asks there for the PIN and a time limit. It reads no screen content: only the
 * package name of the window that opened. Enabled once over adb (see README).
 */
public class KidsGuardService extends AccessibilityService {
    static final String EXTRA_LOCKED_PKG = "tv68_locked_pkg";
    /** The TV's Settings, so kids mode cannot be switched off from there (this service included). */
    private static final String TV_SETTINGS = "com.android.tv.settings";

    private Set<String> launchable = new HashSet<>();
    private long launchableAt;
    private String lastBlocked;
    private long lastBlockedAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();
        if (pkg.equals(getPackageName()) || !Family.kidsLocked(this) || Family.appAllowed(this, pkg)) return;
        if (!isApp(pkg)) return;
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastBlocked) && now - lastBlockedAt < 1500) return;
        lastBlocked = pkg;
        lastBlockedAt = now;
        performGlobalAction(GLOBAL_ACTION_HOME);
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_LOCKED_PKG, pkg));
        } catch (Exception ignored) { }
    }

    /** Only apps someone can open count; keyboards, system overlays and the like are left alone. */
    private boolean isApp(String pkg) {
        if (TV_SETTINGS.equals(pkg)) return true;
        long now = SystemClock.elapsedRealtime();
        if (now - launchableAt > 60_000) {
            Set<String> found = new HashSet<>();
            PackageManager pm = getPackageManager();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0))
                    if (ri.activityInfo != null) found.add(ri.activityInfo.packageName);
            }
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) for (InputMethodInfo im : imm.getInputMethodList()) found.remove(im.getPackageName());
            launchable = found;
            launchableAt = now;
        }
        return launchable.contains(pkg);
    }


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // The service stays bound by the system, which keeps the Telegram poller alive while Home is in the background.
        Telegram.ensurePolling(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onInterrupt() { }
}
