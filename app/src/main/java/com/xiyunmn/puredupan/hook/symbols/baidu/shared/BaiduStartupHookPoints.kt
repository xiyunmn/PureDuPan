package com.xiyunmn.puredupan.hook.symbols.baidu.shared

/**
 * Startup-ad hook points shared by domestic Baidu Netdisk host flavors.
 */
internal object BaiduStartupHookPoints {
    const val SPLASH_AD_ACTIVITY = "com.baidu.netdisk.advertise.ui.SplashAdActivity"
    const val DOMESTIC_HOT_START_MANAGER_STABLE =
        "com.baidu.netdisk.advertise.AdvertiseHotStartManager"
    const val DOMESTIC_HOT_START_MANAGER_ON_RESUME_STABLE_METHOD = "onResume"
    const val DOMESTIC_SPLASH_MANAGER_STABLE =
        "com.baidu.netdisk.advertise.splash.SplashManager"
    const val DOMESTIC_SPLASH_MANAGER_IS_SHOW_SPLASH_STABLE_METHOD = "isShowSplash"
}
