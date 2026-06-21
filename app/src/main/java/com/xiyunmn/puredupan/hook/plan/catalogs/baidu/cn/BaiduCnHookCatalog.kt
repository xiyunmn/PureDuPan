package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.cn

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduCnHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduCnPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduCnPostAttachHookSpecs.preAd +
            BaiduCnPostAttachHookSpecs.startup +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduCnPostAttachHookSpecs.ad +
            BaiduCnPostAttachHookSpecs.middleLead +
            BaiduSharedPostAttachHookSpecs.middle +
            BaiduCnPostAttachHookSpecs.middleBeforeMyPage +
            BaiduCnPostAttachHookSpecs.searchPage +
            BaiduSharedPostAttachHookSpecs.myPage +
            BaiduCnPostAttachHookSpecs.memberCard +
            BaiduSharedPostAttachHookSpecs.postMemberLead +
            BaiduCnPostAttachHookSpecs.postMember +
            BaiduSharedPostAttachHookSpecs.postMemberTail +
            BaiduCnPostAttachHookSpecs.performance +
            BaiduCnPostAttachHookSpecs.tail +
            BaiduCnPostAttachHookSpecs.tailEntry
    }
}
