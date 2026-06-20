package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared.BaiduSharedPostAttachHookSpecs

internal object BaiduSamsungHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduSamsungPostAttachHookSpecs.entry +
            BaiduSharedPostAttachHookSpecs.preAd +
            BaiduSamsungPostAttachHookSpecs.startup +
            BaiduSamsungPostAttachHookSpecs.ad +
            BaiduSharedPostAttachHookSpecs.middle +
            BaiduSharedPostAttachHookSpecs.myPage +
            BaiduSamsungPostAttachHookSpecs.myPage +
            BaiduSamsungPostAttachHookSpecs.memberCard +
            BaiduSharedPostAttachHookSpecs.postMemberLead +
            BaiduSharedPostAttachHookSpecs.postMemberTail +
            BaiduSamsungPostAttachHookSpecs.tailEntry
    }
}
