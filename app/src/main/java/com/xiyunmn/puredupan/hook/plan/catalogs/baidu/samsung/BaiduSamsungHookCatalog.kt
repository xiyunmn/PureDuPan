package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduSamsungHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduSamsungPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduSamsungPostAttachHookSpecs.preAd +
            BaiduSamsungPostAttachHookSpecs.startup +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduSamsungPostAttachHookSpecs.ad +
            BaiduSamsungPostAttachHookSpecs.middleLead +
            BaiduSharedPostAttachHookSpecs.middle +
            BaiduSamsungPostAttachHookSpecs.middleBeforeMyPage +
            BaiduSamsungPostAttachHookSpecs.searchPage +
            BaiduSharedPostAttachHookSpecs.myPage +
            BaiduSamsungPostAttachHookSpecs.memberCard +
            BaiduSharedPostAttachHookSpecs.postMemberLead +
            BaiduSamsungPostAttachHookSpecs.postMember +
            BaiduSharedPostAttachHookSpecs.postMemberTail +
            BaiduSamsungPostAttachHookSpecs.performance +
            BaiduSamsungPostAttachHookSpecs.tail +
            BaiduSamsungPostAttachHookSpecs.tailEntry
    }
}
