package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.domestic

import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduDomesticHookCatalog {
    fun postAttachSpecs(): List<HookSpec> {
        return BaiduDomesticPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduDomesticPostAttachHookSpecs.preAd +
            BaiduDomesticPostAttachHookSpecs.startup +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduDomesticPostAttachHookSpecs.ad +
            BaiduDomesticPostAttachHookSpecs.middleLead +
            BaiduSharedPostAttachHookSpecs.middle +
            BaiduDomesticPostAttachHookSpecs.middleBeforeMyPage +
            BaiduDomesticPostAttachHookSpecs.searchPage +
            BaiduSharedPostAttachHookSpecs.myPage +
            BaiduDomesticPostAttachHookSpecs.memberCard +
            BaiduSharedPostAttachHookSpecs.postMemberLead +
            BaiduDomesticPostAttachHookSpecs.postMember +
            BaiduSharedPostAttachHookSpecs.postMemberTail +
            BaiduDomesticPostAttachHookSpecs.automation +
            BaiduDomesticPostAttachHookSpecs.performance +
            BaiduDomesticPostAttachHookSpecs.tail +
            BaiduDomesticPostAttachHookSpecs.tailEntry
    }
}
