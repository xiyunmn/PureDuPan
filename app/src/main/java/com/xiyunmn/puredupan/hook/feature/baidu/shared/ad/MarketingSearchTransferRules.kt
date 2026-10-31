package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduMarketingTransferHookPoints as Points
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object MarketingSearchTransferRules {
    // Dart 将“1”识别为购买完成，空字符串沿用其取消结果。
    const val SEARCH_CANCEL_RESULT = ""

    fun isSearchPurchase(method: Any?, sid: Any?): Boolean =
        method == "showBusinessGuide" && sid is Int && sid in 102..104

    fun isTransferSpace(scene: Any?): Boolean = scene == Points.TRANSFER_SPACE_SCENE

    fun isLimitPromotion(parameters: List<String>): Boolean = parameters == listOf(
        Points.TRANSFER_CALLBACK, "boolean", "boolean",
    ) || parameters == listOf(Points.TRANSFER_CALLBACK, "boolean", "boolean", "java.lang.String")

    fun isLimitEntry(parameters: List<String>): Boolean =
        parameters == listOf(Points.TRANSFER_CALLBACK) ||
            parameters == listOf(Points.TRANSFER_CALLBACK, "java.lang.String")

    fun isOldLimitUpgrade(errno: Int, fileCount: Int, newGuideEnabled: Boolean?): Boolean =
        errno == -33 && fileCount in 1..300_000 && newGuideEnabled == false

    fun isSpaceEntry(parameters: List<String>): Boolean = parameters == listOf(
        Points.FRAGMENT_ACTIVITY, Points.NO_SPACE_BEAN,
    ) || parameters == listOf(Points.FRAGMENT_ACTIVITY, Points.NO_SPACE_BEAN, Points.DIALOG_CALLBACK)

    fun cancellation(callback: Any?, onFailure: (Throwable) -> Unit): (() -> Unit)? {
        if (callback == null) return {}
        val methods = runCatching {
            listOf("onClose", "onDismiss").map { name ->
                callback.javaClass.getMethod(name).takeIf {
                    it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers)
                }?.apply { isAccessible = true } ?: return null
            }
        }.getOrNull() ?: return null
        val completed = AtomicBoolean()
        return {
            if (completed.compareAndSet(false, true)) {
                methods.forEach { method ->
                    runCatching { method.invoke(callback) }.onFailure(onFailure)
                }
            }
        }
    }
}
