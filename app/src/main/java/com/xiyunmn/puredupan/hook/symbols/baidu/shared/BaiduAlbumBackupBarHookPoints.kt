package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduAlbumBackupBarHookPoints {
    /**
     * 相册备份栏数据/逻辑层落点。
     *
     * [ALBUM_BACKUP_BAR_ADD_USE_CASE] 的 realExecute 把 AlbumBackupBar 放进
     * viewModel.bottomBars。命中隐藏开关时在该入口短路返回 true 且不 put，
     * 备份栏根本不进入 bottomBars，无 View 生成。
     *
     * 国内系使用固定类内的参数和返回类型定位，国际版使用独立备份栏入口。
     */
    const val ALBUM_BACKUP_BAR_ADD_USE_CASE =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.AlbumBackupBarAddUseCase"

    /** 视图模型 bottomBars 持有者接口，参数形状校验用。 */
    const val FILE_LIST_VIEW_MODEL =
        "com.baidu.netdisk.filelist.IFileListViewModel"

}
