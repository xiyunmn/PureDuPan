package com.xiyunmn.puredupan.hook.storage

/** 表示下载目标解析结果；不支持或无效目标均作为失败返回。 */
sealed class StorageDestination {
    data object Disabled : StorageDestination()
    data class ReadyDocumentUri(val uri: String) : StorageDestination()
    data class ReadyMediaStoreRelativePath(val relativePath: String) : StorageDestination()
    data class ReadyLegacyAbsolutePath(val absolutePath: String) : StorageDestination()
    data class Unsupported(val reason: String) : StorageDestination()
    data class Invalid(val reason: String) : StorageDestination()
}
