package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduMarketingTransferHookPoints as Points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingSearchTransferRulesTest {
    @Test
    fun searchPurchaseCancellationDoesNotMatchOtherFlutterCallsOrGrantSuccess() {
        for (sid in 102..104) assertTrue(MarketingSearchTransferRules.isSearchPurchase("showBusinessGuide", sid))
        for (sid in listOf(null, 57, 101, 105, "102", 102L)) {
            assertFalse(MarketingSearchTransferRules.isSearchPurchase("showBusinessGuide", sid))
        }
        assertFalse(MarketingSearchTransferRules.isSearchPurchase("showBuyVipPage", 102))
        assertFalse(MarketingSearchTransferRules.isSearchPurchase("applySearchService", 102))
        assertEquals("", MarketingSearchTransferRules.SEARCH_CANCEL_RESULT)
    }

    @Test
    fun transferSpaceRequiresExactSceneAndBothCurrentHostSignatures() {
        assertTrue(MarketingSearchTransferRules.isTransferSpace("save_file_no_space_guide"))
        for (scene in listOf(null, "workspace", "video", "save_file_no_space_guide_other")) {
            assertFalse(MarketingSearchTransferRules.isTransferSpace(scene))
        }
        val intl = listOf(Points.FRAGMENT_ACTIVITY, Points.NO_SPACE_BEAN)
        assertTrue(MarketingSearchTransferRules.isSpaceEntry(intl))
        assertTrue(MarketingSearchTransferRules.isSpaceEntry(intl + Points.DIALOG_CALLBACK))
        assertFalse(MarketingSearchTransferRules.isSpaceEntry(intl + Points.FUNCTION0))
    }

    @Test
    fun quantityContractKeepsCouponAndUnsupportedCountBranchesSeparate() {
        val promotion = listOf(Points.TRANSFER_CALLBACK, "boolean", "boolean")
        assertTrue(MarketingSearchTransferRules.isLimitPromotion(promotion))
        assertTrue(MarketingSearchTransferRules.isLimitPromotion(promotion + "java.lang.String"))
        assertFalse(MarketingSearchTransferRules.isLimitPromotion(listOf(Points.FRAGMENT_ACTIVITY, Points.TRANSFER_CALLBACK)))
        assertFalse(MarketingSearchTransferRules.isLimitEntry(promotion))
        assertTrue(MarketingSearchTransferRules.isOldLimitUpgrade(-33, 300_000, false))
        assertFalse(MarketingSearchTransferRules.isOldLimitUpgrade(-33, 300_001, false))
        assertFalse(MarketingSearchTransferRules.isOldLimitUpgrade(-33, 12, true))
        assertFalse(MarketingSearchTransferRules.isOldLimitUpgrade(-33, 12, null))
        assertFalse(MarketingSearchTransferRules.isOldLimitUpgrade(-32, 12, false))
    }

    @Test
    fun cancelFinishesNativeCallbacksOnceWithoutBuyingOrUsingCoupons() {
        val target = Callback()
        val errors = mutableListOf<Throwable>()
        val cancel = MarketingSearchTransferRules.cancellation(target) { errors += it }!!
        cancel()
        cancel()
        assertEquals(listOf("close", "dismiss"), target.events)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun dismissStillRunsAfterCloseThrowsAndUnknownCallbackIsRejected() {
        val target = Callback(failClose = true)
        val errors = mutableListOf<Throwable>()
        MarketingSearchTransferRules.cancellation(target) { errors += it }!!.invoke()
        assertEquals(listOf("close", "dismiss"), target.events)
        assertEquals(1, errors.size)
        assertNull(MarketingSearchTransferRules.cancellation(PurchaseOnly()) { errors += it })
    }

    private class Callback(private val failClose: Boolean = false) {
        val events = mutableListOf<String>()
        fun onClose() {
            events += "close"
            if (failClose) error("native close failed")
        }
        fun onDismiss() { events += "dismiss" }
        fun onBuyProductSuccess(value: String) { events += "purchase:$value" }
        fun onOtherAction() { events += "coupon" }
    }

    private class PurchaseOnly {
        fun onBuyProductSuccess(value: String) = value
    }
}
