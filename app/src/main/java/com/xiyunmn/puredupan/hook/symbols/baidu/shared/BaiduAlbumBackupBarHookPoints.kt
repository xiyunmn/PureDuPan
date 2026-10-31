package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduAlbumBackupBarHookPoints {
    /** 国内系相册备份栏数据入口；拦截 realExecute 可阻止写入 bottomBars。 */
    const val ALBUM_BACKUP_BAR_ADD_USE_CASE =
        "com.baidu.netdisk.allfiles.listfragment.extraview.floatingbar.AlbumBackupBarAddUseCase"

    /** 用于校验参数类型的文件列表视图模型接口。 */
    const val FILE_LIST_VIEW_MODEL =
        "com.baidu.netdisk.filelist.IFileListViewModel"

}
