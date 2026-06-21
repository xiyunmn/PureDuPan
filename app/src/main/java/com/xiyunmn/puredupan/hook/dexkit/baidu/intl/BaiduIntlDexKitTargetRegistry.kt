package com.xiyunmn.puredupan.hook.dexkit.baidu.intl

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.dexkit.DexKitHostContext
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetDescriptor
import com.xiyunmn.puredupan.hook.dexkit.DexKitTargetRegistry
import com.xiyunmn.puredupan.hook.dexkit.DexKitWarmUpTask
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlAlbumAiInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlNonCoreDiffSocketDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlStoryDouyinInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.startup.hotstart.IntlHotStartSplashDexKitResolver
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search.IntlSearchPageCustomizeHook
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchPageHookPoints

internal object BaiduIntlDexKitTargetRegistry : DexKitTargetRegistry {
    override val descriptors = listOf(
        DexKitTargetDescriptor(
            id = IntlHotStartSplashDexKitResolver.CACHE_ID,
            target = "intl hot-start splash resolver",
            feature = "移除切屏加载",
        ),
        DexKitTargetDescriptor(
            id = IntlStoryDouyinInitBlockHook.STORY_INIT_CACHE_ID,
            target = "intl story init method",
            feature = "阻止 Story/DouYin 初始化",
        ),
        DexKitTargetDescriptor(
            id = IntlNonCoreDiffSocketDelayHook.SOCKET_REGISTER_CACHE_ID,
            target = "intl diff socket register method",
            feature = "延后非核心 diff socket 注册",
        ),
        DexKitTargetDescriptor(
            id = IntlAlbumAiInitBlockHook.DIRECT_ALBUM_AI_INIT_CACHE_ID,
            target = "intl album init method",
            feature = "阻止相册 AI 初始化",
        ),
        DexKitTargetDescriptor(
            id = BaiduIntlSearchPageHookPoints.QUERY_AI_RECOMMEND_CACHE_ID,
            target = "intl search AI recommend query method",
            feature = "搜索页智能推荐",
        ),
        DexKitTargetDescriptor(
            id = BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_CARD_CACHE_ID,
            target = "intl search AI recommend card composable",
            feature = "搜索页智能推荐",
        ),
        DexKitTargetDescriptor(
            id = BaiduIntlSearchPageHookPoints.searchDefaultContentHelperCacheIds[0].second,
            target = "intl legacy search placeholder display method",
            feature = "搜索页搜索框提示词",
        ),
        DexKitTargetDescriptor(
            id = BaiduIntlSearchPageHookPoints.searchDefaultContentHelperCacheIds[1].second,
            target = "intl new-feed search placeholder display method",
            feature = "搜索页搜索框提示词",
        ),
    )

    override fun buildTasks(host: DexKitHostContext, classLoader: ClassLoader): List<DexKitWarmUpTask> {
        val tasks = mutableListOf<DexKitWarmUpTask>()

        if (host.isFeatureAvailable(FeatureKeys.KEY_REMOVE_HOT_START_SPLASH)) {
            tasks += DexKitWarmUpTask(IntlHotStartSplashDexKitResolver.CACHE_ID) {
                IntlHotStartSplashDexKitResolver.resolve(classLoader) != null
            }
        }
        if (host.isFeatureAvailable(FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT)) {
            tasks += DexKitWarmUpTask(IntlStoryDouyinInitBlockHook.STORY_INIT_CACHE_ID) {
                IntlStoryDouyinInitBlockHook.warmUpDexKitCache(classLoader)
            }
        }
        if (host.isFeatureAvailable(FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET)) {
            tasks += DexKitWarmUpTask(IntlNonCoreDiffSocketDelayHook.SOCKET_REGISTER_CACHE_ID) {
                IntlNonCoreDiffSocketDelayHook.warmUpDexKitCache(classLoader)
            }
        }
        if (host.isFeatureAvailable(FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT)) {
            tasks += DexKitWarmUpTask(IntlAlbumAiInitBlockHook.DIRECT_ALBUM_AI_INIT_CACHE_ID) {
                IntlAlbumAiInitBlockHook.warmUpDexKitCache(classLoader)
            }
        }
        if (host.isFeatureAvailable(FeatureKeys.KEY_SEARCH_PAGE_CUSTOMIZE)) {
            tasks += DexKitWarmUpTask(BaiduIntlSearchPageHookPoints.QUERY_AI_RECOMMEND_CACHE_ID) {
                IntlSearchPageCustomizeHook.warmUpQueryAiRecommendCache(classLoader)
            }
            tasks += DexKitWarmUpTask(BaiduIntlSearchPageHookPoints.SEARCH_AI_RECOMMEND_CARD_CACHE_ID) {
                IntlSearchPageCustomizeHook.warmUpSearchAiRecommendCardCache(classLoader)
            }
            BaiduIntlSearchPageHookPoints.searchDefaultContentHelperCacheIds.forEach { (_, cacheId) ->
                tasks += DexKitWarmUpTask(cacheId) {
                    IntlSearchPageCustomizeHook.warmUpPlaceholderDisplayCache(classLoader, cacheId)
                }
            }
        }

        return tasks
    }
}
