package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.os.Bundle
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.HookUtils
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints

/**
 * 下载页定制 Hook。
 *
 * 所有目标都挂下载页的业务或渲染单点：
 * - 游戏推荐浮窗动画：YunGameGuideViewModel.checkShowGameGuide(FragmentActivity)
 * - 推广广告：YouaGuide.setGuideViewUI(AlbumGuideResult.Success)
 * - 底部会员推广区域：DownloadProbationaryView.onBegin/onRunning/onEnd
 *
 * 不在 View 树中查找、遍历或递归处理控件。
 */
internal object DownloadPageCustomizeHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!isAnyOptionEnabled()) {
            XposedCompat.log("[DownloadPageCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            if (HookSettings.isDownloadPageGameGuideHidden) {
                installedCount += hookGameGuide(cl)
            }
            if (HookSettings.isDownloadPagePromotionAdHidden) {
                installedCount += hookPromotionAd(cl)
            }
            if (HookSettings.isDownloadPageMemberPromotionHidden) {
                installedCount += hookMemberPromotion(cl)
            }
            if (installedCount == 0) {
                hookState.reset()
                XposedCompat.log("[DownloadPageCustomizeHook] no hooks installed")
                return
            }
            XposedCompat.log("[DownloadPageCustomizeHook] hooks INSTALLED: $installedCount")
        } catch (e: ReflectiveOperationException) {
            hookState.reset()
            XposedCompat.log(
                "[DownloadPageCustomizeHook] FAILED (reflection): ${e.javaClass.simpleName}: ${e.message}",
            )
            XposedCompat.log(e)
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[DownloadPageCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookGameGuide(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val viewModelClass = XposedCompat.findClassOrNull(
            BaiduTransferHookPoints.YUN_GAME_GUIDE_VIEW_MODEL,
            cl,
        ) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] YunGameGuideViewModel class NOT FOUND")
            return 0
        }
        val fragmentActivityClass = XposedCompat.findClassOrNull(
            BaiduTransferHookPoints.ANDROIDX_FRAGMENT_ACTIVITY,
            cl,
        ) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] FragmentActivity class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            viewModelClass,
            BaiduTransferHookPoints.CHECK_SHOW_GAME_GUIDE_METHOD,
            fragmentActivityClass,
        ) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] checkShowGameGuide(FragmentActivity) NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isGameGuideEnabled()) {
                XposedCompat.logD("[DownloadPageCustomizeHook] game guide display blocked")
                return@intercept HookUtils.getDefaultReturnValue(method.returnType)
            }
            chain.proceed()
        }
        XposedCompat.log(
            "[DownloadPageCustomizeHook] hook INSTALLED: ${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    private fun hookPromotionAd(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = DownloadPagePromotionAdDexKitResolver.resolve(cl) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] YouaGuide render method NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isPromotionAdEnabled()) {
                XposedCompat.logD("[DownloadPageCustomizeHook] promotion ad render blocked")
                return@intercept HookUtils.getDefaultReturnValue(method.returnType)
            }
            chain.proceed()
        }
        XposedCompat.log(
            "[DownloadPageCustomizeHook] hook INSTALLED: ${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    private fun hookMemberPromotion(cl: ClassLoader): Int {
        val probationaryViewClass = XposedCompat.findClassOrNull(
            BaiduTransferHookPoints.DOWNLOAD_PROBATIONARY_VIEW,
            cl,
        ) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] DownloadProbationaryView class NOT FOUND")
            return 0
        }
        val fragmentActivityClass = XposedCompat.findClassOrNull(
            BaiduTransferHookPoints.ANDROIDX_FRAGMENT_ACTIVITY,
            cl,
        ) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] FragmentActivity class NOT FOUND")
            return 0
        }
        var installedCount = 0
        installedCount += hookMemberPromotionMethod(
            probationaryViewClass,
            BaiduTransferHookPoints.PROBATIONARY_ON_BEGIN_METHOD,
            Bundle::class.java,
            fragmentActivityClass,
        )
        installedCount += hookMemberPromotionMethod(
            probationaryViewClass,
            BaiduTransferHookPoints.PROBATIONARY_ON_RUNNING_METHOD,
            Bundle::class.java,
            Long::class.javaPrimitiveType ?: Long::class.java,
        )
        installedCount += hookMemberPromotionMethod(
            probationaryViewClass,
            BaiduTransferHookPoints.PROBATIONARY_ON_END_METHOD,
            Bundle::class.java,
            Int::class.javaPrimitiveType ?: Int::class.java,
            Boolean::class.javaPrimitiveType ?: Boolean::class.java,
            fragmentActivityClass,
        )
        return installedCount
    }

    private fun hookMemberPromotionMethod(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(clazz, methodName, *paramTypes) ?: run {
            XposedCompat.log("[DownloadPageCustomizeHook] $methodName(${paramTypes.size}) NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            if (isMemberPromotionEnabled()) {
                (chain.thisObject as? View)?.visibility = View.GONE
                XposedCompat.logD("[DownloadPageCustomizeHook] member promotion render blocked: $methodName")
                return@intercept HookUtils.getDefaultReturnValue(method.returnType)
            }
            chain.proceed()
        }
        XposedCompat.log(
            "[DownloadPageCustomizeHook] hook INSTALLED: ${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    private fun isAnyOptionEnabled(): Boolean {
        return isGameGuideEnabled() || isPromotionAdEnabled() || isMemberPromotionEnabled()
    }

    private fun isGameGuideEnabled(): Boolean {
        return HookSettings.isDownloadPageCustomizeEnabled &&
            HookSettings.isDownloadPageGameGuideHidden
    }

    private fun isPromotionAdEnabled(): Boolean {
        return HookSettings.isDownloadPageCustomizeEnabled &&
            HookSettings.isDownloadPagePromotionAdHidden
    }

    private fun isMemberPromotionEnabled(): Boolean {
        return HookSettings.isDownloadPageCustomizeEnabled &&
            HookSettings.isDownloadPageMemberPromotionHidden
    }
}
