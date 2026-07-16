package com.xiyunmn.puredupan.hook.dexkit.baidu.intl

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetDescriptor
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.DexKitWarmUpTask
import com.xiyunmn.puredupan.hook.feature.baidu.intl.automation.IntlCookieByBdussDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlAlbumAiInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlNonCoreDiffSocketDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlStoryDouyinInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.startup.hotstart.IntlHotStartSplashDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.IntlBottomAiTabModeDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.IntlChangeSkinDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.membercard.IntlAboutMeTopFragmentDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AlbumBackupBarAddUseCaseDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme.AboutMePopupResponseHelperDexKitResolver

internal object BaiduIntlDexKitTargetRegistry : DexKitTargetRegistry {
    override val descriptors = listOf(
        DexKitTargetDescriptor(
            id = IntlHotStartSplashDexKitResolver.CACHE_ID,
            target = "intl hot-start splash resolver",
            featureKey = FeatureKeys.KEY_REMOVE_HOT_START_SPLASH,
            feature = "移除切屏加载",
        ),
        DexKitTargetDescriptor(
            id = IntlStoryDouyinInitBlockHook.STORY_INIT_CACHE_ID,
            target = "intl story init method",
            featureKey = FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT,
            feature = "阻止 Story/DouYin 初始化",
        ),
        DexKitTargetDescriptor(
            id = IntlNonCoreDiffSocketDelayHook.SOCKET_REGISTER_CACHE_ID,
            target = "intl diff socket register method",
            featureKey = FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET,
            feature = "延后非核心 diff socket 注册",
        ),
        DexKitTargetDescriptor(
            id = IntlAlbumAiInitBlockHook.DIRECT_ALBUM_AI_INIT_CACHE_ID,
            target = "intl album init method",
            featureKey = FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT,
            feature = "阻止相册 AI 初始化",
        ),
        DexKitTargetDescriptor(
            id = IntlChangeSkinDexKitResolver.CACHE_ID,
            target = "intl changeSkin method",
            featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
            feature = "夜间模式跟随系统",
        ),
        DexKitTargetDescriptor(
            id = IntlBottomAiTabModeDexKitResolver.CACHE_ID,
            target = "intl bottom AI tab mode getter",
            featureKey = FeatureKeys.KEY_REPLACE_BOTTOM_AI,
            feature = "底栏 AI 替换为会员",
        ),
        DexKitTargetDescriptor(
            id = IntlCookieByBdussDexKitResolver.CACHE_ID,
            target = "intl cookie by BDUSS method",
            featureKey = FeatureKeys.KEY_AUTO_DAILY_SIGN_IN,
            feature = "自动签到",
        ),
        DexKitTargetDescriptor(
            id = AlbumBackupBarAddUseCaseDexKitResolver.CACHE_ID,
            target = "intl album backup bar add use case",
            featureKey = FeatureKeys.KEY_BLOCK_ALBUM_BACKUP_BAR,
            feature = "屏蔽相册备份栏",
        ),
        DexKitTargetDescriptor(
            id = IntlAboutMeTopFragmentDexKitResolver.CACHE_ID,
            target = "intl about me top fragment member card setCardUi",
            featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
            feature = "会员卡定制",
        ),
        DexKitTargetDescriptor(
            id = AboutMePopupResponseHelperDexKitResolver.CACHE_ID,
            target = "intl about me PopupResponseHelper refresh method",
            featureKey = FeatureKeys.KEY_MY_PAGE_CUSTOMIZE,
            feature = "我的页定制",
        ),
    )

    override fun buildTasks(
        host: DexKitHostContext,
        settings: SettingsSnapshot,
        classLoader: ClassLoader,
    ): List<DexKitWarmUpTask> {
        val tasks = mutableListOf<DexKitWarmUpTask>()
        fun available(featureKey: String): Boolean = host.isFeatureAvailable(featureKey)

        if (available(FeatureKeys.KEY_REMOVE_HOT_START_SPLASH)) {
            tasks += DexKitWarmUpTask(IntlHotStartSplashDexKitResolver.CACHE_ID) {
                IntlHotStartSplashDexKitResolver.resolve(classLoader) != null
            }
        }
        if (available(FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT)) {
            tasks += DexKitWarmUpTask(IntlStoryDouyinInitBlockHook.STORY_INIT_CACHE_ID) {
                IntlStoryDouyinInitBlockHook.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET)) {
            tasks += DexKitWarmUpTask(IntlNonCoreDiffSocketDelayHook.SOCKET_REGISTER_CACHE_ID) {
                IntlNonCoreDiffSocketDelayHook.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT)) {
            tasks += DexKitWarmUpTask(IntlAlbumAiInitBlockHook.DIRECT_ALBUM_AI_INIT_CACHE_ID) {
                IntlAlbumAiInitBlockHook.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE)) {
            tasks += DexKitWarmUpTask(IntlChangeSkinDexKitResolver.CACHE_ID) {
                IntlChangeSkinDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_REPLACE_BOTTOM_AI)) {
            tasks += DexKitWarmUpTask(IntlBottomAiTabModeDexKitResolver.CACHE_ID) {
                IntlBottomAiTabModeDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_AUTO_DAILY_SIGN_IN)) {
            tasks += DexKitWarmUpTask(IntlCookieByBdussDexKitResolver.CACHE_ID) {
                IntlCookieByBdussDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_BLOCK_ALBUM_BACKUP_BAR)) {
            tasks += DexKitWarmUpTask(AlbumBackupBarAddUseCaseDexKitResolver.CACHE_ID) {
                AlbumBackupBarAddUseCaseDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE)) {
            tasks += DexKitWarmUpTask(IntlAboutMeTopFragmentDexKitResolver.CACHE_ID) {
                IntlAboutMeTopFragmentDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        if (available(FeatureKeys.KEY_MY_PAGE_CUSTOMIZE)) {
            tasks += DexKitWarmUpTask(AboutMePopupResponseHelperDexKitResolver.CACHE_ID) {
                AboutMePopupResponseHelperDexKitResolver.warmUpDexKitCache(classLoader)
            }
        }
        return tasks
    }
}
