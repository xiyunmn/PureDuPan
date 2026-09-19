package com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/** One advertising-mode decision shared by Navigate, Main, login routing and guides. */
internal object DomesticColdStartModeDexKitResolver {
    const val CACHE_ID = "domestic_cold_start_ad_mode_v1"
    private const val TAG = "DomesticColdStartModeDexKitResolver"
    private const val CONFIG_KEY = "key_main_open_ad_parallel"

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { validateRef(cl, it) }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }
        val mainClass = BaiduFeatureRuntime.currentMainActivityClassName() ?: return null
        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val entries = listOf(BaiduDomesticHookPoints.NAVIGATE_ACTIVITY, mainClass).map { owner ->
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(owner)
                            .name(BaiduDomesticHookPoints.IS_PARALLEL_LOAD_AD)
                            .returnType("boolean").paramCount(0),
                    ),
                ).singleOrNull()
            }
            val mainCalls = entries[1]?.invokes.orEmpty().map { it.descriptor }.toSet()
            entries[0]?.invokes.orEmpty().filter {
                Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" &&
                    it.paramTypeNames.isEmpty() && CONFIG_KEY in it.usingStrings &&
                    it.descriptor in mainCalls
            }.map { DexKitCompat.MethodRef(it.className, it.name) }.distinct()
        }
        if (candidates == null) {
            // Verified readable build fallback; no synchronous DexKit work on startup.
            return validateRef(
                cl,
                DexKitCompat.MethodRef(
                    BaiduDomesticHookPoints.MAIN_PROCESS_INIT_KT,
                    BaiduDomesticHookPoints.MAIN_AD_PARALLEL_IS_OPEN,
                ),
            )
        }
        val method = candidates.singleOrNull()?.let { validateRef(cl, it) }
        if (method == null) {
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "shared ad-mode candidates=${candidates.size}")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }
        DexKitCompat.putCachedMethod(TAG, CACHE_ID, DexKitCompat.MethodRef(method.declaringClass.name, method.name))
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "dexkit:${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val owner = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return owner.declaredMethods.singleOrNull {
            it.name == ref.methodName && Modifier.isStatic(it.modifiers) &&
                it.returnType == Boolean::class.javaPrimitiveType && it.parameterCount == 0
        }?.apply { isAccessible = true }
    }
}
