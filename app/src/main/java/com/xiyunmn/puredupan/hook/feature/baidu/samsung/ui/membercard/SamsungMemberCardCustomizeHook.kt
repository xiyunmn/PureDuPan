package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.membercard

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.membercard.LegacyMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

object SamsungMemberCardCustomizeHook {
    internal fun hook(cl: ClassLoader) {
        LegacyMemberCardCustomizeHook.hook(
            cl = cl,
            fragmentClassName = BaiduSamsungHookPoints.ABOUT_ME_TOP_FRAGMENT,
            onViewCreatedMethodName = BaiduSamsungHookPoints.ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD,
        )
    }
}
