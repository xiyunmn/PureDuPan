package com.xiyunmn.puredupan.hook.symbols.baidu.intl

/**
 * 国际版相册备份栏「渲染入口」落点（明文，跨版本稳定）。
 *
 * 国际版 13.11.9 R8 全局剥离 @Metadata，数据层 AddUseCase（`w7.__`，形状
 * `boolean(IFileListViewModel,Map)`）与全 APK 103 个同形状候选撞形、无字符串锚点，
 * 静态无法唯一定位。改走渲染入口：
 *
 * 备份栏 View（[ALBUM_BACKUP_BAR_VIEW]，`@Tag("AlbumBackupBarView")` 明文类）全 APK
 * 唯一实例化点是浮动栏工厂 `w7.h`（实现明文接口 [FLOATING_BAR_FACTORY_INTERFACE]）的
 * `"albumbackup_bar"` 分支——`new AlbumBackupBarView(activity, ...)`。DexKit 以
 * 「实现 IFloatingBarFactory 且方法体 invoke AlbumBackupBarView.<init>」定位该工厂方法
 * （intl 全 APK 唯一），afterHook 中 `result is AlbumBackupBarView → 返回 null`，精确
 * 只拦截备份栏，同工厂的 garbage / probationary / select_size 栏不受影响。
 *
 * 工厂返回 null 被 `FloatingBarManager` + 消费端完美接住（仅打 log，不 addView），
 * 无副作用、无 View 树扫描。三端（国内 13.27.8 / 三星 13.27.8 / 国际 13.11.9）
 * AlbumBackupBarView 与 IFloatingBarFactory 均明文；国内/三星保留 @Metadata 仍走
 * 共享数据层 AddUseCase 路径，本入口仅国际版启用。
 */
internal object BaiduIntlAlbumBackupBarHookPoints {
    const val ALBUM_BACKUP_BAR_VIEW =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.AlbumBackupBarView"

    const val FLOATING_BAR_FACTORY_INTERFACE =
        "com.baidu.netdisk.filelist.extraview.IFloatingBarFactory"

    const val FLOATING_BAR_VIEW_INTERFACE =
        "com.baidu.netdisk.filelist.extraview.IFloatingBarView"

    const val CONSTRUCTOR_METHOD_NAME = "<init>"
}
