package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduFilePageHookPoints

/**
 * 文件页定制 Hook。
 *
 * 底部安全提示走三条互补路径：
 * 1. 数据层：hook ShowSafetyFooterUseCase.realExecute(...) 返回 false，阻止新
 *    文件页把 showSafetyBottomView 置为 true。国内版新路径有效；国际版无此 UseCase。
 * 2. 旧 ListView：保留 MyNetdiskFragment.initSafetyBottomView(Context) 的原生初始化，
 *    返回前仅从所属列表移除 mBottomSafety footer。分类页仍会访问该对象，不能跳过创建
 *    或清空字段；原生后续修改其可见性也不会使已移除的 footer 重新进入列表。
 * 3. RecyclerView v2：国际版主文件页实际使用 FileListFragment / FileListChildFragment，
 *    两者直接向 FileListRecyclerView 添加 safety_ability_layout footer，完全绕过旧入口。
 *    在 addFooterView(View) 参数边界识别 SafetyInstructionsView 后跳过添加。
 * 移除提示后，旧列表初始化及 RecyclerView 绑定 adapter 时保留底部滚动留白，避免末项贴住导航栏。
 *
 * 已删除旧 View 树路径：FileListChildFragment 根节点的 OnGlobalLayoutListener /
 * OnPreDrawListener / postDelayed 循环，以及 safe_ability_layout 资源 ID 全树递归。
 */
internal object FilePageCustomizeHook {

    // 原生 safety_ability_layout 的提示外留白为上方 18dp + 下方 14dp。
    private const val BOTTOM_SCROLL_SPACE_DP = 32
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
            installed += hookLegacySafetyFooter(cl)
            installed += hookSafetyRecyclerView(cl)
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

    private fun hookLegacySafetyFooter(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val method = XposedCompat.findMethodOrNull(
            BaiduFilePageHookPoints.MY_NETDISK_FRAGMENT,
            cl,
            BaiduFilePageHookPoints.INIT_SAFETY_BOTTOM_VIEW_METHOD,
            Context::class.java,
        ) ?: run {
            XposedCompat.log("[FilePageCustomizeHook] initSafetyBottomView NOT FOUND")
            return 0
        }
        val fields = runCatching {
            XposedCompat.findField(method.declaringClass, BaiduFilePageHookPoints.BOTTOM_SAFETY_FIELD) to
                XposedCompat.findField(method.declaringClass, BaiduFilePageHookPoints.LIST_VIEW_FIELD)
        }.getOrNull()
        if (fields == null || method.returnType != Void.TYPE ||
            !View::class.java.isAssignableFrom(fields.first.type) ||
            !ListView::class.java.isAssignableFrom(fields.second.type)
        ) {
            XposedCompat.log("[FilePageCustomizeHook] legacy safety footer fields incompatible")
            return 0
        }
        val (safetyField, listField) = fields

        mod.hook(method).intercept { chain ->
            // VideoAllFragment 在 super 返回后直接访问 mBottomSafety；音频/文档页也会
            // 在空列表和筛选状态更新时访问它。只移除列表条目，保留原生对象及初始化副作用。
            val result = chain.proceed()
            if (isEnabled()) {
                runCatching {
                    val fragment = chain.thisObject ?: return@runCatching
                    val footer = safetyField.get(fragment) as? View ?: return@runCatching
                    val list = listField.get(fragment) as? ListView ?: return@runCatching
                    list.removeFooterView(footer)
                    ensureBottomScrollSpace(list)
                }.onFailure {
                    XposedCompat.logW("[FilePageCustomizeHook] legacy safety footer removal failed: ${it.javaClass.name}")
                }
            }
            result
        }
        XposedCompat.logD(
            "[FilePageCustomizeHook] legacy safety footer hook installed: " +
                "${method.declaringClass.name}.${method.name}",
        )
        return 1
    }

    private fun hookSafetyRecyclerView(cl: ClassLoader): Int {
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
        // 国内新文件页的数据层已阻止 footer 创建，不能只在 addFooterView 里补间距。
        // 三个宿主均在此专用列表类中声明 setAdapter，避开全局 RecyclerView/View Hook。
        val adapterMethods = recyclerClass.declaredMethods.filter { method ->
            method.name == "setAdapter" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == BaiduFilePageHookPoints.RECYCLER_VIEW_ADAPTER &&
                method.returnType == Void.TYPE
        }
        adapterMethods.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isEnabled()) {
                    (chain.thisObject as? ViewGroup)?.let(::ensureBottomScrollSpace)
                }
                result
            }
        }
        return methods.size + adapterMethods.size
    }

    private fun ensureBottomScrollSpace(list: ViewGroup) {
        val minimum = (BOTTOM_SCROLL_SPACE_DP * list.resources.displayMetrics.density + 0.5f).toInt()
        // 取下限而非累加：目录/adapter 重绑不会不断扩大间距，也不会缩小宿主已有的留白。
        if (list.paddingBottom < minimum) {
            if (list.isPaddingRelative) {
                list.setPaddingRelative(list.paddingStart, list.paddingTop, list.paddingEnd, minimum)
            } else {
                list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, minimum)
            }
        }
        // 留白计入原生滚动范围，滚动时内容仍可经过该区域，末项可以完整滚到导航栏上方。
        list.clipToPadding = false
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
