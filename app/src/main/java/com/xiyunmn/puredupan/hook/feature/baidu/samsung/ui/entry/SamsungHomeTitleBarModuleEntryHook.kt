package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.SharedHomeTitleBarModuleEntryInstaller
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

internal object SamsungHomeTitleBarModuleEntryHook {
    private const val TAG = "SamsungHomeTitleBarModuleEntryHook"
    private const val FRAGMENT_CLASS_NAME = BaiduSamsungHookPoints.NEW_FEED_HOME_TITLE_BAR_FRAGMENT
    private val UPLOAD_ENTRY_ID_NAMES = listOf(
        BaiduSamsungHookPoints.HOME_UPLOAD_ENTRY_GUIDE_END,
        BaiduSamsungHookPoints.HOME_UPLOAD_ENTRY,
    )

    fun hook(cl: ClassLoader) {
        SharedHomeTitleBarModuleEntryInstaller.hook(
            cl = cl,
            tag = TAG,
            fragmentClassName = FRAGMENT_CLASS_NAME,
            uploadEntryIdNames = UPLOAD_ENTRY_ID_NAMES,
        )
    }
}
