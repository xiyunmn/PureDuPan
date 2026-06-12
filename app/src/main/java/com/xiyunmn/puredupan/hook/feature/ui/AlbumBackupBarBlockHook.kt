package com.xiyunmn.puredupan.hook.feature.ui

import android.view.View
import android.view.ViewGroup
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.StableBaiduPanHookPoints
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState

/**
 * Hides the file-tab album backup guide bar without breaking FloatingBarManager.
 *
 * The host creates this through FileTabBottomBarFactory.create(...). Returning null risks
 * breaking downstream manager code, so we keep the host object flow and collapse only the
 * concrete AlbumBackupBarView / its inner backup layouts.
 */
object AlbumBackupBarBlockHook {
    private val hookState = HookState()

    private val targetViewIds = setOf("album_backup_layout", "backup_layout")

    internal fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAlbumBackupBarBlocked) {
            XposedCompat.log("[AlbumBackupBarBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installed = 0

            XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.FILE_TAB_BOTTOM_BAR_FACTORY,
                cl,
            )?.let { factoryClass ->
                for (method in factoryClass.declaredMethods) {
                    if (method.name != StableBaiduPanHookPoints.FILE_TAB_BOTTOM_BAR_FACTORY_CREATE_METHOD) {
                        continue
                    }
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (ConfigManager.isAlbumBackupBarBlocked) {
                            collapseAlbumBackupViews(result)
                        }
                        result
                    }
                    installed++
                }
            } ?: XposedCompat.log("[AlbumBackupBarBlockHook] FileTabBottomBarFactory class NOT FOUND")

            XposedCompat.findClassOrNull(
                StableBaiduPanHookPoints.ALBUM_BACKUP_BAR_VIEW,
                cl,
            )?.let { barClass ->
                for (constructor in barClass.declaredConstructors) {
                    constructor.isAccessible = true
                    mod.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        if (ConfigManager.isAlbumBackupBarBlocked) {
                            collapseAlbumBackupViews(chain.thisObject)
                        }
                        result
                    }
                    installed++
                }

                for (method in barClass.declaredMethods) {
                    if (method.name != StableBaiduPanHookPoints.ALBUM_BACKUP_BAR_VIEW_INIT_UI_METHOD) {
                        continue
                    }
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (ConfigManager.isAlbumBackupBarBlocked) {
                            collapseAlbumBackupViews(chain.thisObject)
                        }
                        result
                    }
                    installed++
                }
            } ?: XposedCompat.log("[AlbumBackupBarBlockHook] AlbumBackupBarView class NOT FOUND")

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[AlbumBackupBarBlockHook] no hooks installed")
                return
            }

            XposedCompat.log("[AlbumBackupBarBlockHook] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[AlbumBackupBarBlockHook] FAILED: ${e.message}")
        }
    }

    private fun collapseAlbumBackupViews(candidate: Any?) {
        val view = candidate as? View ?: return
        if (isAlbumBackupTarget(view)) {
            collapseView(view)
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collapseAlbumBackupViews(view.getChildAt(index))
            }
        }
    }

    private fun isAlbumBackupTarget(view: View): Boolean {
        if (view.javaClass.name == StableBaiduPanHookPoints.ALBUM_BACKUP_BAR_VIEW) {
            return true
        }
        val id = view.id
        if (id == View.NO_ID) return false
        return try {
            view.resources.getResourceEntryName(id) in targetViewIds
        } catch (_: Throwable) {
            false
        }
    }

    private fun collapseView(view: View) {
        try {
            view.visibility = View.GONE
            view.alpha = 0f
            view.minimumHeight = 0
            view.setPadding(0, 0, 0, 0)
            val lp = view.layoutParams
            if (lp != null) {
                lp.height = 0
                view.layoutParams = lp
            }
            view.requestLayout()
            XposedCompat.logD("[AlbumBackupBarBlockHook] album backup bar collapsed")
        } catch (e: Exception) {
            XposedCompat.logW("[AlbumBackupBarBlockHook] collapse failed: ${e.message}")
        }
    }

}
