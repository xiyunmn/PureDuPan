package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.entry

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.entry.SharedHomeTitleBarModuleEntryInstaller
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime

internal object DomesticHomeTitleBarModuleEntryHook {
    private const val TAG = "DomesticHomeTitleBarModuleEntryHook"

    fun hook(cl: ClassLoader) {
        val fragmentClassName = BaiduFeatureRuntime
            .currentHomeCustomizeHookPoints()
            .searchboxFragmentClassName
        if (fragmentClassName == null) {
            XposedCompat.log("[$TAG] HomeSearchboxFragment host capability missing")
            return
        }
        SharedHomeTitleBarModuleEntryInstaller.hook(cl, TAG, fragmentClassName)
    }
}
