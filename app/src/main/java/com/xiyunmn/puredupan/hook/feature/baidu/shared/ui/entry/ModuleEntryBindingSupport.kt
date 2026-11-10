package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry

import android.content.Context
import android.view.View
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook
import java.util.Collections
import java.util.WeakHashMap

internal object ModuleEntryBindingSupport {
    private data class Binding(
        val original: View.OnLongClickListener?, val longClickable: Boolean,
        val installed: View.OnLongClickListener, val tag: String, val entry: String,
    )
    private val bindings = WeakHashMap<View, Binding>()
    private val loggedBindings: MutableMap<View, String> =
        Collections.synchronizedMap(WeakHashMap())

    fun bindLongPressToSettings(
        view: View?,
        classLoader: ClassLoader?,
        tag: String,
        entryName: String,
    ) {
        if (view == null || classLoader == null || com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return
        val bindingKey = "$tag:$entryName"
        val shouldLog = loggedBindings.put(view, bindingKey) != bindingKey
        val previous = bindings[view]
        val info = XposedCompat.getObjectField(view, "mListenerInfo")
        val current = info?.let { XposedCompat.getObjectField(it, "mOnLongClickListener") as? View.OnLongClickListener }
        val original = if (previous != null && current === previous.installed) previous.original else current
        val originalLongClickable = if (previous != null && current === previous.installed) previous.longClickable else view.isLongClickable
        val listener = View.OnLongClickListener {
            showSettings(it.context, classLoader, tag, entryName)
            true
        }
        bindings[view] = Binding(original, originalLongClickable, listener, tag, entryName)
        view.setOnLongClickListener(listener)
        if (shouldLog) {
            XposedCompat.log("[$tag] long-press listener bound to $entryName")
        }
    }

    fun detachAll(): ArrayList<Array<Any?>> {
        val targets = ArrayList<Array<Any?>>()
        bindings.entries.toList().forEach { (view, binding) ->
            val info = XposedCompat.getObjectField(view, "mListenerInfo")
            if (info != null && XposedCompat.getObjectField(info, "mOnLongClickListener") === binding.installed) {
                view.setOnLongClickListener(binding.original)
                view.isLongClickable = binding.longClickable
            }
            targets.add(arrayOf("entry", java.lang.ref.WeakReference(view), binding.tag, binding.entry))
        }
        bindings.clear()
        loggedBindings.clear()
        return targets
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
