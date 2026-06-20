package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.SharedAboutMeModuleEntryInstaller

internal object SamsungAboutMeModuleEntryHook {
    private const val TAG = "SamsungAboutMeModuleEntryHook"

    fun hook(cl: ClassLoader) {
        SharedAboutMeModuleEntryInstaller.hook(cl, TAG)
    }
}
