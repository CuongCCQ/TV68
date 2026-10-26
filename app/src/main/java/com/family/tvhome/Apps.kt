package com.family.tvhome

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppItem(val pkg: String, val label: String, val icon: Drawable, val launch: Intent)

object Apps {
    /** Liệt kê app có thể mở trên TV (ưu tiên icon/intent kiểu Leanback). */
    fun load(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppItem>()

        fun scan(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || result.containsKey(pkg)) continue
                val launch = Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val icon = try {
                    if (category == Intent.CATEGORY_LEANBACK_LAUNCHER) ri.activityInfo.loadBanner(pm) ?: ri.loadIcon(pm)
                    else ri.loadIcon(pm)
                } catch (e: Exception) { ri.loadIcon(pm) }
                result[pkg] = AppItem(pkg, ri.loadLabel(pm).toString(), icon, launch)
            }
        }
        scan(Intent.CATEGORY_LEANBACK_LAUNCHER)
        scan(Intent.CATEGORY_LAUNCHER)
        return result.values.sortedBy { it.label.lowercase() }
    }

    fun openSystemSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
