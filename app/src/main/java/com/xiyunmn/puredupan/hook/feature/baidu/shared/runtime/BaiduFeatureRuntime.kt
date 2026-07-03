package com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime

import android.content.Context
import com.xiyunmn.puredupan.hook.host.HostPackages
import com.xiyunmn.puredupan.hook.host.HostRuntimeState

internal object BaiduFeatureRuntime {
    fun currentMainActivityClassName(): String? =
        HostRuntimeState.currentMainActivityClassName()

    fun currentMainActivityPresenterClassName(): String? =
        HostRuntimeState.currentMainActivityPresenterClassName()

    fun currentAboutMeActivityClassName(): String? =
        HostRuntimeState.currentAboutMeActivityClassName()

    fun currentAboutMeActivityClassNames(): List<String> =
        HostRuntimeState.currentAboutMeActivityClassNames()

    fun currentPopupResponseClassName(): String? =
        HostRuntimeState.currentPopupResponseClassName()

    fun currentNewHomeFabFragmentClassName(): String? =
        HostRuntimeState.currentNewHomeFabFragmentClassName()

    fun currentSettingsImageResultHostActivityClassNames(): List<String> =
        HostRuntimeState.currentSettingsImageResultHostActivityClassNames()

    fun currentStableActivityClassNames(): List<String> =
        HostRuntimeState.currentStableActivityClassNames()

    fun currentHomeCustomizeHookPoints() =
        HostRuntimeState.currentHomeCustomizeHookPoints()

    fun currentHotStartSplashLifecycleManagerClassName(): String? =
        HostRuntimeState.currentHotStartSplashLifecycleManagerClassName()

    fun currentHotStartSplashBackgroundResumeAdStartMethodName(): String? =
        HostRuntimeState.currentHotStartSplashBackgroundResumeAdStartMethodName()

    fun skinConfigClassNameFor(context: Context): String? =
        HostRuntimeState.skinConfigClassNameFor(context)

    fun isSamsungHost(context: Context): Boolean =
        HostRuntimeState.canonicalPackageNameOrSelf(context.packageName) == HostPackages.BAIDU_SAMSUNG

    fun isIntlHost(context: Context): Boolean =
        HostRuntimeState.canonicalPackageNameOrSelf(context.packageName) == HostPackages.BAIDU_INTL
}
