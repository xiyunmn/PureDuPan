package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticUpdateDialogDexKitResolver {
    const val CACHE_ID = "domestic_update_dialog_show_lc_version"

    private const val TAG = "DomesticUpdateDialogDexKitResolver"
    private const val VERSION_UPDATE_HELPER_TAG = "VersionUpdateHelper"

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl) != null
    }

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .returnType(Void.TYPE)
                            .paramTypes(
                                listOf(
                                    Activity::class.java.name,
                                    BaiduDomesticHookPoints.UPDATE_INFO,
                                    BaiduDomesticHookPoints.PRIORITY_DIALOG_INFO,
                                    String::class.java.name,
                                ),
                            ),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                )
            }
        } ?: return resolveStableFallback(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isShowDialogShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.className == BaiduDomesticHookPoints.VERSION_UPDATE_HELPER) 1 else 0
            }.thenBy { it.first.className }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] showLCVersionDialog unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveStableFallback(cl)
        }

        val method = best.second
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        XposedCompat.log("[$TAG] resolved showLCVersionDialog: ${method.declaringClass.name}.${method.name}")
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        return validateRef(
            cl,
            DexKitCompat.MethodRef(
                BaiduDomesticHookPoints.VERSION_UPDATE_HELPER,
                BaiduDomesticHookPoints.VERSION_UPDATE_HELPER_SHOW_LC_VERSION_DIALOG_METHOD,
            ),
        )?.also { method ->
            DexKitCompat.markTargetSuccess(
                TAG,
                CACHE_ID,
                "fallback:${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validateRef(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        )
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: helper/signature mismatch"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isVersionUpdateHelperClass(clazz, cl)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isShowDialogMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun isVersionUpdateHelperClass(clazz: Class<*>, cl: ClassLoader): Boolean {
        if (clazz.name == BaiduDomesticHookPoints.VERSION_UPDATE_HELPER) return true
        val helperInterface = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.ILC_UPDATE_HELPER, cl)
            ?: return false
        if (!helperInterface.isAssignableFrom(clazz)) return false
        return clazz.declaredFields.any { field ->
            Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java &&
                runCatching {
                    field.isAccessible = true
                    field.get(null) == VERSION_UPDATE_HELPER_TAG
                }.getOrDefault(false)
        }
    }

    private fun DexMethodCandidate.isShowDialogShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName == "void" &&
            paramTypeNames == listOf(
                Activity::class.java.name,
                BaiduDomesticHookPoints.UPDATE_INFO,
                BaiduDomesticHookPoints.PRIORITY_DIALOG_INFO,
                String::class.java.name,
            )

    private fun isShowDialogMethod(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 4 &&
            method.parameterTypes[0].name == Activity::class.java.name &&
            method.parameterTypes[1].name == BaiduDomesticHookPoints.UPDATE_INFO &&
            method.parameterTypes[2].name == BaiduDomesticHookPoints.PRIORITY_DIALOG_INFO &&
            method.parameterTypes[3] == String::class.java

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(5)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} ${candidate.returnTypeName}(${candidate.paramTypeNames.joinToString()})"
            }
            .ifBlank { "-" }
        val topMatches = matches.take(5)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(5).joinToString("\n").ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
