package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import android.webkit.WebViewClient
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/** 通过离线包构造契约定位 H5 客户端，保留旧版未混淆入口。 */
internal object IntlOfflinePackageClientDexKitResolver {
    const val CACHE_ID = "intl_offline_package_client_v1"
    private const val TAG = "IntlOfflinePackageClientDexKitResolver"
    private const val PACKAGE_INFO = "com.baidu.netdisk.offline.packages.model.OfflinePackageInfo"
    private val packageParams = listOf("android.app.Activity", "android.webkit.WebViewClient", PACKAGE_INFO)
    private val urlParams = listOf("android.app.Activity", "android.webkit.WebViewClient", "java.lang.String")

    fun resolve(cl: ClassLoader): Class<*>? {
        validate(cl, BaiduIntlHookPoints.FAST_WEB_VIEW_CLIENT)?.let {
            DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "stable:${it.name}")
            return it
        }
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            if (ref.methodName == "<init>") validate(cl, ref.className) else null
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }
        val owners = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create().matcher(
                    MethodMatcher.create().name("<init>").returnType("void")
                        .paramTypes(*packageParams.toTypedArray()),
                ),
            ).map { it.className }.distinct()
        } ?: return null
        val matches = owners.mapNotNull { validate(cl, it) }
        val owner = matches.singleOrNull()
        if (owner == null) {
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "validatedCandidateCount=${matches.size}")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }
        DexKitCompat.putCachedMethod(TAG, CACHE_ID, DexKitCompat.MethodRef(owner.name, "<init>"))
        return owner
    }

    private fun validate(cl: ClassLoader, name: String): Class<*>? {
        val owner = XposedCompat.findClassOrNull(name, cl) ?: return null
        if (!WebViewClient::class.java.isAssignableFrom(owner)) return null
        val constructors = owner.declaredConstructors.map { constructor ->
            constructor.parameterTypes.map(Class<*>::getName)
        }
        if (packageParams !in constructors || urlParams !in constructors) return null
        return owner
    }
}
