package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.content.Context
import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduFilePageHookPoints

/** 隐藏文件列表的安全提示，保留原生 footer 对象和尺寸，供分类页与快速滚动使用。 */
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
        val safetyField = runCatching {
            XposedCompat.findField(method.declaringClass, BaiduFilePageHookPoints.BOTTOM_SAFETY_FIELD)
        }.getOrNull()
        if (safetyField == null || method.returnType != Void.TYPE ||
            !View::class.java.isAssignableFrom(safetyField.type)
        ) {
            XposedCompat.log("[FilePageCustomizeHook] legacy safety footer fields incompatible")
            return 0
        }
        mod.hook(method).intercept { chain ->
            // 视频、音频和文档页仍会更新安全提示，需保留原生对象和列表条目。
            val result = chain.proceed()
            if (isEnabled()) {
                runCatching {
                    val fragment = chain.thisObject ?: return@runCatching
                    val footer = safetyField.get(fragment) as? View ?: return@runCatching
                    hideSafetyContent(footer)
                }.onFailure {
                    XposedCompat.logW("[FilePageCustomizeHook] legacy safety content hide failed: ${it.javaClass.name}")
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
                val result = chain.proceed()
                val footer = chain.args.firstOrNull() as? View
                if (isEnabled() && footer != null) {
                    hideSafetyContent(footer)
                }
                result
            }
            XposedCompat.logD(
                "[FilePageCustomizeHook] RecyclerView footer hook installed: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        }
        return methods.size
    }

    private fun hideSafetyContent(footer: View) {
        val safety = if (footer.javaClass.name == BaiduFilePageHookPoints.SAFETY_INSTRUCTIONS_VIEW) {
            footer
        } else {
            val id = footer.resources.getIdentifier(
                BaiduFilePageHookPoints.SAFETY_ABILITY_VIEW_ID,
                "id",
                footer.context.packageName,
            )
            if (id == 0) return
            footer.findViewById<View>(id)?.takeIf {
                it.javaClass.name == BaiduFilePageHookPoints.SAFETY_INSTRUCTIONS_VIEW
            } ?: return
        }
        // INVISIBLE 保留 footer 尺寸和位置，同时禁止提示区域接收触摸。
        // 后续文字更新不会恢复提示容器的可见性。
        safety.visibility = View.INVISIBLE
        safety.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isFilePageCustomizeEnabled &&
            HookSettings.isFilePageBottomSafetyTipHidden
    }
}
