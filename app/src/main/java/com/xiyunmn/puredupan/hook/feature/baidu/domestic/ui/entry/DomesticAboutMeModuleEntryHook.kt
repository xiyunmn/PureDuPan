package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.entry

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.SharedAboutMeModuleEntryInstaller

internal object DomesticAboutMeModuleEntryHook {
    private const val TAG = "DomesticAboutMeModuleEntryHook"

    fun hook(cl: ClassLoader) {
        SharedAboutMeModuleEntryInstaller.hook(cl, TAG)
    }
}
