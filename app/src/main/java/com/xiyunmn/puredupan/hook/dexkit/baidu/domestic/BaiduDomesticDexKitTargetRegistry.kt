package com.xiyunmn.puredupan.hook.dexkit.baidu.domestic

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetDescriptor
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.DexKitWarmUpTask
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.DownloadOpenWithBindingsResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduChangeSkinDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticDynamicPluginAutoDecisionDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticVideoAdPreloadDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.HomeRecentItemLimitDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search.SearchPageVoiceSearchDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.video.BaiduVideoQualityUnlockDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.video.BaiduVideoSpeedUnlockDexKitResolver

internal object BaiduDomesticDexKitTargetRegistry : DexKitTargetRegistry {
    override val descriptors = listOf(
        DexKitTargetDescriptor(
            id = DownloadOpenWithBindingsResolver.CACHE_ID,
            target = "current download page edit model bindings",
            feature = "下载页用其它应用打开",
            featureKey = FeatureKeys.KEY_DOWNLOAD_OPEN_WITH,
        ),
        DexKitTargetDescriptor(
            id = BaiduChangeSkinDexKitResolver.CACHE_ID,
            target = "domestic changeSkin method",
            feature = "follow system night mode",
            featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
        ),
        DexKitTargetDescriptor(
            id = DomesticVideoAdPreloadDexKitResolver.CACHE_ID,
            target = "domestic video front ad download method",
            feature = "video ad preload block",
            featureKey = FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD,
        ),
        DexKitTargetDescriptor(
            id = DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_INSTALL_DECISION_CACHE_ID,
            target = "domestic dynamic plugin auto-install decision",
            feature = "dynamic plugin auto download block",
            featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
        ),
        DexKitTargetDescriptor(
            id = SearchPageVoiceSearchDexKitResolver.CACHE_ID,
            target = "domestic search page voice search screen",
            feature = "search page voice search",
            featureKey = FeatureKeys.KEY_HIDE_SEARCH_PAGE_VOICE_SEARCH,
        ),
        DexKitTargetDescriptor(
            id = BaiduVideoSpeedUnlockDexKitResolver.ONLINE_ENABLE_CACHE_ID,
            target = "video speed online enable method",
            feature = "unlock video speed",
            featureKey = FeatureKeys.KEY_UNLOCK_VIDEO_SPEED,
        ),
        DexKitTargetDescriptor(
            id = BaiduVideoSpeedUnlockDexKitResolver.HAS_PRIVILEGE_CACHE_ID,
            target = "video speed privilege method",
            feature = "unlock video speed",
            featureKey = FeatureKeys.KEY_UNLOCK_VIDEO_SPEED,
        ),
        DexKitTargetDescriptor(
            id = BaiduVideoQualityUnlockDexKitResolver.VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID,
            target = "video quality privilege methods",
            feature = "unlock video quality",
            featureKey = FeatureKeys.KEY_UNLOCK_VIDEO_QUALITY,
        ),
    ) + listOf(
        DexKitTargetDescriptor(
            id = HomeRecentItemLimitDexKitResolver.STATUS_CACHE_ID,
            target = "home recent item limit method group",
            feature = "home recent item limit",
            featureKey = FeatureKeys.KEY_HOME_RECENT_ITEM_LIMIT,
        ),
    )

    override fun buildTasks(
        host: DexKitHostContext,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ): List<DexKitWarmUpTask> {
        val tasks = mutableListOf<DexKitWarmUpTask>()
        fun available(featureKey: String): Boolean = host.isFeatureAvailable(featureKey)

        if (available(FeatureKeys.KEY_DOWNLOAD_OPEN_WITH)) {
            tasks += DexKitWarmUpTask(DownloadOpenWithBindingsResolver.CACHE_ID, DownloadOpenWithBindingsResolver.cacheIds) {
                DownloadOpenWithBindingsResolver.resolve(classLoader) != null
            }
        }

        if (available(FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE)) {
            tasks += DexKitWarmUpTask(BaiduChangeSkinDexKitResolver.CACHE_ID) {
                BaiduChangeSkinDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD)) {
            tasks += DexKitWarmUpTask(DomesticVideoAdPreloadDexKitResolver.CACHE_ID) {
                DomesticVideoAdPreloadDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD)) {
            tasks += DexKitWarmUpTask(
                DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_INSTALL_DECISION_CACHE_ID,
            ) {
                DomesticDynamicPluginAutoDecisionDexKitResolver.warmUpAutoInstallDecisionCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_HIDE_SEARCH_PAGE_VOICE_SEARCH)) {
            tasks += DexKitWarmUpTask(SearchPageVoiceSearchDexKitResolver.CACHE_ID) {
                SearchPageVoiceSearchDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_HOME_RECENT_ITEM_LIMIT)) {
            tasks += DexKitWarmUpTask(
                id = HomeRecentItemLimitDexKitResolver.STATUS_CACHE_ID,
                cacheIds = HomeRecentItemLimitDexKitResolver.cacheIds +
                    HomeRecentItemLimitDexKitResolver.STATUS_CACHE_ID,
            ) {
                HomeRecentItemLimitDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_UNLOCK_VIDEO_SPEED)) {
            tasks += DexKitWarmUpTask(BaiduVideoSpeedUnlockDexKitResolver.ONLINE_ENABLE_CACHE_ID) {
                BaiduVideoSpeedUnlockDexKitResolver.resolveIsSpeedUpOnlineEnable(classLoader) != null
            }
            tasks += DexKitWarmUpTask(BaiduVideoSpeedUnlockDexKitResolver.HAS_PRIVILEGE_CACHE_ID) {
                BaiduVideoSpeedUnlockDexKitResolver.resolveHasSpeedPrivilege(classLoader) != null
            }
        }
        if (available(FeatureKeys.KEY_UNLOCK_VIDEO_QUALITY)) {
            tasks += DexKitWarmUpTask(BaiduVideoQualityUnlockDexKitResolver.VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID) {
                BaiduVideoQualityUnlockDexKitResolver.resolveVideoPrivilegeQualityMethods(classLoader).isNotEmpty()
            }
        }
        return tasks
    }
}
