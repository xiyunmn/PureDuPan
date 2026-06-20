package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.SharedHomeTitleBarModuleEntryInstaller
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

internal object SamsungHomeTitleBarModuleEntryHook {
    private const val TAG = "SamsungHomeTitleBarModuleEntryHook"
    private const val FRAGMENT_CLASS_NAME = BaiduSamsungHookPoints.NEW_FEED_HOME_TITLE_BAR_FRAGMENT

    fun hook(cl: ClassLoader) {
        SharedHomeTitleBarModuleEntryInstaller.hook(cl, TAG, FRAGMENT_CLASS_NAME)
    }
}
