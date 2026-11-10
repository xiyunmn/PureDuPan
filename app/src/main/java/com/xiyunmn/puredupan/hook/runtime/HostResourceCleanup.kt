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

    fun prepareState(targets: List<Array<Any?>>, cl: ClassLoader) {
        HomeCustomizeHook.prepareState(targets, cl)
    }

    fun restoreState(targets: List<Array<Any?>>, cl: ClassLoader) {
        HomeCustomizeHook.restoreState(targets, cl)
    }

    fun disposeForReload(failures: HotReloadFailures) {
        failures.run("MarketingSearchTransferBlockHook") { com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.MarketingSearchTransferBlockHook.disposeForReload() }
        failures.run("AutoDailySignInRuntime") { AutoDailySignInRuntime.disposeForReload() }
        failures.run("AutoDailySignInScheduler") { AutoDailySignInScheduler.disposeForReload() }
        failures.run("DomesticAutoDailySignInHook") { DomesticAutoDailySignInHook.disposeForReload() }
        failures.run("IntlAutoDailySignInHook") { IntlAutoDailySignInHook.disposeForReload() }
        failures.run("HomeCustomizeHook") { HomeCustomizeHook.disposeForReload(failures) }
        failures.run("HomeSavedHistoryCache") { HomeSavedHistoryCache.disposeForReload() }
        failures.run("DownloadOpenWithHook") { DownloadOpenWithHook.disposeForReload() }
        failures.run("AboutMeBottomContentPositionHook") { AboutMeBottomContentPositionHook.disposeForReload() }
        failures.run("DomesticSharePushGuideBlockHook") { DomesticSharePushGuideBlockHook.disposeForReload() }
        failures.run("DomesticNotificationPromptBlockHook") { DomesticNotificationPromptBlockHook.disposeForReload() }
        failures.run("IntlAlbumAiInitBlockHook") { IntlAlbumAiInitBlockHook.disposeForReload() }
        failures.run("IntlStoryDouyinInitBlockHook") { IntlStoryDouyinInitBlockHook.disposeForReload() }
        failures.run("IntlOfflinePackageInitBlockHook") { IntlOfflinePackageInitBlockHook.disposeForReload() }
        failures.run("IntlFeedPreloadDelayHook") { IntlFeedPreloadDelayHook.disposeForReload() }
        failures.run("IntlTaskScoreRefreshDelayHook") { IntlTaskScoreRefreshDelayHook.disposeForReload() }
        failures.run("IntlFloatViewStartupDelayHook") { IntlFloatViewStartupDelayHook.disposeForReload() }
        failures.run("IntlNonCoreDiffSocketDelayHook") { IntlNonCoreDiffSocketDelayHook.disposeForReload() }
        failures.run("IntlHomeStableRestoreSignal") { IntlHomeStableRestoreSignal.disposeForReload() }
    }
}
