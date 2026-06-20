package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.SharedAboutMeModuleEntryInstaller
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

internal object SamsungAboutMeModuleEntryHook {
    private const val TAG = "SamsungAboutMeModuleEntryHook"
    private val SCAN_ICON_ID_NAMES = listOf(
        BaiduSamsungHookPoints.ABOUT_ME_SCAN_ICON,
        BaiduSamsungHookPoints.ABOUT_ME_QRCODE_ENTRANCE_ICON,
        BaiduSamsungHookPoints.ABOUT_ME_QRCODE_ENTRANCE_ICON_NEW_POS,
    )

    fun hook(cl: ClassLoader) {
        SharedAboutMeModuleEntryInstaller.hook(
            cl = cl,
            tag = TAG,
            scanIconIdNames = SCAN_ICON_ID_NAMES,
        )
    }
}
