package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduStorageHookPoints {
    const val DOMESTIC_DOWNLOAD_EXTENSION = "com.baidu.netdisk.util.DownloadExtensionKt"
    const val DOMESTIC_TARGET30_STORAGE = "com.baidu.netdisk.partition.Target30StorageKt"
    const val DEFAULT_SETTING = "com.baidu.netdisk.base.storage.config.Setting"
    const val URI_CREATOR = "createDownloadUriStr"
    const val DOWNLOADABLE = "com.baidu.netdisk.transfer.base.IDownloadable"
    const val FILE_INFO_GENERATOR = "com.baidu.netdisk.transfer.base.IFileInfoGenerator"
    const val QUERY_ABSOLUTE_PATH = "queryAbsolutePathByDownloadUri"
    const val QUERY_PATH = "queryPathByDownloadUri"
    const val DEFAULT_SAVE_DIR = "getDefaultSaveDir"
    const val FOLDER_TASK_STATE_UTIL = "com.baidu.netdisk.transfer.util.DownloadFolderTaskStateUtilKt"
    const val ADD_DOWNLOAD_FOLDER_TASK = "addDownloadFolderTask"
    const val SDK_FILE_UTILS = "com.baidu.android.util.io.FileUtils"
    const val SDK_PUBLIC_DIRECTORY = "getPublicExternalDiretory"
}
