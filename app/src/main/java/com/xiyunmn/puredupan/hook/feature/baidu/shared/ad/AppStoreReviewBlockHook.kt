package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import android.app.Activity
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduDialogHookPoints
import java.lang.reflect.Method
import java.util.List as JavaList

/** 屏蔽切换到文件页时出现的应用评分引导。 */
internal object AppStoreReviewBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isAppStoreReviewBlocked) {
            XposedCompat.log("[AppStoreReviewBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0
            installed += hookPerformStrategyEntry(
                cl = cl,
                className = BaiduDialogHookPoints.NETDISK_CONTEXT_OPERATION,
                tag = "NetdiskContext.Operation.performAppStoreReviewStrategy",
            )
            installed += hookPerformStrategyEntry(
                cl = cl,
                className = BaiduDialogHookPoints.OPERATION_APIS_KT,
                tag = "OperationApisKt.performAppStoreReviewStrategy",
            )

            val strategyClass = XposedCompat.findClassOrNull(
                BaiduDialogHookPoints.APP_STORE_REVIEW_SHOW_STRATEGY,
                cl,
            )
            if (strategyClass != null) {
                for (method in strategyClass.declaredMethods) {
                    if (method.name != BaiduDialogHookPoints.APP_STORE_REVIEW_SHOW_CENTER_DIALOG_METHOD) {
                        continue
                    }
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        if (HookSettings.isAppStoreReviewBlocked) {
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
                BaiduDialogHookPoints.APP_STORE_REVIEW_DIALOG,
                cl,
            )
            if (dialogClass != null) {
                val showMethod = findNoArgMethodInHierarchy(
                    dialogClass,
                    BaiduDialogHookPoints.APP_STORE_REVIEW_DIALOG_SHOW_METHOD,
                )
                if (showMethod != null) {
                    mod.hook(showMethod).intercept { chain ->
                        if (
                            HookSettings.isAppStoreReviewBlocked &&
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

            val bottomDialogClass = XposedCompat.findClassOrNull(
                BaiduDialogHookPoints.APP_STORE_SCORE_BOTTOM_DIALOG,
                cl,
            )
            if (bottomDialogClass != null) {
                val showMethod = findNoArgMethodInHierarchy(
                    bottomDialogClass,
                    BaiduDialogHookPoints.APP_STORE_REVIEW_DIALOG_SHOW_METHOD,
                )
                if (showMethod != null) {
                    mod.hook(showMethod).intercept { chain ->
                        if (
                            HookSettings.isAppStoreReviewBlocked &&
                            bottomDialogClass.isInstance(chain.thisObject)
                        ) {
                            XposedCompat.logD("[AppStoreReviewBlockHook] AppStoreScoreBottomDialog.show blocked")
                            return@intercept HookUtils.getDefaultReturnValue(showMethod.returnType)
                        }
                        chain.proceed()
                    }
                    installed++
                } else {
                    XposedCompat.log("[AppStoreReviewBlockHook] AppStoreScoreBottomDialog.show method NOT FOUND")
                }
            } else {
                XposedCompat.logD("[AppStoreReviewBlockHook] AppStoreScoreBottomDialog class not found")
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

    private fun hookPerformStrategyEntry(
        cl: ClassLoader,
        className: String,
        tag: String,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[AppStoreReviewBlockHook] class not found: $className")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            BaiduDialogHookPoints.PERFORM_APP_STORE_REVIEW_STRATEGY_METHOD,
            Activity::class.java,
            JavaList::class.java,
        ) ?: run {
            XposedCompat.logD("[AppStoreReviewBlockHook] method not found: $tag")
            return 0
        }
        method.isAccessible = true
        val mod = XposedCompat.module ?: return 0
        mod.hook(method).intercept { chain ->
            if (HookSettings.isAppStoreReviewBlocked) {
                XposedCompat.logD("[AppStoreReviewBlockHook] $tag blocked")
                HookUtils.getDefaultReturnValue(method.returnType)
            } else {
                chain.proceed()
            }
        }
        return 1
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
