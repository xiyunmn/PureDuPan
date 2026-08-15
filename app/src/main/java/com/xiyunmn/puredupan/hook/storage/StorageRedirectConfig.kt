package com.xiyunmn.puredupan.hook.storage

/** Host-scoped storage preferences. Every flag is deliberately opt-in. */
data class StorageRedirectConfig(
    val enabled: Boolean = false,
    val downloadRedirectEnabled: Boolean = false,
    val downloadTreeUri: String? = null,
    val removeOuterPathEnabled: Boolean = false,
    val rootGuardEnabled: Boolean = false,
    val wechatBackupRedirectEnabled: Boolean = false,
    val readerSdkRedirectEnabled: Boolean = false,
)

data class StorageRedirectSnapshot(
    val hostPackageName: String,
    val config: StorageRedirectConfig,
) {
    val enabled: Boolean get() = config.enabled
    val downloadRedirectEnabled: Boolean get() = config.downloadRedirectEnabled
    val downloadTreeUri: String? get() = config.downloadTreeUri
    val removeOuterPathEnabled: Boolean get() = config.removeOuterPathEnabled
    val rootGuardEnabled: Boolean get() = config.rootGuardEnabled
}
