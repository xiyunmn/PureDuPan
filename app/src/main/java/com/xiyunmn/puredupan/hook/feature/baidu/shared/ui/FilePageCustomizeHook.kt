package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.view.View
import android.view.ViewGroup
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
 *    时跳过方法体（不 inflate safety_ability_layout / 不 addFooterView）。该方法在
 *    国内/三星/国际三版均保留，作为旧 ListView 文件页兼容入口。
 * 3. RecyclerView v2：国际版主文件页实际使用 FileListFragment / FileListChildFragment，
 *    两者直接向 FileListRecyclerView 添加 safety_ability_layout footer，完全绕过旧入口。
 *    在 addFooterView(View) 参数边界识别 SafetyInstructionsView 后跳过添加。
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
            installed += hookSafetyRecyclerFooterEntry(cl)
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
        // 国际版无 ShowSafetyFooterUseCase 数据层路径；实际 RecyclerView v2 由下方精准
        // footer 参数 Hook 覆盖，旧 ListView 仍由 initSafetyBottomView 兼容。跳过数据层解析，
        // 避免运行时 cache-miss 触发实时 DexKit 扫描又落 candidateCount=0。
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

    private fun hookSafetyRecyclerFooterEntry(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val recyclerClass = XposedCompat.findClassOrNull(
            BaiduFilePageHookPoints.FILE_LIST_RECYCLER_VIEW,
            cl,
        ) ?: run {
            XposedCompat.logD("[FilePageCustomizeHook] FileListRecyclerView unavailable")
            return 0
        }
        val methods = recyclerClass.declaredMethods.filter { method ->
            method.name == BaiduFilePageHookPoints.ADD_FOOTER_VIEW_METHOD &&
                method.parameterTypes.contentEquals(arrayOf(View::class.java)) &&
                method.returnType == Void.TYPE
        }
        methods.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val footer = chain.args.firstOrNull() as? View
                if (isEnabled() && footer != null && isSafetyFooter(footer)) {
                    XposedCompat.logD(
                        "[FilePageCustomizeHook] RecyclerView safety footer blocked: " +
                            "${method.declaringClass.name}.${method.name}",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            XposedCompat.logD(
                "[FilePageCustomizeHook] RecyclerView footer hook installed: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
        return methods.size
    }

    private fun isSafetyFooter(root: View): Boolean {
        if (root.javaClass.name == BaiduFilePageHookPoints.SAFETY_INSTRUCTIONS_VIEW) return true
        val safetyId = runCatching {
            root.resources.getIdentifier(
                BaiduFilePageHookPoints.SAFETY_ABILITY_VIEW_ID,
                "id",
                root.context.packageName,
            )
        }.getOrDefault(0)
        if (safetyId != 0 && root.findViewById<View>(safetyId) != null) return true
        if (root !is ViewGroup) return false
        return (0 until root.childCount).any { index -> isSafetyFooter(root.getChildAt(index)) }
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isFilePageCustomizeEnabled &&
            HookSettings.isFilePageBottomSafetyTipHidden
    }
}
