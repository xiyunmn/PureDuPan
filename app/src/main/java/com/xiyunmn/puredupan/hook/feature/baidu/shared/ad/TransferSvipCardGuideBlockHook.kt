package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints
import java.lang.reflect.Method

/**
 * 屏蔽转存成功弹窗下方的 SVIP 优惠广告卡片。
 *
 * Rubik 的 GuideContext 是国内版、国际版和三星版共用的稳定业务入口。
 * 这里同时拦截静态桥接方法和 Companion 实例方法，让广告卡片不进入创建流程，
 * 避免依赖资源 ID 或在弹窗 View 树中做兼容扫描。
 */
internal object TransferSvipCardGuideBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[TransferSvipCardGuideBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val methods = linkedSetOf<Method>()
            listOf(
                BaiduTransferHookPoints.BUSINESS_GUIDE_CONTEXT,
                BaiduTransferHookPoints.BUSINESS_GUIDE_CONTEXT_COMPANION,
            ).forEach { className ->
                val clazz = XposedCompat.findClassOrNull(className, cl)
                if (clazz == null) {
                    XposedCompat.logD("[TransferSvipCardGuideBlockHook] class not found: $className")
                } else {
                    clazz.declaredMethods.filterTo(methods, ::isTargetMethod)
                }
            }

            if (methods.isEmpty()) {
                hookState.reset()
                XposedCompat.log(
                    "[TransferSvipCardGuideBlockHook] checkShowSvipCardGuide NOT FOUND",
                )
                return
            }

            var installedCount = 0
            methods.forEach { method ->
                try {
                    mod.hook(method).intercept { chain ->
                        if (isEnabled()) {
                            XposedCompat.logD(
                                "[TransferSvipCardGuideBlockHook] SVIP card guide blocked",
                            )
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                    installedCount += 1
                } catch (t: Throwable) {
                    XposedCompat.log(
                        "[TransferSvipCardGuideBlockHook] hook FAILED: " +
                            "${method.declaringClass.name}.${method.name}: ${t.message}",
                    )
                    XposedCompat.log(t)
                }
            }

            if (installedCount == 0) {
                hookState.reset()
                XposedCompat.log("[TransferSvipCardGuideBlockHook] no target installed")
                return
            }

            XposedCompat.log(
                "[TransferSvipCardGuideBlockHook] hook INSTALLED: $installedCount target(s)",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[TransferSvipCardGuideBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isTargetMethod(method: Method): Boolean {
        val parameterTypes = method.parameterTypes
        return method.name == BaiduTransferHookPoints.CHECK_SHOW_SVIP_CARD_GUIDE_METHOD &&
            method.returnType == Void.TYPE &&
            parameterTypes.size == 4 &&
            parameterTypes[0].name == BaiduTransferHookPoints.ANDROIDX_FRAGMENT_ACTIVITY &&
            parameterTypes[1].name == "android.view.ViewGroup" &&
            parameterTypes[2].name == BaiduTransferHookPoints.KOTLIN_FUNCTION0 &&
            parameterTypes[3].name == BaiduTransferHookPoints.KOTLIN_FUNCTION0
    }

    private fun isEnabled(): Boolean =
        HookSettings.isFilePageCustomizeEnabled && HookSettings.isTransferSvipCardBlocked
}
