package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlAlbumBackupBarHookPoints

/**
 * 国际版相册备份栏屏蔽（渲染入口版）。
 *
 * 国际版 13.11.9 R8 全局剥离 @Metadata，共享数据层
 * [com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AlbumBackupBarBlockHook]
 * 依赖的 AddUseCase（`w7.__`）无静态存活锚点（形状撞 103 个候选、无字符串锚点），
 * 在 intl 必然 double-failure。故国际版单独实现，走渲染入口。
 *
 * [IntlAlbumBackupBarFactoryDexKitResolver] 定位浮动栏工厂里实例化 AlbumBackupBarView
 * 的方法（工厂实现 IFloatingBarFactory + 方法体 new AlbumBackupBarView，intl 全 APK 唯一）。
 * afterHook 中若 `result is AlbumBackupBarView` 则返回 null——精确只拦截备份栏，同工厂的
 * garbage / probationary / select_size 栏（同方法不同分支的返回值）不受影响。
 *
 * 返回 null 被 FloatingBarManager + 消费端（FileListChildFragment）完美接住：仅打 log、
 * 不 addView，无 NPE、无副作用、无 View 树扫描。国内/三星走共享数据层路径，本 hook 仅 intl 启用。
 */
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
