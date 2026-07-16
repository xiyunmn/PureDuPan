package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 文件页定制 Hook。
 *
 * 底部安全提示走数据层：hook ShowSafetyFooterUseCase.realExecute(...)
 * 返回 false，阻止宿主把 showSafetyBottomView 置为 true。该类在弱混淆分支保留原
 * 类名；国内版/三星版 13.27.8 强混淆分支通过 Kotlin Metadata / 方法形态 / DexKit
 * 定位，不按版本分支。
 *
 * 已删除旧 View 树路径：FileListChildFragment 根节点的 OnGlobalLayoutListener /
 * OnPreDrawListener / postDelayed 循环，以及 safe_ability_layout 资源 ID 全树递归。
 */
internal object FilePageCustomizeHook {

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[FilePageCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val installed = hookSafetyFooterUseCase(cl)
            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[FilePageCustomizeHook] hooks NOT INSTALLED")
                return
            }

            XposedCompat.log("[FilePageCustomizeHook] hook INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[FilePageCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookSafetyFooterUseCase(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = FilePageSafetyFooterUseCaseDexKitResolver.resolveClass(cl) ?: run {
            XposedCompat.log("[FilePageCustomizeHook] ShowSafetyFooterUseCase NOT RESOLVED")
            return 0
        }
        val methods = FilePageSafetyFooterUseCaseDexKitResolver.findRealExecuteMethods(clazz)
        if (methods.isEmpty()) {
            XposedCompat.log("[FilePageCustomizeHook] ShowSafetyFooterUseCase.realExecute NOT FOUND")
            return 0
        }

        for (method in methods) {
            mod.hook(method).intercept { chain ->
                if (isEnabled()) {
                    XposedCompat.logD(
                        "[FilePageCustomizeHook] ShowSafetyFooterUseCase blocked: " +
                            "${method.declaringClass.name}.${method.name}",
                    )
                    false
                } else {
                    chain.proceed()
                }
            }
            XposedCompat.logD(
                "[FilePageCustomizeHook] safety footer use-case hook installed: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
        return methods.size
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isFilePageCustomizeEnabled &&
            HookSettings.isFilePageBottomSafetyTipHidden
    }
}
