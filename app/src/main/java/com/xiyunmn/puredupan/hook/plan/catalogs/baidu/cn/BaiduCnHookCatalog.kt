package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.cn

import com.xiyunmn.puredupan.hook.plan.HookCatalog
import com.xiyunmn.puredupan.hook.plan.HookSpec
import com.xiyunmn.puredupan.hook.plan.catalogs.baidu.domestic.BaiduDomesticHookCatalog

internal object BaiduCnHookCatalog : HookCatalog {
    override fun postAttachSpecs(): List<HookSpec> {
        return BaiduDomesticHookCatalog.postAttachSpecs()
    }
}
