package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.view.View
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.util.WeakHashMap

/** 独立管理点击拦截与长按预览，关闭时归还宿主原有监听器。 */
internal object MemberCardGestureBinding {
    private data class Binding(
        val role: String,
        val originalClick: View.OnClickListener?,
        val originalLongClick: View.OnLongClickListener?,
        val originallyClickable: Boolean,
        val originallyLongClickable: Boolean,
        val click: View.OnClickListener,
        val longClick: View.OnLongClickListener,
    )

    private val bindings = WeakHashMap<View, Binding>()

    fun apply(view: View, snapshot: SettingsSnapshot, role: String, openImage: (View) -> Unit) {
        if (com.xiyunmn.puredupan.hook.runtime.ModuleLifetime.isRetiring) return
        restore(view)
        val removeClick = snapshot.isMemberCardCustomizeEnabled && snapshot.isMemberCardClickRemoved
        val preview = snapshot.isMemberCardCustomizeEnabled && snapshot.isMemberCardBackgroundViewedOnClick &&
            !snapshot.memberCardBackgroundUri.isNullOrBlank()
        if (!removeClick && !preview) return
        val listenerInfo = XposedCompat.getObjectField(view, "mListenerInfo")
        val originalClick = listenerInfo?.let {
            XposedCompat.getObjectField(it, "mOnClickListener") as? View.OnClickListener
        }
        val originalLongClick = listenerInfo?.let {
            XposedCompat.getObjectField(it, "mOnLongClickListener") as? View.OnLongClickListener
        }
        val binding = Binding(
            role, originalClick, originalLongClick, view.isClickable, view.isLongClickable,
            View.OnClickListener { target ->
                val current = HookSettings.memberCardSnapshot()
                if (!current.isMemberCardCustomizeEnabled || !current.isMemberCardClickRemoved) {
                    apply(target, current, role, openImage)
                    target.performClick()
                }
            },
            View.OnLongClickListener { target ->
                val current = HookSettings.memberCardSnapshot()
                if (current.isMemberCardCustomizeEnabled && current.isMemberCardBackgroundViewedOnClick &&
                    !current.memberCardBackgroundUri.isNullOrBlank()
                ) {
                    openImage(target)
                    true
                } else {
                    apply(target, current, role, openImage)
                    target.performLongClick()
                }
            },
        )
        bindings[view] = binding
        if (removeClick) view.setOnClickListener(binding.click)
        if (preview) view.setOnLongClickListener(binding.longClick)
    }

    fun detachAll(): ArrayList<Array<Any?>> {
        val targets = ArrayList<Array<Any?>>()
        bindings.entries.toList().forEach { (view, binding) ->
            targets.add(arrayOf(binding.role, java.lang.ref.WeakReference(view)))
            restore(view)
        }
        return targets
    }

    private fun restore(view: View) {
        val binding = bindings.remove(view) ?: return
        val info = XposedCompat.getObjectField(view, "mListenerInfo") ?: return
        if (XposedCompat.getObjectField(info, "mOnClickListener") === binding.click) {
            view.setOnClickListener(binding.originalClick)
            view.isClickable = binding.originallyClickable
        }
        if (XposedCompat.getObjectField(info, "mOnLongClickListener") === binding.longClick) {
            view.setOnLongClickListener(binding.originalLongClick)
            view.isLongClickable = binding.originallyLongClickable
        }
    }
}
