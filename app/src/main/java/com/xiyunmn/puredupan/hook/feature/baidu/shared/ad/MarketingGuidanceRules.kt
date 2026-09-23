package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import java.net.URI
import java.net.URLDecoder

/** Only destinations whose host implementation opens a membership purchase or ad-reward page. */
internal object MarketingGuidanceRules {
    data class Payload(
        val guideType: Int?,
        val scene: Int?,
        val feature: Int?,
        val scheme: String?,
        val eventType: Int? = null,
        val eventName: String? = null,
        val generativePush: Boolean = false,
    )

    private val mainScenes = setOf(1, 5, 6, 7)
    private val realtimeEvents = setOf("realtime_click_download", "realtime_close_cashier")
    private val purchasePaths = setOf(
        "/wap/svip/guide",
        "/wap/svip/activities/tenyears/sell",
        "/wap/svip/privilege/freeget",
    )
    private val webRoutes = setOf(
        "/half/pay/BusinessPrivilegeProductActivity",
        "/netdisk/native/BusinessPopupWebActivity",
        "/netdisk/native/RichMediaActivity",
    )

    fun shouldBlockGuidance(onMainPage: Boolean, expectedGuideType: Int, payload: Payload): Boolean {
        if (!onMainPage || expectedGuideType !in 1..2 || payload.guideType != expectedGuideType) return false
        if (payload.scene !in mainScenes || payload.generativePush) return false
        // The host treats these as backup notices and KMP search results respectively.
        if (payload.feature == 14 || payload.feature == 31) return false
        if (payload.eventType != null && payload.eventType !in 1..2) return false
        if (payload.eventType == 2 && payload.eventName !in realtimeEvents) return false
        if (!payload.eventName.isNullOrEmpty() && payload.eventName !in realtimeEvents) return false
        return isKnownMarketingDestination(payload.scheme)
    }

    fun shouldBlockFreeFloat(
        onTargetPage: Boolean,
        hostAllowsFloat: Boolean,
        switchStatus: String?,
        nodeKey: String?,
        configuredPages: String?,
        activityClassName: String,
    ): Boolean = onTargetPage && hostAllowsFloat && switchStatus == "1" &&
        nodeKey == "free_float_window_config" && !configuredPages.isNullOrBlank() &&
        configuredPages.split(',').any { it.trim() == activityClassName }

    internal fun isKnownMarketingDestination(value: String?): Boolean {
        if (value.isNullOrBlank() || value.length > 8192) return false
        val uri = parse(value) ?: return false
        if (isPurchasePage(uri)) return true
        if (uri.scheme != "bdnetdisk" || uri.host != "router" ||
            uri.userInfo != null || uri.port != -1 || !uri.rawFragment.isNullOrEmpty() ||
            uri.rawPath !in webRoutes
        ) return false
        val targets = runCatching {
            uri.rawQuery.orEmpty().split('&').mapNotNull { parameter ->
                val pair = parameter.split('=', limit = 2)
                if (pair.size != 2 || URLDecoder.decode(pair[0], "UTF-8") != "url") null
                else URLDecoder.decode(pair[1], "UTF-8")
            }
        }.getOrNull() ?: return false
        // Do not recursively interpret arbitrary routes, duplicate URLs, or extra decoding layers.
        return targets.singleOrNull()?.let(::parse)?.let(::isPurchasePage) == true
    }

    private fun parse(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private fun isPurchasePage(uri: URI): Boolean =
        uri.scheme == "https" && uri.host == "pan.baidu.com" && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && uri.rawPath in purchasePaths && uri.rawFragment.isNullOrEmpty()
}
