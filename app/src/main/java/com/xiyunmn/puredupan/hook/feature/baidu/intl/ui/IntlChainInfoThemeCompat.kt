package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.R
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import com.xiyunmn.puredupan.hook.ui.HostThemeChangeDispatcher
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Repairs the share-link file-row background that is absent from the intl dark skin package.
 *
 * ChainInfoAdapter inflates chain_item_filelist with bg_chain_file_list_item. The bundled dark
 * skin defines the three colors used by that selector but not the selector itself, so Android
 * keeps the host's light drawable while the row text changes to dark-skin colors.
 */
internal object IntlChainInfoThemeCompat {
    private const val TAG = "IntlChainInfoThemeCompat"
    private const val ITEM_VIEW_FIELD = "itemView"

    private val hookState = HookState()
    private val rowRefs = mutableListOf<WeakReference<View>>()

    internal fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val adapterClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.CHAIN_INFO_ADAPTER, cl)
                ?: run {
                    hookState.reset()
                    XposedCompat.log("[$TAG] ChainInfoAdapter class NOT FOUND")
                    return
                }
            val createViewHolder = XposedCompat.findMethodOrNull(
                adapterClass,
                BaiduIntlHookPoints.CHAIN_INFO_CREATE_VIEW_HOLDER_METHOD,
                ViewGroup::class.java,
                Integer.TYPE,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] ChainInfoAdapter.onCreateViewHolder NOT FOUND")
                return
            }
            val skinAccess = resolveSkinAccess(cl)
                ?: run {
                    hookState.reset()
                    XposedCompat.log("[$TAG] SkinManager.getColor NOT FOUND")
                    return
                }

            mod.hook(createViewHolder).intercept { chain ->
                val holder = chain.proceed()
                runCatching {
                    holderItemView(holder)?.let { itemView ->
                        if (isChainInfoFileRow(itemView)) {
                            rememberRow(itemView)
                            applyBackground(itemView, skinAccess)
                        }
                    }
                }.onFailure { t ->
                    XposedCompat.logD("[$TAG] file-row post-process failed: ${t.message}")
                }
                holder
            }
            HostThemeChangeDispatcher.register { reason ->
                refreshRows(skinAccess, reason)
            }
            XposedCompat.log("[$TAG] hook INSTALLED: ChainInfoAdapter.onCreateViewHolder")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[$TAG] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun resolveSkinAccess(cl: ClassLoader): SkinAccess? {
        val skinManagerClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_MANAGER, cl)
            ?: return null
        val getInstance = XposedCompat.findMethodOrNull(skinManagerClass, "getInstance")
            ?: return null
        val getColor = XposedCompat.findMethodOrNull(skinManagerClass, "getColor", Integer.TYPE)
            ?: return null
        val manager = getInstance.invoke(null) ?: return null
        return SkinAccess(manager, getColor)
    }

    private fun holderItemView(holder: Any?): View? {
        holder ?: return null
        return runCatching {
            holder.javaClass.getField(ITEM_VIEW_FIELD).get(holder) as? View
        }.getOrNull()
    }

    private fun isChainInfoFileRow(view: View): Boolean {
        if (view.id == View.NO_ID) return false
        return runCatching {
            view.resources.getResourceEntryName(view.id) ==
                BaiduIntlHookPoints.CHAIN_INFO_FILE_ROW_ID_NAME
        }.getOrDefault(false)
    }

    private fun rememberRow(view: View) {
        synchronized(rowRefs) {
            rowRefs.removeAll { it.get() == null }
            if (rowRefs.none { it.get() === view }) {
                rowRefs += WeakReference(view)
            }
        }
    }

    private fun refreshRows(skinAccess: SkinAccess, reason: String) {
        val rows = synchronized(rowRefs) {
            rowRefs.removeAll { it.get() == null }
            rowRefs.mapNotNull { it.get() }
        }
        rows.forEach { row ->
            runCatching { applyBackground(row, skinAccess) }
                .onFailure { t ->
                    XposedCompat.logD("[$TAG] file-row refresh failed: ${t.message}")
                }
        }
        if (rows.isNotEmpty()) {
            XposedCompat.logD("[$TAG] refreshed ${rows.size} file rows: $reason")
        }
    }

    private fun applyBackground(view: View, skinAccess: SkinAccess) {
        val normal = resolveSkinColor(
            view,
            skinAccess,
            BaiduIntlHookPoints.CHAIN_INFO_FILE_ROW_NORMAL_COLOR_NAME,
        ) ?: return
        val checked = resolveSkinColor(
            view,
            skinAccess,
            BaiduIntlHookPoints.CHAIN_INFO_FILE_ROW_CHECKED_COLOR_NAME,
        ) ?: return
        val pressed = resolveSkinColor(
            view,
            skinAccess,
            BaiduIntlHookPoints.CHAIN_INFO_FILE_ROW_PRESSED_COLOR_NAME,
        ) ?: return

        view.background = StateListDrawable().apply {
            addState(
                intArrayOf(R.attr.state_enabled, -R.attr.state_checked, R.attr.state_pressed),
                ColorDrawable(pressed),
            )
            addState(
                intArrayOf(R.attr.state_focused, R.attr.state_enabled, -R.attr.state_checked),
                ColorDrawable(pressed),
            )
            addState(
                intArrayOf(R.attr.state_enabled, R.attr.state_checked),
                ColorDrawable(checked),
            )
            addState(
                intArrayOf(R.attr.state_enabled, -R.attr.state_pressed),
                ColorDrawable(normal),
            )
            addState(
                intArrayOf(-R.attr.state_focused, R.attr.state_enabled),
                ColorDrawable(normal),
            )
            addState(intArrayOf(-R.attr.state_enabled), ColorDrawable(pressed))
        }
        view.refreshDrawableState()
        view.invalidate()
    }

    private fun resolveSkinColor(
        view: View,
        skinAccess: SkinAccess,
        resourceName: String,
    ): Int? {
        val resourceId = view.resources.getIdentifier(resourceName, "color", view.context.packageName)
        if (resourceId == 0) {
            XposedCompat.logD("[$TAG] color resource NOT FOUND: $resourceName")
            return null
        }
        return runCatching {
            skinAccess.getColor.invoke(skinAccess.manager, resourceId) as Int
        }.getOrElse { t ->
            XposedCompat.logD("[$TAG] resolve color failed ($resourceName): ${t.message}")
            null
        }
    }

    private data class SkinAccess(
        val manager: Any,
        val getColor: Method,
    )
}
