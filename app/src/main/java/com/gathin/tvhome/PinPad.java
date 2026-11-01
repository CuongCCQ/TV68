package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * TV68: an on-screen number pad for the PIN. The TV's own keyboard shows each digit in large type while it is
 * typed, so the PIN never goes through it: the pad draws dots only, and the remote's number keys work too.
 */
final class PinPad {
    static final int MAX = 8;

    final LinearLayout view;
    private final TextView dots;
    private final StringBuilder pin = new StringBuilder();
    private final Button firstKey;

    PinPad(Context ctx) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        dots = new TextView(ctx);
        dots.setTextSize(30f);
        dots.setLetterSpacing(0.3f);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, (int) (6 * dp), 0, (int) (10 * dp));
        view.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"⌫", "0", "✕"}};
        Button first = null;
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (String k : r) {
                Button b = new Button(ctx);
                b.setText(k);
                b.setTextSize(22f);
                b.setAllCaps(false);
                b.setOnClickListener(v -> press(k));
                row.addView(b, new LinearLayout.LayoutParams((int) (88 * dp), (int) (64 * dp)));
                if (first == null) first = b;
            }
            view.addView(row);
        }
        firstKey = first;
        refresh();
    }

    private void press(String k) {
        if ("⌫".equals(k)) { if (pin.length() > 0) pin.setLength(pin.length() - 1); }
        else if ("✕".equals(k)) pin.setLength(0);
        else if (pin.length() < MAX) pin.append(k);
        refresh();
    }

    private void refresh() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) s.append('●');
        if (pin.length() < 4) for (int i = pin.length(); i < 4; i++) s.append('○');
        dots.setText(s);
    }

    String get() { return pin.toString(); }

    void clear() { pin.setLength(0); refresh(); }

    void focus() { firstKey.requestFocus(); }

    /** The remote's 0-9 keys type digits and its Back-space/DEL key erases, wherever focus is in the dialog. */
    void listenTo(AlertDialog dialog) {
        dialog.setOnKeyListener((d, keyCode, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) { press(String.valueOf(keyCode - KeyEvent.KEYCODE_0)); return true; }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) { press(String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0)); return true; }
            if (keyCode == KeyEvent.KEYCODE_DEL) { press("⌫"); return true; }
            return false;
        });
    }
}
