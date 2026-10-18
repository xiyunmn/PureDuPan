package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduStartupSplashHookPoints {
    const val NAVIGATE_ACTIVITY = "com.baidu.netdisk.ui.Navigate"
    const val NAVIGATE_CONTENT_ID = "content"
    const val DEFAULT_MAIN_ACTIVITY = "com.baidu.netdisk.ui.DefaultMainActivity"
    const val SPLASH_VIEW = "android.window.SplashScreenView"
    const val SPLASH_BUILDER = "$SPLASH_VIEW\$Builder"
    const val SHELL_SPLASH_BUILDER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$SplashViewBuilder"
    const val SHAPE_ICON_FACTORY = "$SHELL_SPLASH_BUILDER\$ShapeIconFactory"
    const val ICON_MASK_BACKGROUND = "com.android.wm.shell.startingsurface.SplashscreenIconDrawableFactory\$MaskBackgroundDrawable"
    const val COLOR_OS_STARTING_WINDOW = "com.android.wm.shell.startingsurface.OplusShellStartingWindowManager"
}
