package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry

import android.content.Context
import android.view.View
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook
import java.util.Collections
import java.util.WeakHashMap

internal object ModuleEntryBindingSupport {
    private val loggedBindings: MutableMap<View, String> =
        Collections.synchronizedMap(WeakHashMap())

    fun bindLongPressToSettings(
        view: View?,
        classLoader: ClassLoader?,
        tag: String,
        entryName: String,
    ) {
        if (view == null || classLoader == null) return
        val bindingKey = "$tag:$entryName"
        val shouldLog = loggedBindings.put(view, bindingKey) != bindingKey
        view.setOnLongClickListener {
            showSettings(it.context, classLoader, tag, entryName)
            true
        }
        if (shouldLog) {
            XposedCompat.log("[$tag] long-press listener bound to $entryName")
        }
    }

    fun findViewByEntryName(root: View, entryName: String): View? {
        val context = root.context ?: return null
        val resId = context.resources.getIdentifier(entryName, "id", context.packageName)
        if (resId == 0) return null
        return root.findViewById(resId)
    }

    private fun showSettings(
        context: Context,
        classLoader: ClassLoader,
        tag: String,
        entryName: String,
    ) {
        try {
            SettingsMenuHook.showModuleSettingsDialog(context, classLoader)
        } catch (t: Throwable) {
            XposedCompat.logW("[$tag] show settings dialog failed for $entryName: ${t.message}")
        }
    }
}
