package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat

/**
 * 相册备份栏屏蔽。
 *
 * 已迁移到数据/逻辑层：命中隐藏开关时 hook AlbumBackupBarAddUseCase.realExecute，
 * 短路返回 true 且不把 AlbumBackupBar put 进 viewModel.bottomBars。备份栏根本不进入
 * bottomBars，FloatingBarManager 不会创建 AlbumBackupBarView，无 View 生成。
 *
 * 国内、三星由 [AlbumBackupBarAddUseCaseResolver] 定位；国际版使用独立 Hook。
 *
 * 已删除旧 View 树路径：FileTabBottomBarFactory.create 返回后的 collapseAlbumBackupViews
 * 递归、AlbumBackupBarView 构造器 / initUI 折叠，以及 album_backup_layout /
 * backup_layout 资源 ID 匹配。
 */
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
