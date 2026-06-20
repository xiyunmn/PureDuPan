package com.xiyunmn.puredupan.hook.host

import android.content.Context
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus
import com.xiyunmn.puredupan.hook.config.model.MemberCardLayoutMode
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.features.HostFeatureAvailabilityRegistry

internal object HostRuntimeState {
    fun supportedPackageNames(): Set<String> {
        return HostRegistry.supportedPackageNames
    }

    fun profileForPackage(packageName: String): HostProfile? {
        return HostRegistry.resolveByPackageName(packageName)
    }

    fun hostIdForPackage(packageName: String): String? {
        return profileForPackage(packageName)?.id
    }

    fun isSupportedPackage(packageName: String): Boolean {
        return profileForPackage(packageName) != null
    }

    fun featureStatusMapFor(context: Context): Map<String, FeatureAvailabilityStatus> {
        return featureStatusMapForPackage(context.packageName)
    }

    fun featureStatusMapForPackage(packageName: String): Map<String, FeatureAvailabilityStatus> {
        return HostFeatureAvailabilityRegistry.featureStatusMapFor(packageName)
    }

    private fun capabilitiesFor(context: Context): HostCapabilities {
        return capabilitiesForPackage(context.packageName)
    }

    private fun capabilitiesForPackage(packageName: String): HostCapabilities {
        return HostRegistry.resolveByPackageName(packageName)?.capabilities ?: HostCapabilities()
    }

    private fun currentUiHookPoints(): HostUiHookPoints {
        return XposedCompat.currentPackageName()
            ?.let(::capabilitiesForPackage)
            ?.uiHookPoints
            ?: HostUiHookPoints()
    }

    fun currentMainActivityClassName(): String? {
        return currentUiHookPoints().mainActivityClassName
    }

    fun currentMainActivityPresenterClassName(): String? {
        return currentUiHookPoints().mainActivityPresenterClassName
    }

    fun currentAboutMeActivityClassName(): String? {
        return currentUiHookPoints().aboutMeActivityClassName
    }

    fun currentAboutMeActivityClassNames(): List<String> {
        val hookPoints = currentUiHookPoints()
        return listOfNotNull(
            hookPoints.aboutMeActivityClassName,
            hookPoints.newAboutMeActivityClassName,
        ).distinct()
    }

    fun currentPopupResponseClassName(): String? {
        return currentUiHookPoints().popupResponseClassName
    }

    fun currentNewHomeFabFragmentClassName(): String? {
        return currentUiHookPoints().newHomeFabFragmentClassName
    }

    fun currentSettingsImageResultHostActivityClassNames(): List<String> {
        return currentUiHookPoints().settingsImageResultHostActivityClassNames
    }

    fun currentHomeCustomizeHookPoints(): HomeCustomizeHookPoints {
        return currentUiHookPoints().homeCustomize
    }

    fun supportsExperimentalDexKit(packageName: String): Boolean {
        return !capabilitiesForPackage(packageName).dexKit.targetRegistryId.isNullOrBlank()
    }

    fun dexKitRuntimeInfoForPackage(packageName: String): HostDexKitRuntimeInfo? {
        return HostRegistry.resolveByPackageName(packageName)?.let(HostDexKitRuntimeInfo::from)
    }

    fun showDexKitStatusInSettings(context: Context): Boolean {
        return capabilitiesFor(context).dexKit.showStatusInSettings
    }

    fun memberCardLayoutModeFor(context: Context): MemberCardLayoutMode {
        return capabilitiesFor(context).settings.memberCardLayoutMode
    }

    fun primarySplashAdFeatureKeyFor(context: Context): String? {
        return primarySplashAdFeatureKeyForPackage(context.packageName)
    }

    fun primarySplashAdFeatureKeyForPackage(packageName: String): String? {
        return capabilitiesForPackage(packageName).settings.primarySplashAdFeatureKey
    }

    fun skinConfigClassNameFor(context: Context): String? {
        return capabilitiesFor(context).uiHookPoints.skinConfigClassName
    }

    fun canonicalPackageNameOrSelf(packageName: String): String {
        return HostRegistry.resolveByPackageName(packageName)?.packageName ?: packageName
    }
}
