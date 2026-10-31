package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookUtils
import java.lang.reflect.Method

/** 拦截 VersionUpdateHelper 的更新提示入口，避免影响共用弹窗组件。 */
internal object DomesticUpdateDialogBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isUpdateDialogBlocked) {
            XposedCompat.log("[DomesticUpdateDialogBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            DomesticUpdateDialogResolver.resolve(cl)?.let { method ->
                installed += hookMethods(listOf(method), "VersionUpdateHelper.showLCVersionDialog")
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[DomesticUpdateDialogBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[DomesticUpdateDialogBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[DomesticUpdateDialogBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DomesticUpdateDialogBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookMethods(methods: List<Method>, logName: String): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        for (method in methods) {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (HookSettings.isUpdateDialogBlocked) {
                    XposedCompat.logD("[DomesticUpdateDialogBlockHook] $logName blocked")
                    HookUtils.getDefaultReturnValue(method.returnType)
                } else {
                    chain.proceed()
                }
            }
            installed++
        }
        return installed
    }
}
