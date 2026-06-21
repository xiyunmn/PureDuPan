package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.cn.BaiduCnPostAttachHookSpecs
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduSamsungHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduSamsungPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduCnPostAttachHookSpecs.preAd +
            BaiduSamsungPostAttachHookSpecs.startup +
            BaiduSharedPostAttachHookSpecs.splashBypass +
            BaiduSamsungPostAttachHookSpecs.ad +
            BaiduCnPostAttachHookSpecs.middleLead +
            BaiduSharedPostAttachHookSpecs.middle +
            BaiduCnPostAttachHookSpecs.middleBeforeMyPage +
            BaiduCnPostAttachHookSpecs.searchPage +
            BaiduSharedPostAttachHookSpecs.myPage +
            BaiduCnPostAttachHookSpecs.memberCard +
            BaiduSharedPostAttachHookSpecs.postMemberLead +
            BaiduCnPostAttachHookSpecs.postMember +
            BaiduSharedPostAttachHookSpecs.postMemberTail +
            BaiduSamsungPostAttachHookSpecs.performance +
            BaiduCnPostAttachHookSpecs.tail +
            BaiduCnPostAttachHookSpecs.tailEntry
    }
}
