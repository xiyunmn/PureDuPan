package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduFilePageHookPoints
import java.util.Collections
import java.util.WeakHashMap

internal object FilePageCustomizeHook {
    private const val SAFETY_ABILITY_LAYOUT_ID = "safe_ability_layout"

    private val hookState = HookState()
    private val attachedRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[FilePageCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val clazz = XposedCompat.findClassOrNull(
                BaiduFilePageHookPoints.FILE_LIST_CHILD_FRAGMENT,
                cl,
            ) ?: run {
                XposedCompat.log("[FilePageCustomizeHook] FileListChildFragment class NOT FOUND")
                hookState.reset()
                return
            }

            var installed = 0
            XposedCompat.findMethodOrNull(
                clazz,
                "onViewCreated",
                View::class.java,
                Bundle::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    attachFilePageWatcher(chain.args.firstOrNull() as? View)
                    result
                }
                installed++
            } ?: XposedCompat.log("[FilePageCustomizeHook] onViewCreated(View, Bundle) NOT FOUND")

            XposedCompat.findMethodOrNull(clazz, "onResume")?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    attachFilePageWatcher(getFragmentRootView(chain.thisObject))
                    result
                }
                installed++
            } ?: XposedCompat.logD("[FilePageCustomizeHook] onResume not found for view cleanup")

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[FilePageCustomizeHook] no hooks installed")
                return
            }

            XposedCompat.log("[FilePageCustomizeHook] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[FilePageCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun attachFilePageWatcher(root: View?) {
        if (root == null || !isEnabled()) return
        scheduleApplyFilePageCustomize(root)
        if (!attachedRoots.add(root)) return

        root.viewTreeObserver.addOnGlobalLayoutListener {
            runCatching { applyFilePageCustomize(root) }
        }
        root.viewTreeObserver.addOnPreDrawListener {
            runCatching { applyFilePageCustomize(root) }
            true
        }
    }

    private fun scheduleApplyFilePageCustomize(root: View) {
        runCatching { applyFilePageCustomize(root) }
        root.post { runCatching { applyFilePageCustomize(root) } }
        for (delay in listOf(80L, 240L, 600L, 1200L)) {
            root.postDelayed({ runCatching { applyFilePageCustomize(root) } }, delay)
        }
    }

    private fun applyFilePageCustomize(root: View) {
        if (!isEnabled()) return
        if (HookSettings.isFilePageBottomSafetyTipHidden) {
            hideBottomSafetyTips(root)
        }
    }

    private fun hideBottomSafetyTips(root: View) {
        val packageName = root.context?.packageName ?: return
        val safetyTips = findViewsByEntryName(root, SAFETY_ABILITY_LAYOUT_ID, packageName)
        for (safetyTip in safetyTips) {
            val container = findSafetyTipContainer(safetyTip)
            if (collapseView(container)) {
                XposedCompat.logD("[FilePageCustomizeHook] bottom safety tip hidden")
            }
        }
    }

    private fun findSafetyTipContainer(safetyTip: View): View {
        val parent = safetyTip.parent as? View
        if (parent is ViewGroup && parent.childCount <= 3) {
            return parent
        }
        return safetyTip
    }

    private fun findViewsByEntryName(
        root: View,
        idName: String,
        packageName: String,
    ): List<View> {
        val id = root.resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return emptyList()
        val results = mutableListOf<View>()
        collectViewsById(root, id, results)
        return results
    }

    private fun collectViewsById(
        view: View,
        id: Int,
        results: MutableList<View>,
    ) {
        if (view.id == id) {
            results += view
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            collectViewsById(group.getChildAt(index), id, results)
        }
    }

    private fun collapseView(view: View): Boolean {
        if (
            view.visibility == View.GONE &&
            view.alpha == 0f &&
            view.minimumHeight == 0 &&
            view.layoutParams?.height == 0
        ) {
            return false
        }

        view.animate()?.cancel()
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        view.minimumHeight = 0
        view.setPadding(0, 0, 0, 0)

        val params = view.layoutParams
        if (params != null) {
            params.height = 0
            if (params is ViewGroup.MarginLayoutParams) {
                params.setMargins(0, 0, 0, 0)
            }
            if (params is LinearLayout.LayoutParams) {
                params.weight = 0f
            }
            view.layoutParams = params
        }
        (view.parent as? ViewGroup)?.requestLayout()
        view.requestLayout()
        return true
    }

    private fun getFragmentRootView(fragment: Any?): View? {
        return runCatching {
            fragment?.javaClass?.methods?.firstOrNull { method ->
                method.name == "getView" && method.parameterTypes.isEmpty()
            }?.invoke(fragment) as? View
        }.getOrNull()
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isFilePageCustomizeEnabled &&
            HookSettings.isFilePageBottomSafetyTipHidden
    }
}
