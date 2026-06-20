package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.membercard

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.membercard.LegacyMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints

object IntlMemberCardCustomizeHook {
    internal fun hook(cl: ClassLoader) {
        LegacyMemberCardCustomizeHook.hook(
            cl = cl,
            fragmentClassName = BaiduIntlHookPoints.ABOUT_ME_TOP_FRAGMENT,
            onViewCreatedMethodName = BaiduIntlHookPoints.ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD,
        )
    }
}
