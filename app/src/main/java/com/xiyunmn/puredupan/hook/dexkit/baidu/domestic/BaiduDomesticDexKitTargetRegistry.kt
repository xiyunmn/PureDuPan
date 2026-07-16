package com.xiyunmn.puredupan.hook.dexkit.baidu.domestic

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetDescriptor
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.DexKitWarmUpTask
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticUpdateDialogDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.BottomAiTabDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.membercard.AboutMeTopHeteromoDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AlbumBackupBarAddUseCaseDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.DomesticChangeSkinDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticDynamicPluginAutoDecisionDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticFloatViewStartupDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticIconResourceDownloadDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticSwanPreloadResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticThumbnailOperatorDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticVideoAdPreloadDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.DomesticCookieByBdussDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.DomesticColdStartSplashDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.DomesticHotStartSplashDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search.SearchPageVoiceSearchDexKitResolver

internal object BaiduDomesticDexKitTargetRegistry : DexKitTargetRegistry {
    override val descriptors = listOf(
        DexKitTargetDescriptor(
            id = DomesticColdStartSplashDexKitResolver.CACHE_ID,
            target = "domestic cold-start splash resolver",
            feature = "splash interstitial block",
            featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
        ),
        DexKitTargetDescriptor(
            id = DomesticHotStartSplashDexKitResolver.CACHE_ID,
            target = "domestic hot-start splash resolver",
            feature = "splash interstitial block",
            featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
        ),
        DexKitTargetDescriptor(
            id = DomesticUpdateDialogDexKitResolver.CACHE_ID,
            target = "domestic update dialog method",
            feature = "update dialog block",
            featureKey = FeatureKeys.KEY_BLOCK_UPDATE_DIALOG,
        ),
        DexKitTargetDescriptor(
            id = BottomAiTabDexKitResolver.CACHE_ID,
            target = "domestic bottom AI tab mode getter",
            feature = "bottom AI tab replacement",
            featureKey = FeatureKeys.KEY_REPLACE_BOTTOM_AI,
        ),
        DexKitTargetDescriptor(
            id = DomesticChangeSkinDexKitResolver.CACHE_ID,
            target = "domestic changeSkin method",
            feature = "follow system night mode",
            featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
        ),
        DexKitTargetDescriptor(
            id = DomesticThumbnailOperatorDexKitResolver.CLIENT_COMPUTE_INIT_CACHE_ID,
            target = "domestic client compute init method",
            feature = "thumbnail operator service block",
            featureKey = FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
        ),
        DexKitTargetDescriptor(
            id = DomesticThumbnailOperatorDexKitResolver.THUMBNAIL_ADD_JOB_CACHE_ID,
            target = "domestic thumbnail add-job method",
            feature = "thumbnail operator service block",
            featureKey = FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
        ),
        DexKitTargetDescriptor(
            id = DomesticFloatViewStartupDexKitResolver.CACHE_ID,
            target = "domestic float-view startup audio method",
            feature = "audio circle autostart block",
            featureKey = FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
        ),
        DexKitTargetDescriptor(
            id = DomesticVideoAdPreloadDexKitResolver.CACHE_ID,
            target = "domestic video front ad download method",
            feature = "video ad preload block",
            featureKey = FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD,
        ),
        DexKitTargetDescriptor(
            id = DomesticSwanPreloadResolver.PREFETCH_EVENT_CACHE_ID,
            target = "domestic Swan prefetch event method",
            feature = "Swan preload block",
            featureKey = FeatureKeys.KEY_DISABLE_SWAN_PRELOAD,
        ),
        DexKitTargetDescriptor(
            id = DomesticIconResourceDownloadDexKitResolver.CACHE_ID,
            target = "domestic icon resource download start method",
            feature = "icon resource download block",
            featureKey = FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
        ),
        DexKitTargetDescriptor(
            id = DomesticCookieByBdussDexKitResolver.CACHE_ID,
            target = "domestic cookie by BDUSS method",
            featureKey = FeatureKeys.KEY_AUTO_DAILY_SIGN_IN,
            feature = "自动签到",
        ),
        DexKitTargetDescriptor(
            id = DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_DOWNLOAD_FACTORY_CACHE_ID,
            target = "domestic dynamic plugin auto-download factory",
            feature = "dynamic plugin auto download block",
            featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
        ),
        DexKitTargetDescriptor(
            id = DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_INSTALL_FACTORY_CACHE_ID,
            target = "domestic dynamic plugin auto-install factory",
            feature = "dynamic plugin auto download block",
            featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
        ),
        DexKitTargetDescriptor(
            id = DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_DOWNLOAD_DECISION_CACHE_ID,
            target = "domestic dynamic plugin auto-download decision",
            feature = "dynamic plugin auto download block",
            featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
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
            id = AlbumBackupBarAddUseCaseDexKitResolver.CACHE_ID,
            target = "album backup bar add use case realExecute",
            feature = "album backup bar block",
            featureKey = FeatureKeys.KEY_BLOCK_ALBUM_BACKUP_BAR,
        ),
        DexKitTargetDescriptor(
            id = AboutMeTopHeteromoDexKitResolver.CACHE_ID,
            target = "about me top heteromo member card fragment",
            feature = "member card customize",
            featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        ),
    )

