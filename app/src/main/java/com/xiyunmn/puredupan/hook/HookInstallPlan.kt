package com.xiyunmn.puredupan.hook

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.ad.AppStoreReviewBlockHook
import com.xiyunmn.puredupan.hook.feature.ad.BusinessOpDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.ad.FullScreenBackupBlockHook
import com.xiyunmn.puredupan.hook.feature.ad.LuckyCouponBlockHook
import com.xiyunmn.puredupan.hook.feature.ad.SharePushGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.ad.SvipIconGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.ad.UpdateDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.AigcBackgroundComponentBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.AdSdkInitBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.B2fGuidancePrefetchBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.DatapackSocketRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.DynamicPluginAutoDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.GarbageCleanServiceRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.IconResourceDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.IncentiveBusinessServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.AudioCircleViewAutostartBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.intl.IntlFeedPreloadDelayHook
import com.xiyunmn.puredupan.hook.feature.performance.intl.IntlOfflinePackageSyncDelayHook
import com.xiyunmn.puredupan.hook.feature.performance.intl.IntlTaskScoreRefreshDelayHook
import com.xiyunmn.puredupan.hook.feature.performance.OemPushServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.SwanPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.ThumbnailOperatorServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.performance.VideoAdPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.startup.SplashBypassCore
import com.xiyunmn.puredupan.hook.feature.startup.SplashInterstitialBlockHook
import com.xiyunmn.puredupan.hook.feature.startup.intl.IntlLaunchHandoffOptimizeHook
import com.xiyunmn.puredupan.hook.feature.startup.hotstart.HotStartSplashRemoveHook
import com.xiyunmn.puredupan.hook.feature.ui.AlbumBackupBarBlockHook
import com.xiyunmn.puredupan.hook.feature.ui.BottomAiTabReplaceHook
import com.xiyunmn.puredupan.hook.feature.ui.BottomBarBadgeBlockHook
import com.xiyunmn.puredupan.hook.feature.ui.BottomBarSimplifyFeature
import com.xiyunmn.puredupan.hook.feature.ui.FormalUiEntryHook
import com.xiyunmn.puredupan.hook.feature.ui.AboutMeGodModeHook
import com.xiyunmn.puredupan.hook.feature.ui.GameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.ui.GameCenterRuntimeBlockHook
import com.xiyunmn.puredupan.hook.feature.ui.membercard.cn.CnMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.ui.membercard.intl.IntlMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.ui.NewHomeFabRemoveHook
import com.xiyunmn.puredupan.hook.feature.ui.RenewButtonHideHook
import com.xiyunmn.puredupan.hook.feature.ui.SettingsImagePickerResultHook
import com.xiyunmn.puredupan.hook.feature.ui.SystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.ui.HomeCustomizeHook
import com.xiyunmn.puredupan.hook.feature.ui.HomeUploadEntryHook
import com.xiyunmn.puredupan.hook.host.HostProfile

internal data class HookInstallEntry(
    val id: String,
    val install: (ClassLoader) -> Unit,
)

internal data class HookInstallPlan(
    val processName: String,
    val phase: String,
    val entries: List<HookInstallEntry>,
) {
    fun isEmpty(): Boolean = entries.isEmpty()
}

internal object HookInstaller {
    fun install(plan: HookInstallPlan, cl: ClassLoader) {
        if (plan.entries.isEmpty()) {
            XposedCompat.logD("[HookInstallPlan] ${plan.phase}: empty for process=${plan.processName}")
            return
        }
        for (entry in plan.entries) {
            try {
                XposedCompat.logD("[HookInstallPlan] Installing ${entry.id} (${plan.phase})...")
                entry.install(cl)
            } catch (e: ReflectiveOperationException) {
                // 反射相关异常：方法/类不存在，通常是版本不兼容
                XposedCompat.log(
                    "[HookInstallPlan] ${entry.id} install FAILED (${plan.phase}): ${e.javaClass.simpleName}: ${e.message}",
                )
                XposedCompat.log(e)
            } catch (e: Exception) {
                // 其他可恢复异常
                XposedCompat.log(
                    "[HookInstallPlan] ${entry.id} install FAILED (${plan.phase}): ${e.message}",
                )
                XposedCompat.log(e)
            } catch (e: Error) {
                // 严重错误：OOM, StackOverflow 等
                XposedCompat.logE(
                    "[HookInstallPlan] ${entry.id} install FATAL ERROR (${plan.phase}): ${e.javaClass.simpleName}: ${e.message}",
                )
                XposedCompat.log(e)
                // 不中断其他 Hook 的安装，但记录严重错误
            }
        }
    }
}

