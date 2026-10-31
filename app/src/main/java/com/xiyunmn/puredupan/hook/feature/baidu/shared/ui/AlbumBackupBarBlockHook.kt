package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat

/** 阻止国内系宿主添加相册备份栏数据，使对应视图无需创建。 */
internal object AlbumBackupBarBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isAlbumBackupBarBlocked) {
            XposedCompat.log("[AlbumBackupBarBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = AlbumBackupBarAddUseCaseResolver.resolve(cl) ?: run {
                hookState.reset()
                XposedCompat.log("[AlbumBackupBarBlockHook] AlbumBackupBarAddUseCase.realExecute NOT RESOLVED")
                return
            }

            mod.hook(method).intercept { chain ->
                if (HookSettings.isAlbumBackupBarBlocked) {
                    XposedCompat.logD("[AlbumBackupBarBlockHook] album backup bar add blocked")
                    true
                } else {
                    chain.proceed()
                }
            }

            XposedCompat.log(
                "[AlbumBackupBarBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[AlbumBackupBarBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }
}
