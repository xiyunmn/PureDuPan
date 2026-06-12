package com.xiyunmn.puredupan.hook.feature.ad

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookUtils
import java.lang.reflect.Method

/**
 * Blocks the app-store review guide dialog shown when switching to the file tab.
 *
 * The stack shows AppStoreReviewShowStrategy.showCenterDialog(...) builds AppStoreReviewDialog
 * and inflates DialogAppStoreReviewBinding from MyNetdiskActivity.onCreate. Blocking that narrow
 * business display entry avoids touching the tab-switch Handler or broad dialog infrastructure.
 */
object AppStoreReviewBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAppStoreReviewBlocked) {
            XposedCompat.log("[AppStoreReviewBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0

            val strategyClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.APP_STORE_REVIEW_SHOW_STRATEGY,
                cl,
            )
            if (strategyClass != null) {
                for (method in strategyClass.declaredMethods) {
                    if (method.name != StableBaiduPanHookPoints.APP_STORE_REVIEW_SHOW_CENTER_DIALOG_METHOD) {
                        continue
                    }
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        if (ConfigManager.isAppStoreReviewBlocked) {
                            XposedCompat.logD("[AppStoreReviewBlockHook] showCenterDialog blocked")
                            HookUtils.getDefaultReturnValue(method.returnType)
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                }
            } else {
                XposedCompat.log("[AppStoreReviewBlockHook] AppStoreReviewShowStrategy class NOT FOUND")
            }

            val dialogClass = XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.APP_STORE_REVIEW_DIALOG,
                cl,
            )
            if (dialogClass != null) {
                val showMethod = findNoArgMethodInHierarchy(
                    dialogClass,
                    StableBaiduPanHookPoints.APP_STORE_REVIEW_DIALOG_SHOW_METHOD,
                )
                if (showMethod != null) {
                    mod.hook(showMethod).intercept { chain ->
                        if (
                            ConfigManager.isAppStoreReviewBlocked &&
                            dialogClass.isInstance(chain.thisObject)
                        ) {
                            XposedCompat.logD("[AppStoreReviewBlockHook] AppStoreReviewDialog.show blocked")
                            return@intercept HookUtils.getDefaultReturnValue(showMethod.returnType)
                        }
                        chain.proceed()
                    }
                    installed++
                } else {
                    XposedCompat.log("[AppStoreReviewBlockHook] AppStoreReviewDialog.show method NOT FOUND")
                }
            } else {
                XposedCompat.log("[AppStoreReviewBlockHook] AppStoreReviewDialog class NOT FOUND")
            }

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[AppStoreReviewBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[AppStoreReviewBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log("[AppStoreReviewBlockHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}")
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[AppStoreReviewBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun HookUtils.getDefaultReturnValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private fun findNoArgMethodInHierarchy(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }
}
