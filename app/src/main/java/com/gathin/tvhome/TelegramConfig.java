package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * TV68: receives the Telegram bot token over adb, so it is never typed on the TV or stored in the repo:
 * adb shell am broadcast -a com.gathin.tvhome.TELEGRAM -n com.gathin.tvhome/.TelegramConfig --es token TOKEN
 * Guarded by WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps don't.
 */
public class TelegramConfig extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String token = intent.getStringExtra("token");
        if (token == null || !token.matches("\\d+:[A-Za-z0-9_-]{20,}")) {
            setResultData("bad token");
            return;
        }
        Telegram.setToken(ctx, token);
        setResultData("ok, link code " + Telegram.linkCode(ctx));
    }
}
