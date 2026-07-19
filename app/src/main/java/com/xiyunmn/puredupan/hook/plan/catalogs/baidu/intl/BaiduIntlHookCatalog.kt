package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.intl

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduIntlHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduIntlPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduIntlPostAttachHookSpecs.hotStart +
            BaiduIntlPostAttachHookSpecs.ad +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduSharedPostAttachHookSpecs.middle +
            BaiduIntlPostAttachHookSpecs.home +
            BaiduSharedPostAttachHookSpecs.myPage +
            BaiduIntlPostAttachHookSpecs.memberCard +
            BaiduIntlPostAttachHookSpecs.searchPage +
            BaiduSharedPostAttachHookSpecs.postMemberLead +
            BaiduIntlPostAttachHookSpecs.bottomBar +
            BaiduSharedPostAttachHookSpecs.postMemberTail +
            BaiduIntlPostAttachHookSpecs.theme +
            BaiduIntlPostAttachHookSpecs.automation +
            BaiduIntlPostAttachHookSpecs.startup +
            BaiduIntlPostAttachHookSpecs.performance +
            BaiduIntlPostAttachHookSpecs.tailEntry
    }
}
