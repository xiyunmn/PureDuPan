package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduFilePageHookPoints

/**
 * 文件页定制 Hook。
 *
 * 底部安全提示走两条互补路径：
 * 1. 数据层：hook ShowSafetyFooterUseCase.realExecute(...) 返回 false，阻止新
 *    文件页把 showSafetyBottomView 置为 true。国内版新路径有效；国际版无此 UseCase。
 * 2. 渲染入口：hook 明文 MyNetdiskFragment.initSafetyBottomView(Context)，enabled
 *    时跳过方法体（不 inflate safety_ability_layout / 不 addFooterView）。国际版
 *    13.11.9 走旧版 ListView 渲染路径，不经过数据层门，必须靠此入口拦截；该方法在
 *    国内/三星/国际三版均明文且体一致，OpenNetdiskFragment 不 override，mBottomSafety
 *    字段仅赋值从不读取，跳过 NPE 安全。不按版本分支。
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
            var installed = 0
            installed += hookSafetyFooterUseCase(cl)
            installed += hookSafetyBottomViewRenderEntry(cl)
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
        // 国际版无 ShowSafetyFooterUseCase 数据层路径（13.11.9 走旧版 ListView 渲染，隐藏靠下方
        // initSafetyBottomView 渲染入口 hook）。跳过数据层解析，避免运行时 cache-miss 触发实时
        // DexKit 扫描又落 candidateCount=0；国内/三星保留数据层路径不变。
        if (BaiduFeatureRuntime.isCurrentIntlHost()) {
            XposedCompat.logD("[FilePageCustomizeHook] safety footer use-case skipped: intl host has no such UseCase")
            return 0
        }
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

    private fun hookSafetyBottomViewRenderEntry(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val context = XposedCompat.findClassOrNull("android.content.Context", cl) ?: return 0
        val method = XposedCompat.findMethodOrNull(
            BaiduFilePageHookPoints.MY_NETDISK_FRAGMENT,
            cl,
            BaiduFilePageHookPoints.INIT_SAFETY_BOTTOM_VIEW_METHOD,
            context,
        ) ?: run {
            XposedCompat.log("[FilePageCustomizeHook] initSafetyBottomView NOT FOUND")
            return 0
        }

        mod.hook(method).intercept { chain ->
            if (isEnabled()) {
                XposedCompat.logD("[FilePageCustomizeHook] initSafetyBottomView skipped (footer not inflated)")
                null
            } else {
                chain.proceed()
            }
        }
        XposedCompat.logD(
            "[FilePageCustomizeHook] safety bottom view render-entry hook installed: " +
                "${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isFilePageCustomizeEnabled &&
            HookSettings.isFilePageBottomSafetyTipHidden
    }
}