    override fun buildTasks(
        host: DexKitHostContext,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ): List<DexKitWarmUpTask> {
        val tasks = mutableListOf<DexKitWarmUpTask>()
        fun available(featureKey: String): Boolean = host.isFeatureAvailable(featureKey)

        if (available(FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL)) {
            tasks += DexKitWarmUpTask(DomesticColdStartSplashDexKitResolver.CACHE_ID) {
                DomesticColdStartSplashDexKitResolver.warmUpDexKitCache(classLoader)
            }
            tasks += DexKitWarmUpTask(DomesticHotStartSplashDexKitResolver.CACHE_ID) {
                DomesticHotStartSplashDexKitResolver.resolve(classLoader) != null
            }
        }
        if (available(FeatureKeys.KEY_BLOCK_UPDATE_DIALOG)) {
            tasks += DexKitWarmUpTask(DomesticUpdateDialogDexKitResolver.CACHE_ID) {
                DomesticUpdateDialogDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_REPLACE_BOTTOM_AI)) {
            tasks += DexKitWarmUpTask(BottomAiTabDexKitResolver.CACHE_ID) {
                BottomAiTabDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE)) {
            tasks += DexKitWarmUpTask(DomesticChangeSkinDexKitResolver.CACHE_ID) {
                DomesticChangeSkinDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE)) {
            tasks += DexKitWarmUpTask(DomesticThumbnailOperatorDexKitResolver.CLIENT_COMPUTE_INIT_CACHE_ID) {
                DomesticThumbnailOperatorDexKitResolver.resolveClientComputeInit(classLoader) != null
            }
            tasks += DexKitWarmUpTask(DomesticThumbnailOperatorDexKitResolver.THUMBNAIL_ADD_JOB_CACHE_ID) {
                DomesticThumbnailOperatorDexKitResolver.resolveThumbnailAddJob(classLoader) != null
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART)) {
            tasks += DexKitWarmUpTask(DomesticFloatViewStartupDexKitResolver.CACHE_ID) {
                DomesticFloatViewStartupDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD)) {
            tasks += DexKitWarmUpTask(DomesticVideoAdPreloadDexKitResolver.CACHE_ID) {
                DomesticVideoAdPreloadDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_SWAN_PRELOAD)) {
            tasks += DexKitWarmUpTask(DomesticSwanPreloadResolver.PREFETCH_EVENT_CACHE_ID) {
                DomesticSwanPreloadResolver.warmUpPrefetchEventCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD)) {
            tasks += DexKitWarmUpTask(DomesticIconResourceDownloadDexKitResolver.CACHE_ID) {
                DomesticIconResourceDownloadDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_AUTO_DAILY_SIGN_IN)) {
            tasks += DexKitWarmUpTask(DomesticCookieByBdussDexKitResolver.CACHE_ID) {
                DomesticCookieByBdussDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD)) {
            tasks += DexKitWarmUpTask(
                DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_DOWNLOAD_FACTORY_CACHE_ID,
            ) {
                DomesticDynamicPluginAutoDecisionDexKitResolver.warmUpAutoDownloadFactoryCache(classLoader)
            }
            tasks += DexKitWarmUpTask(
                DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_INSTALL_FACTORY_CACHE_ID,
            ) {
                DomesticDynamicPluginAutoDecisionDexKitResolver.warmUpAutoInstallFactoryCache(classLoader)
            }
            tasks += DexKitWarmUpTask(
                DomesticDynamicPluginAutoDecisionDexKitResolver.AUTO_DOWNLOAD_DECISION_CACHE_ID,
            ) {
                DomesticDynamicPluginAutoDecisionDexKitResolver.warmUpAutoDownloadDecisionCache(classLoader)
            }
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
        if (available(FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE)) {
            tasks += DexKitWarmUpTask(AboutMeTopHeteromoDexKitResolver.CACHE_ID) {
                AboutMeTopHeteromoDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        return tasks
    }
}
