package com.xiyunmn.puredupan.hook.feature.startup.hotstart.intl

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.core.DexKitCompat
import com.xiyunmn.puredupan.hook.core.XposedCompat
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlHotStartSplashDexKitResolver {
    data class ResolveResult(
        val className: String,
        val methodName: String,
    )

    private val signatureHints = listOf(
        "hot_start_filter_ad",
        "onResume startActivity",
        "onResume disable show activity",
        "returnToYun.isAdFiltered",
        "SplashManager().limitCountAdShow()",
        "onPauseByScreenLock",
        "com.baidu.netdisk.advertise.ui.SplashAdActivity",
    )

    fun resolve(cl: ClassLoader): ResolveResult? {
        if (!ConfigManager.isExperimentalDexKitEnabled) {
            XposedCompat.logD("[IntlHotStartSplashDexKitResolver] skipped: config disabled")
            return null
        }

        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateCachedResult(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val methods = DexKitCompat.withBridge(TAG, cl) { bridge ->
                bridge.setThreadNum(1)

                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .returnType(Boolean::class.javaPrimitiveType!!)
                                .paramTypes(android.app.Activity::class.java)
                                .usingEqStrings(signatureHints),
                        ),
                )
        } ?: return null

        val best = methods
            .filter {
                !it.isConstructor &&
                    it.returnTypeName == "boolean" &&
                    it.paramTypeNames == listOf("android.app.Activity")
            }
            .sortedWith(
                compareByDescending<org.luckypray.dexkit.result.MethodData> { score(it) }
                    .thenBy { it.className }
                    .thenBy { it.name },
            )
            .firstOrNull()

        if (best == null) {
            XposedCompat.log("[IntlHotStartSplashDexKitResolver] no candidate matched")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }

        XposedCompat.log(
            "[$TAG] resolved ${best.className}.${best.name} score=${score(best)}",
        )
        val result = ResolveResult(
            className = best.className,
            methodName = best.name,
        )
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(result.className, result.methodName),
        )
        return result
    }

    private fun validateCachedResult(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): ResolveResult? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val method = XposedCompat.findMethodOrNull(
            clazz,
            ref.methodName,
            android.app.Activity::class.java,
        ) ?: return null
        if (method.returnType != Boolean::class.javaPrimitiveType) return null
        return ResolveResult(ref.className, ref.methodName)
    }

    private fun score(method: org.luckypray.dexkit.result.MethodData): Int {
        val usingStrings = method.usingStrings.toSet()
        var score = 0
        signatureHints.forEachIndexed { index, hint ->
            if (hint in usingStrings) {
                score += 100 - index
            }
        }
        return score
    }

    private const val TAG = "IntlHotStartSplashDexKitResolver"
    private const val CACHE_ID = "intl_hot_start_splash"
}
