package com.xiyunmn.puredupan.hook.host

import com.xiyunmn.puredupan.hook.core.Constants

internal object HostRegistry {
    private val baiduCnProfile = HostProfile(
        flavor = HostFlavor.BAIDU_CN,
        packageName = Constants.BAIDU_NETDISK_PACKAGE,
        applicationClassName = "com.baidu.netdisk.ShellApplication",
        handledProcessNames = setOf(
            Constants.BAIDU_NETDISK_PACKAGE,
            "${Constants.BAIDU_NETDISK_PACKAGE}:pushservice",
        ),
        attachHookProcessNames = setOf(
            Constants.BAIDU_NETDISK_PACKAGE,
            "${Constants.BAIDU_NETDISK_PACKAGE}:pushservice",
        ),
        capabilities = HostCapabilities(
            supportsOemPushHook = true,
            supportsStandaloneHotStartSplashRemove = false,
        ),
    )

    private val baiduIntlProfile = HostProfile(
        flavor = HostFlavor.BAIDU_INTL,
        packageName = Constants.BAIDU_DRIVE_INTL_PACKAGE,
        applicationClassName = "com.baidu.netdisk.ShellApplication",
        handledProcessNames = setOf(Constants.BAIDU_DRIVE_INTL_PACKAGE),
        attachHookProcessNames = setOf(Constants.BAIDU_DRIVE_INTL_PACKAGE),
        capabilities = HostCapabilities(
            supportsOemPushHook = false,
            supportsHotStartSplashAd = true,
            supportsStandaloneHotStartSplashRemove = true,
            supportsLaunchHandoffOptimize = true,
            supportsUpdateDialogBlock = false,
            supportsSvipIconGuideBlock = false,
            supportsSharePushGuideBlock = false,
            supportsBottomAiTabReplace = false,
            supportsHomeCustomize = true,
            supportsHomeUploadEntry = true,
            supportsGarbageCleanServiceOptimize = false,
            supportsDatapackSocketOptimize = false,
            supportsAigcBackgroundOptimize = false,
            supportsDynamicPluginAutoDownloadBlock = false,
            supportsVideoAdPreloadBlock = false,
            supportsAdSdkInitBlock = false,
            supportsSwanPreloadBlock = false,
            supportsThumbnailOperatorServiceBlock = false,
            supportsIncentiveBusinessServiceBlock = false,
            supportsAudioCircleAutostartBlock = false,
            supportsIconResourceDownloadBlock = false,
            supportsB2fGuidancePrefetchBlock = false,
            supportsIntlOfflinePackageSyncDelay = true,
            supportsIntlFeedPreloadDelay = true,
            supportsMemberCardCustomize = true,
        ),
    )

    private val profiles = listOf(
        baiduCnProfile,
        baiduIntlProfile,
    )

    val supportedPackageNames: Set<String> = profiles.mapTo(linkedSetOf()) { it.packageName }

    fun resolveByPackageName(packageName: String): HostProfile? {
        return profiles.firstOrNull { it.handlesPackage(packageName) }
    }

    fun requireByPackageName(packageName: String): HostProfile {
        return resolveByPackageName(packageName) ?: baiduCnProfile
    }
}
