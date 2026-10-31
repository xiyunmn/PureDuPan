package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduStorageHookPoints {
    const val DOMESTIC_DOWNLOAD_EXTENSION = "com.baidu.netdisk.util.DownloadExtensionKt"
    const val DOMESTIC_TARGET30_STORAGE = "com.baidu.netdisk.partition.Target30StorageKt"
    // 国际版下载扩展类，已核对对应 APK 的原始 DEX。
    val INTL_DOWNLOAD_EXTENSIONS = listOf("k01.s", "j01.s")
    const val INTL_TARGET30_STORAGE = "com.baidu.netdisk.partition.q"
    const val DEFAULT_SETTING = "com.baidu.netdisk.base.storage.config.Setting"
    const val DEFAULT_SETTING_INTL = "com.baidu.netdisk.base.storage.config.r"
    const val URI_CREATOR = "createDownloadUriStr"
    const val INTL_URI_CREATOR = "_"
    const val INTL_DOWNLOAD_PATH = "___"
    const val INTL_LINK_DOWNLOAD_PATH = "_____"
    const val DOWNLOADABLE = "com.baidu.netdisk.transfer.base.IDownloadable"
    const val FILE_INFO_GENERATOR = "com.baidu.netdisk.transfer.base.IFileInfoGenerator"
    const val QUERY_ABSOLUTE_PATH = "queryAbsolutePathByDownloadUri"
    const val QUERY_PATH = "queryPathByDownloadUri"
    const val INTL_GET_PARTITION_LOCAL_PATH = "a"
    const val INTL_QUERY_ABSOLUTE_PATH = "i"
    const val INTL_QUERY_PATH = "k"
    const val DEFAULT_SAVE_DIR = "getDefaultSaveDir"
    const val INTL_DEFAULT_SAVE_DIR = "_"
    const val INTL_LEGACY_SAVE_DIR = "__"
    const val FOLDER_TASK_STATE_UTIL = "com.baidu.netdisk.transfer.util.DownloadFolderTaskStateUtilKt"
    const val ADD_DOWNLOAD_FOLDER_TASK = "addDownloadFolderTask"
    val INTL_FOLDER_TASK_BINDINGS = listOf("cq0.____" to "cq0.b", "bq0.____" to "bq0.b")
    const val INTL_ADD_DOWNLOAD_FOLDER_TASK = "____"
    const val INTL_FOLDER_TRANSFER_PARENT = "f"
    const val INTL_FOLDER_NAME = "c"
    const val SDK_FILE_UTILS = "com.baidu.android.util.io.FileUtils"
    const val SDK_PUBLIC_DIRECTORY = "getPublicExternalDiretory"
}
