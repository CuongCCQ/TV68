package com.family.tvhome

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object PinDialog {
    /** Hỏi PIN; gọi onOk khi đúng. */
    fun verify(ctx: Context, prefs: Prefs, title: String = "Nhập mật khẩu", onOk: () -> Unit) {
        if (!prefs.hasPin()) { onOk(); return }
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Mã PIN"
        }
        AlertDialog.Builder(ctx).setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (prefs.checkPin(input.text.toString())) onOk()
                else Toast.makeText(ctx, "Sai mật khẩu", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Huỷ", null).show()
    }

    /** Đặt PIN mới (nhập 2 lần). */
    fun create(ctx: Context, prefs: Prefs, onDone: () -> Unit) {
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4–8 số"
        }
        AlertDialog.Builder(ctx).setTitle("Đặt mật khẩu trẻ em").setView(input)
            .setPositiveButton("Tiếp") { _, _ ->
                val p = input.text.toString()
                if (p.length !in 4..8) { Toast.makeText(ctx, "PIN phải 4–8 số", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val again = EditText(ctx).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    hint = "Nhập lại"
                }
                AlertDialog.Builder(ctx).setTitle("Nhập lại mật khẩu").setView(again)
                    .setPositiveButton("Lưu") { _, _ ->
                        if (again.text.toString() == p) { prefs.setPin(p); onDone() }
                        else Toast.makeText(ctx, "Hai lần nhập không khớp", Toast.LENGTH_SHORT).show()
                    }.setNegativeButton("Huỷ", null).show()
            }.setNegativeButton("Huỷ", null).show()
    }
}
