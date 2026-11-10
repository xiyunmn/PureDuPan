package com.xiyunmn.puredupan.hook.runtime

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.HomeCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.HomeSavedHistoryCache
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.DownloadOpenWithHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme.AboutMeBottomContentPositionHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.AutoDailySignInScheduler
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.DomesticAutoDailySignInHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.automation.IntlAutoDailySignInHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticSharePushGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticNotificationPromptBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlHomeStableRestoreSignal
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlAlbumAiInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlStoryDouyinInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlOfflinePackageInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlFeedPreloadDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlTaskScoreRefreshDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlFloatViewStartupDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlNonCoreDiffSocketDelayHook

/** 在主线程、退役获准后释放本代拥有的宿主资源。 */
internal object HostResourceCleanup {
    fun exportState(): ArrayList<Array<Any?>> = HomeCustomizeHook.exportState()

    fun restoreState(targets: List<Array<Any?>>, cl: ClassLoader) {
        HomeCustomizeHook.restoreState(targets, cl)
    }

    fun disposeForReload() {
        com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.MarketingSearchTransferBlockHook.disposeForReload()
        AutoDailySignInRuntime.disposeForReload()
        AutoDailySignInScheduler.disposeForReload()
        DomesticAutoDailySignInHook.disposeForReload()
        IntlAutoDailySignInHook.disposeForReload()
        HomeCustomizeHook.disposeForReload()
        HomeSavedHistoryCache.disposeForReload()
        DownloadOpenWithHook.disposeForReload()
        AboutMeBottomContentPositionHook.disposeForReload()
        DomesticSharePushGuideBlockHook.disposeForReload()
        DomesticNotificationPromptBlockHook.disposeForReload()
        IntlAlbumAiInitBlockHook.disposeForReload()
        IntlStoryDouyinInitBlockHook.disposeForReload()
        IntlOfflinePackageInitBlockHook.disposeForReload()
        IntlFeedPreloadDelayHook.disposeForReload()
        IntlTaskScoreRefreshDelayHook.disposeForReload()
        IntlFloatViewStartupDelayHook.disposeForReload()
        IntlNonCoreDiffSocketDelayHook.disposeForReload()
        IntlHomeStableRestoreSignal.disposeForReload()
    }
}
