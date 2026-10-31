package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlAlbumBackupBarHookPoints

/** 在国际版浮动栏工厂返回时过滤相册备份栏，保留同工厂创建的其它栏位。宿主按空结果跳过视图添加。 */
internal object IntlAlbumBackupBarBlockHook {
    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!HookSettings.isAlbumBackupBarBlocked) {
            XposedCompat.log("[IntlAlbumBackupBarBlockHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            val method = IntlAlbumBackupBarFactoryDexKitResolver.resolve(cl) ?: run {
                hookState.reset()
                XposedCompat.log("[IntlAlbumBackupBarBlockHook] factory method NOT RESOLVED")
                return
            }

            val backupBarViewClass = XposedCompat.findClassOrNull(
                BaiduIntlAlbumBackupBarHookPoints.ALBUM_BACKUP_BAR_VIEW,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[IntlAlbumBackupBarBlockHook] AlbumBackupBarView class NOT FOUND")
                return
            }

            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isAlbumBackupBarBlocked && backupBarViewClass.isInstance(result)) {
                    XposedCompat.logD("[IntlAlbumBackupBarBlockHook] album backup bar view suppressed")
                    null
                } else {
                    result
                }
            }

            XposedCompat.log(
                "[IntlAlbumBackupBarBlockHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name}",
            )
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[IntlAlbumBackupBarBlockHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }
}