internal object HookInstallPlanner {
    private data class DerivedSettings(
        val hasMemberCardCustomizeOption: Boolean,
        val hasHomeCustomizeOption: Boolean,
        val hasBottomBarTabOption: Boolean,
    )

    private data class HookSpec(
        val id: String,
        val enabled: (PlanContext, SettingsSnapshot, DerivedSettings) -> Boolean,
        val install: (ClassLoader) -> Unit,
    )

    private class PlanContext(
        val host: HostProfile,
        val processName: String,
    ) {
        val isMain: Boolean = host.isMainProcess(processName)
        val isPushService: Boolean = host.isPushServiceProcess(processName)
        val supportsOemPushHook: Boolean =
            host.capabilities.supportsOemPushHook && (isMain || isPushService)
    }

    private val postAttachSpecs = listOf(
        HookSpec("FormalUiEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> FormalUiEntryHook.hook(cl) },
        HookSpec("SettingsImagePickerResultHook", { context, _, _ ->
            context.isMain
        }) { cl -> SettingsImagePickerResultHook.hook(cl) },
        HookSpec("RenewButtonHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isRenewButtonHidden
        }) { cl -> RenewButtonHideHook.hook(cl) },
        HookSpec("SplashInterstitialBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }) { cl -> SplashInterstitialBlockHook.hook(cl) },
        HookSpec("HotStartSplashRemoveHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsStandaloneHotStartSplashRemove &&
                context.host.capabilities.supportsHotStartSplashAd &&
                settings.isHotStartSplashRemoveEnabled
        }) { cl -> HotStartSplashRemoveHook.hook(cl) },
        HookSpec("SplashBypassCore", { context, settings, _ ->
            context.isMain && settings.isSplashInterstitialBlockEnabled
        }) { cl -> SplashBypassCore.hook(cl) },
        HookSpec("BusinessOpDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }) { cl -> BusinessOpDialogBlockHook.hook(cl) },
        HookSpec("LuckyCouponBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }) { cl -> LuckyCouponBlockHook.hook(cl) },
        HookSpec("UpdateDialogBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsUpdateDialogBlock &&
                settings.isUpdateDialogBlocked
        }) { cl -> UpdateDialogBlockHook.hook(cl) },
        HookSpec("FullScreenBackupBlockHook", { context, settings, _ ->
            context.isMain && settings.isFullScreenBackupBlocked
        }) { cl -> FullScreenBackupBlockHook.hook(cl) },
        HookSpec("SvipIconGuideBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsSvipIconGuideBlock &&
                settings.isFullScreenBackupBlocked
        }) { cl -> SvipIconGuideBlockHook.hook(cl) },
        HookSpec("SharePushGuideBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsSharePushGuideBlock &&
                settings.isSharePushGuideBlocked
        }) { cl -> SharePushGuideBlockHook.hook(cl) },
        HookSpec("AppStoreReviewBlockHook", { context, settings, _ ->
            context.isMain && settings.isAppStoreReviewBlocked
        }) { cl -> AppStoreReviewBlockHook.hook(cl) },
        HookSpec("BottomAiTabReplaceHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsBottomAiTabReplace &&
                settings.isBottomBarCustomEnabled &&
                settings.isBottomAiReplaced
        }) { cl -> BottomAiTabReplaceHook.hook(cl) },
        HookSpec("BottomBarBadgeBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                settings.isBottomBarBadgeBlocked
        }) { cl -> BottomBarBadgeBlockHook.hook(cl) },
        HookSpec("HomeCustomizeHook", { context, settings, derived ->
            context.isMain &&
                context.host.capabilities.supportsHomeCustomize &&
                settings.isHomeCustomizeEnabled &&
                derived.hasHomeCustomizeOption
        }) { cl -> HomeCustomizeHook.hook(cl) },
        HookSpec("GameCenterRuntimeBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }) { cl -> GameCenterRuntimeBlockHook.hook(cl) },
        HookSpec("GameCenterRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }) { cl -> GameCenterRemoveHook.hook(cl) },
        HookSpec("AboutMeGodModeHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                (
                    settings.isAboutMeBannerRemoved ||
                        settings.isMyServiceRemoved ||
                        settings.isAboutMeCoinCenterBubbleHidden ||
                        settings.isAboutMeSignInDotHidden ||
                        settings.isAboutMeAiCoinAssetHidden ||
                        settings.isAboutMeManageSpaceTextHidden ||
                        settings.isAboutMeRewardTextHidden ||
                        settings.isAboutMeAccountExitTextHidden ||
                        settings.isAboutMeStarSkinTextHidden
                )
        }) { cl -> AboutMeGodModeHook.hook(cl) },
        HookSpec("CnMemberCardCustomizeHook", { context, settings, derived ->
            context.isMain &&
                context.host.flavor == com.xiyunmn.puredupan.hook.host.HostFlavor.BAIDU_CN &&
                context.host.capabilities.supportsMemberCardCustomize &&
                settings.isMemberCardCustomizeEnabled &&
                derived.hasMemberCardCustomizeOption
        }) { cl -> CnMemberCardCustomizeHook.hook(cl) },
        HookSpec("IntlMemberCardCustomizeHook", { context, settings, derived ->
            context.isMain &&
                context.host.flavor == com.xiyunmn.puredupan.hook.host.HostFlavor.BAIDU_INTL &&
                context.host.capabilities.supportsMemberCardCustomize &&
                settings.isMemberCardCustomizeEnabled &&
                derived.hasMemberCardCustomizeOption
        }) { cl -> IntlMemberCardCustomizeHook.hook(cl) },
        HookSpec("NewHomeFabRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isSharePageCustomizeEnabled &&
                settings.isHomeFabRemoved
        }) { cl -> NewHomeFabRemoveHook.hook(cl) },
        HookSpec("AlbumBackupBarBlockHook", { context, settings, _ ->
            context.isMain && settings.isAlbumBackupBarBlocked
        }) { cl -> AlbumBackupBarBlockHook.hook(cl) },
        HookSpec("BottomBarSimplifyFeature", { context, settings, derived ->
            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                derived.hasBottomBarTabOption
        }) { cl -> BottomBarSimplifyFeature.hook(cl) },
        HookSpec("IntlLaunchHandoffOptimizeHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsLaunchHandoffOptimize &&
                settings.isIntlSplashStartupAccelerateEnabled
        }) { cl -> IntlLaunchHandoffOptimizeHook.hook(cl) },
        HookSpec("GarbageCleanServiceRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsGarbageCleanServiceOptimize &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isGarbageCleanServiceRegisterDisabled
        }) { cl -> GarbageCleanServiceRegisterBlockHook.hook(cl) },
        HookSpec("DatapackSocketRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsDatapackSocketOptimize &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDatapackSocketRegisterDisabled
        }) { cl -> DatapackSocketRegisterBlockHook.hook(cl) },
        HookSpec("AigcBackgroundComponentBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsAigcBackgroundOptimize &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAigcBackgroundComponentDisabled
        }) { cl -> AigcBackgroundComponentBlockHook.hook(cl) },
        HookSpec("DynamicPluginAutoDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsDynamicPluginAutoDownloadBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDynamicPluginAutoDownloadDisabled
        }) { cl -> DynamicPluginAutoDownloadBlockHook.hook(cl) },
        HookSpec("OemPushServiceBlockHook", { context, settings, _ ->
            context.supportsOemPushHook &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isOemPushServiceDisabled
        }) { cl -> OemPushServiceBlockHook.hook(cl) },
        HookSpec("VideoAdPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsVideoAdPreloadBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isVideoAdPreloadDisabled
        }) { cl -> VideoAdPreloadBlockHook.hook(cl) },
        HookSpec("AdSdkInitBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsAdSdkInitBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAdSdkInitDisabled
        }) { cl -> AdSdkInitBlockHook.hook(cl) },
        HookSpec("SwanPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsSwanPreloadBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isSwanPreloadDisabled
        }) { cl -> SwanPreloadBlockHook.hook(cl) },
        HookSpec("ThumbnailOperatorServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsThumbnailOperatorServiceBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isThumbnailOperatorServiceDisabled
        }) { cl -> ThumbnailOperatorServiceBlockHook.hook(cl) },
        HookSpec("IncentiveBusinessServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsIncentiveBusinessServiceBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIncentiveBusinessServiceDisabled
        }) { cl -> IncentiveBusinessServiceBlockHook.hook(cl) },
        HookSpec("AudioCircleViewAutostartBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsAudioCircleAutostartBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isMediaBrowserServiceAutostartDisabled
        }) { cl -> AudioCircleViewAutostartBlockHook.hook(cl) },
        HookSpec("IconResourceDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsIconResourceDownloadBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIconResourceDownloadDisabled
        }) { cl -> IconResourceDownloadBlockHook.hook(cl) },
        HookSpec("B2fGuidancePrefetchBlockHook", { context, settings, _ ->
            context.isMain &&
                context.host.capabilities.supportsB2fGuidancePrefetchBlock &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isB2fGuidancePrefetchDisabled
        }) { cl -> B2fGuidancePrefetchBlockHook.hook(cl) },
        HookSpec("IntlOfflinePackageSyncDelayHook", { context, settings, _ ->
            context.isMain &&
                context.host.flavor == com.xiyunmn.puredupan.hook.host.HostFlavor.BAIDU_INTL &&
                context.host.capabilities.supportsIntlOfflinePackageSyncDelay &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlOfflinePackageSyncDelayed
        }) { cl -> IntlOfflinePackageSyncDelayHook.hook(cl) },
        HookSpec("IntlFeedPreloadDelayHook", { context, settings, _ ->
            context.isMain &&
                context.host.flavor == com.xiyunmn.puredupan.hook.host.HostFlavor.BAIDU_INTL &&
                context.host.capabilities.supportsIntlFeedPreloadDelay &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlFeedPreloadDelayed
        }) { cl -> IntlFeedPreloadDelayHook.hook(cl) },
        HookSpec("IntlTaskScoreRefreshDelayHook", { context, settings, _ ->
            context.isMain &&
                context.host.flavor == com.xiyunmn.puredupan.hook.host.HostFlavor.BAIDU_INTL &&
                context.host.capabilities.supportsIntlTaskScoreRefreshDelay &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlTaskScoreRefreshDelayed
        }) { cl -> IntlTaskScoreRefreshDelayHook.hook(cl) },
        HookSpec("SystemNightModeSyncHook", { context, _, _ ->
            context.isMain
        }) { cl -> SystemNightModeSyncHook.hook(cl) },
        HookSpec("HomeUploadEntryHook", { context, _, _ ->
            context.isMain && context.host.capabilities.supportsHomeUploadEntry
        }) { cl -> HomeUploadEntryHook.hook(cl) },
    )

    fun shouldHandleProcess(host: HostProfile, processName: String): Boolean {
        return host.shouldHandleProcess(processName)
    }

    fun shouldInstallAttachHook(host: HostProfile, processName: String): Boolean {
        return host.shouldInstallAttachHook(processName)
    }

    fun staticPlan(host: HostProfile, processName: String): HookInstallPlan {
        return HookInstallPlan(processName, "static", emptyList())
    }

    fun postAttachPlan(
        host: HostProfile,
        processName: String,
        settings: SettingsSnapshot,
    ): HookInstallPlan {
        val context = PlanContext(host, processName)
        val derived = deriveSettings(settings)
        val entries = postAttachSpecs
            .filter { spec -> spec.enabled(context, settings, derived) }
            .map { spec -> HookInstallEntry(spec.id, spec.install) }
        return HookInstallPlan(processName, "postAttach", entries)
    }

    private fun deriveSettings(settings: SettingsSnapshot): DerivedSettings {
        return DerivedSettings(
            hasMemberCardCustomizeOption = settings.isMemberCardBackgroundReplaced ||
                settings.isMemberCardSizeAdjusted ||
                settings.isMemberCardOperationHidden ||
                settings.isMemberCardBenefitHidden ||
                settings.isMemberCardFirstBenefitHidden ||
                settings.isMemberCardSecondBenefitHidden ||
                settings.isMemberCardThirdBenefitHidden ||
                settings.isMemberCardBenefitBarHidden ||
                settings.isMemberCardSvipLevelHidden ||
                settings.isMemberCardSvipStatusHidden ||
                settings.isMemberCardRenewButtonHidden ||
                settings.isIntlMemberCardSvipLevelHidden ||
                settings.isIntlMemberCardUpgradeButtonHidden ||
                settings.isMemberCardClickRemoved ||
                settings.isMemberCardBackgroundViewedOnClick,
            hasHomeCustomizeOption = settings.isHomeTopPromotionHidden ||
                settings.isHomeSearchPlaceholderHidden ||
                settings.isHomeSearchAigcIconHidden ||
                settings.isHomeFeedTipHidden ||
                settings.isHomeBannerHidden ||
                settings.isHomeMemoriesSectionHidden ||
                settings.isHomeSaveSectionHidden ||
                settings.isHomeRecentSectionHidden,
            hasBottomBarTabOption = settings.isBottomBarTabFileHidden ||
                settings.isBottomBarTabAigcHidden ||
                settings.isBottomBarTabShareHidden ||
                settings.isBottomBarTabVipHidden ||
                settings.isBottomBarTabHomeHidden ||
                settings.isBottomBarTabMineHidden,
        )
    }

}
