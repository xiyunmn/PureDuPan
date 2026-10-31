package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduDialogHookPoints

/** 屏蔽用户中心的 SVIP 专属图标全屏引导。 */
internal object SvipIconGuideBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isFullScreenBackupBlocked) {
            XposedCompat.log("[SvipIconGuideBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduDialogHookPoints.SVIP_ICON_GUIDE,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.logD("[SvipIconGuideBlockHook] SvipIconGuide class not found")
                return
            }

            var installed = 0
            for (method in clazz.declaredMethods) {
                if (method.name != BaiduDialogHookPoints.SVIP_ICON_GUIDE_SHOW_GUIDE_METHOD) {
                    continue
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isFullScreenBackupBlocked) {
                        XposedCompat.logD("[SvipIconGuideBlockHook] showGuide blocked")
                        HookUtils.getDefaultReturnValue(method.returnType)
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[SvipIconGuideBlockHook] showGuide NOT FOUND")
                return
            }

            XposedCompat.log("[SvipIconGuideBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[SvipIconGuideBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[SvipIconGuideBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }
}
