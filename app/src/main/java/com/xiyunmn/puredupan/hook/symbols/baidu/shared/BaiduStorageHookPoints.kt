package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduStorageHookPoints {
    const val DOMESTIC_DOWNLOAD_EXTENSION = "com.baidu.netdisk.util.DownloadExtensionKt"
    const val DOMESTIC_TARGET30_STORAGE = "com.baidu.netdisk.partition.Target30StorageKt"
    const val INTL_DOWNLOAD_EXTENSION = "ry0.r"
    const val INTL_TARGET30_STORAGE = "com.baidu.netdisk.partition.q"
    const val DEFAULT_SETTING = "com.baidu.netdisk.base.storage.config.Setting"
    const val DEFAULT_SETTING_INTL = "com.baidu.netdisk.base.storage.config.r"
    const val URI_CREATOR = "createDownloadUriStr"
    const val INTL_URI_CREATOR = "m19027_"
    const val QUERY_ABSOLUTE_PATH = "queryAbsolutePathByDownloadUri"
    const val QUERY_PATH = "queryPathByDownloadUri"
    const val INTL_GET_PARTITION_LOCAL_PATH = "a"
    const val INTL_QUERY_ABSOLUTE_PATH = "i"
    const val INTL_QUERY_PATH = "k"
    const val DEFAULT_SAVE_DIR = "getDefaultSaveDir"
    const val FOLDER_TASK_STATE_UTIL = "com.baidu.netdisk.transfer.util.DownloadFolderTaskStateUtilKt"
    const val ADD_DOWNLOAD_FOLDER_TASK = "addDownloadFolderTask"
}
