package com.xiyunmn.puredupan.hook.feature.ui

import android.view.View
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.ui.SettingsMenuHook

object HomeUploadEntryHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val fragmentClass = XposedCompat.findClassOrNull(
                "com.baidu.netdisk.home25ai.fragment.HomeSearchboxFragment", cl
            )
            if (fragmentClass == null) {
                hookState.reset()
                return
            }

            val onCreateViewMethod = XposedCompat.findMethodOrNull(
                fragmentClass, "onCreateView",
                android.view.LayoutInflater::class.java,
                android.view.ViewGroup::class.java,
                android.os.Bundle::class.java
            )
            if (onCreateViewMethod == null) {
                hookState.reset()
                return
            }

            mod.hook(onCreateViewMethod).intercept { chain ->
                val result = chain.proceed()
                try {
                    val rootView = result as? View ?: return@intercept result
                    val uploadEntry = rootView.findViewById<View>(
                        rootView.context.resources.getIdentifier(
                            "upload_file_entry", "id", "com.baidu.netdisk"
                        )
                    )
                    uploadEntry?.setOnLongClickListener {
                        SettingsMenuHook.showModuleSettingsDialog(it.context, cl)
                        true
                    }
                } catch (_: Throwable) {}
                result
            }
        } catch (e: Exception) {
            hookState.reset()
            throw e
        }
    }

}
