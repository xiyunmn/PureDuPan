package com.xiyunmn.puredupan.hook.storage

/** A resolved destination. Unsupported/Invalid are intentionally not fallbacks. */
sealed class StorageDestination {
    data object Disabled : StorageDestination()
    data class ReadyDocumentUri(val uri: String) : StorageDestination()
    data class ReadyMediaStoreRelativePath(val relativePath: String) : StorageDestination()
    data class ReadyLegacyAbsolutePath(val absolutePath: String) : StorageDestination()
    data class Unsupported(val reason: String) : StorageDestination()
    data class Invalid(val reason: String) : StorageDestination()
}
