package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.cn

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.domestic.BaiduDomesticPostAttachHookSpecs
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduCnHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduCnPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduDomesticPostAttachHookSpecs.preAd +
            BaiduCnPostAttachHookSpecs.startup +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduCnPostAttachHookSpecs.ad +
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
            BaiduCnPostAttachHookSpecs.performance +
            BaiduDomesticPostAttachHookSpecs.tail +
            BaiduDomesticPostAttachHookSpecs.tailEntry
    }
}
