package com.xiyunmn.puredupan.hook.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.xiyunmn.puredupan.hook.config.model.StartupTab
import org.json.JSONArray
import org.json.JSONObject

/** Runtime bottom tabs belong to module state, independently of the user's selection. */
internal object StartupTabCache {
    private const val KEY = "startup_tabs_v1"

    fun read(context: Context, prefs: SharedPreferences): List<StartupTab> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val json = JSONObject(raw)
        if (json.getString("host") != hostVersion(context)) return emptyList()
        val tabs = json.getJSONArray("tabs")
        List(tabs.length()) { index ->
            val tab = tabs.getJSONObject(index)
            StartupTab(tab.getString("id"), tab.getString("label"))
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, prefs: SharedPreferences, tabs: List<StartupTab>) {
        val items = JSONArray()
        tabs.forEach { tab -> items.put(JSONObject().put("id", tab.id).put("label", tab.label)) }
        val raw = JSONObject().put("host", hostVersion(context)).put("tabs", items).toString()
        if (prefs.getString(KEY, null) != raw) prefs.edit().putString(KEY, raw).apply()
    }

    private fun hostVersion(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "${context.packageName}:${info.versionName}:$code"
    }
}
