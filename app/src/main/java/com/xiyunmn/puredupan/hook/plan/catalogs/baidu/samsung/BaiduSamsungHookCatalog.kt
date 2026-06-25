package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.domestic.BaiduDomesticPostAttachHookSpecs
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduSamsungHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduSamsungPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduDomesticPostAttachHookSpecs.preAd +
            BaiduSamsungPostAttachHookSpecs.startup +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduSamsungPostAttachHookSpecs.ad +
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
            BaiduSamsungPostAttachHookSpecs.performance +
            BaiduDomesticPostAttachHookSpecs.tail +
            BaiduDomesticPostAttachHookSpecs.tailEntry
    }
}
