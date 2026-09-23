package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class MarketingGuidanceRulesTest {
    private val purchase = "https://pan.baidu.com/wap/svip/guide?scene_id=47"
    private fun payload(url: String? = purchase) = MarketingGuidanceRules.Payload(1, 1, 5, url)

    @Test fun onlyKnownPurchaseOrAdRewardDestinationsQualify() {
        assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload()))
        assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload("https://pan.baidu.com/wap/svip/activities/tenyears/sell?from=guide")))
        assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload("https://pan.baidu.com/wap/svip/privilege/freeget")))
        for (url in listOf(null, "", "https://pan.baidu.com/wap/vip/assets", "https://pan.baidu.com/wap/svip/space/record", "https://pan.baidu.com/s/abc?title=SVIP", "https://example.org/?url=$purchase")) {
            assertFalse(url, MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload(url)))
        }
    }

    @Test fun exactHostRouteAndSingleDecodingLayerAreRequired() {
        val encoded = URLEncoder.encode(purchase, "UTF-8")
        val route = "bdnetdisk://router/half/pay/BusinessPrivilegeProductActivity?url=$encoded"
        assertTrue(MarketingGuidanceRules.isKnownMarketingDestination(route))
        assertTrue(MarketingGuidanceRules.isKnownMarketingDestination("bdnetdisk://router/netdisk/native/RichMediaActivity?url=$encoded"))
        for (url in listOf(
            "https://pan.baidu.com.example.org/wap/svip/guide",
            "https://pan.baidu.com@other.example/wap/svip/guide",
            "https://user@pan.baidu.com/wap/svip/guide",
            "https://pan.baidu.com/wap/svip/guide/other",
            "https://pan.baidu.com/wap/svip/guide#/files",
            "https://pan.baidu.com:444/wap/svip/guide",
            "bdnetdisk://router/unknown?url=$encoded",
            "$route&url=$encoded",
            "bdnetdisk://router/half/pay/BusinessPrivilegeProductActivity?%75rl=https%3A%2F%2Fexample.org&url=$encoded",
            "bdnetdisk://router/half/pay/BusinessPrivilegeProductActivity?url=%GG",
            "bdnetdisk://router/half/pay/BusinessPrivilegeProductActivity?url=${URLEncoder.encode(encoded, "UTF-8")}",
        )) assertFalse(url, MarketingGuidanceRules.isKnownMarketingDestination(url))
    }

    @Test fun marketingTargetAloneDoesNotOverrideSceneOrMessageSemantics() {
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(false, 1, payload()))
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 2, payload()))
        for (scene in listOf(null, 2, 3, 4, 8)) {
            assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(scene = scene)))
        }
        for (feature in listOf(14, 31)) {
            assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(feature = feature)))
        }
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(generativePush = true)))
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(eventName = "ai_task_complete")))
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(eventName = "unknown_event")))
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(eventType = 2)))
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(eventType = 3)))
        assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(eventName = "realtime_close_cashier")))
        assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(eventType = 2, eventName = "realtime_close_cashier")))
        assertFalse(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload(null).copy(eventName = "realtime_close_cashier")))
    }

    @Test fun eachMainTabAndGuideShapeCanBeSelectedIndependently() {
        for (scene in listOf(1, 5, 6, 7)) {
            assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 1, payload().copy(scene = scene)))
            assertTrue(MarketingGuidanceRules.shouldBlockGuidance(true, 2, payload().copy(guideType = 2, scene = scene)))
        }
    }

    @Test fun freeFloatRequiresNativeModeServerPageAndAllowedActivity() {
        fun matches(target: Boolean = true, allows: Boolean = true, status: String? = "1", node: String? = "free_float_window_config", pages: String? = " test.Home, test.Search ", activity: String = "test.Home") =
            MarketingGuidanceRules.shouldBlockFreeFloat(target, allows, status, node, pages, activity)
        assertTrue(matches())
        assertTrue(matches(activity = "test.Search"))
        assertFalse(matches(target = false))
        assertFalse(matches(allows = false))
        assertFalse(matches(status = "0"))
        assertFalse(matches(status = null))
        assertFalse(matches(node = "unknown"))
        assertFalse(matches(pages = null))
        assertFalse(matches(pages = "test.HomeOther"))
        assertFalse(matches(activity = "test.Download"))
    }
}
