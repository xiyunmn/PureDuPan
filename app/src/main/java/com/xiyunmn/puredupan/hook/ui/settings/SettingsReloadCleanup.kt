package com.xiyunmn.puredupan.hook.ui.settings

import android.app.Dialog
import android.view.View
import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.MemberCardGestureBinding
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.MemberCardBackgroundLayout
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.ModuleEntryBindingSupport
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme.AboutMeBottomContentPositionHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.membercard.DomesticMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.membercard.IntlMemberCardCustomizeHook
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 热重载只跨代传递系统视图与字符串，不保留旧代回调。 */
internal object SettingsReloadCleanup {
    private val dialogs = WeakHashMap<Dialog, Boolean>()
    private val dialogCleanups = WeakHashMap<Dialog, WeakReference<() -> Unit>>()

    fun onDispose(dialog: Dialog, cleanup: () -> Unit) {
        var active = true
        val dispose = {
            if (active) {
                active = false
                cleanup()
            }
        }
        dialogCleanups[dialog] = WeakReference(dispose)
        dialog.setOnDismissListener {
            dispose()
            dialogCleanups.remove(dialog)
        }
    }

    fun disposeDialog(dialog: Dialog) {
        dialogCleanups.remove(dialog)?.get()?.invoke()
    }

    fun showDialog(dialog: Dialog) {
        if (ModuleLifetime.isRetiring) return
        dialogs[dialog] = true
        val decor = dialog.window?.decorView
        decor?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                dialogs.remove(dialog)
                v.removeOnAttachStateChangeListener(this)
            }
        })
        try {
            dialog.show()
        } catch (t: Throwable) {
            dialogs.remove(dialog)
            throw t
        }
    }

    fun closeDialogs() {
        DeferredSettingsSave.flushAll()
        SettingsMainDialog.disposeForReload()
        SettingsDialogShade.disposeForReload()
        MemberCardBackgroundEditorDialog.disposeForReload()
        (dialogs.keys.toList() + dialogCleanups.keys.toList()).distinct().forEach {
            disposeDialog(it)
            it.setOnDismissListener(null)
            it.dismiss()
        }
        dialogCleanups.clear()
        dialogs.clear()
        UiStyle.detachThemes()
    }

    fun detachHostViews(): ArrayList<Array<Any?>> {
        val targets = ModuleEntryBindingSupport.detachAll()
        targets.addAll(MemberCardGestureBinding.detachAll())
        targets.addAll(MemberCardBackgroundLayout.detachAll())
        targets.addAll(DomesticMemberCardCustomizeHook.detachSizes())
        targets.addAll(IntlMemberCardCustomizeHook.detachSizes())
        targets.addAll(AboutMeBottomContentPositionHook.exportPositionTargets())
        return targets
    }

    fun restoreHostViews(targets: List<Array<Any?>>, cl: ClassLoader) {
        if (ModuleLifetime.isRetiring) return
        fun targetView(target: Array<Any?>): View? {
            val view = (target.getOrNull(1) as? WeakReference<*>)?.get() as? View ?: return null
            val activity = view.context as? android.app.Activity
            if (activity?.isFinishing == true || activity?.isDestroyed == true) return null
            return view
        }
        // 先交回原始尺寸，防止后续布局回调把调整后的值记成默认值。
        for (target in targets) {
            val role = target.getOrNull(0) as? String
            if (role != "member-size-domestic" && role != "member-size-intl") continue
            val view = targetView(target) ?: continue
            val width = target.getOrNull(2) as? Int ?: continue
            val height = target.getOrNull(3) as? Int ?: continue
            if (role == "member-size-domestic") DomesticMemberCardCustomizeHook.restoreOriginalSize(view, width, height)
            else IntlMemberCardCustomizeHook.restoreOriginalSize(view, width, height)
        }
        for (target in targets) {
            val view = targetView(target) ?: continue
            when (target.getOrNull(0) as? String) {
                "entry" -> ModuleEntryBindingSupport.bindLongPressToSettings(
                    view, cl, target.getOrNull(2) as? String ?: continue,
                    target.getOrNull(3) as? String ?: continue,
                )
                "background-domestic" -> (view as? android.widget.ImageView)?.let { DomesticMemberCardCustomizeHook.rebindBackground(it) }
                "background-intl" -> (view as? android.widget.ImageView)?.let { IntlMemberCardCustomizeHook.rebindBackground(it) }
                "member-domestic" -> DomesticMemberCardCustomizeHook.rebindGesture(view)
                "member-intl" -> IntlMemberCardCustomizeHook.rebindGesture(view)
                "member-size-domestic" -> DomesticMemberCardCustomizeHook.rebindSize(view)
                "member-size-intl" -> IntlMemberCardCustomizeHook.rebindSize(view)
            }
        }
        // 卡片尺寸就绪后，再恢复下方内容的缓存应用与校准。
        for (target in targets) {
            if (target.getOrNull(0) != "aboutme-position") continue
            targetView(target)?.let(AboutMeBottomContentPositionHook::rebindPosition)
        }
    }
}
