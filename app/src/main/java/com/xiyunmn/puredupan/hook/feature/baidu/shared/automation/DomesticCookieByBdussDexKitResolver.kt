package com.xiyunmn.puredupan.hook.feature.baidu.shared.automation

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticCookieByBdussDexKitResolver {
    const val CACHE_ID = "domestic_cookie_by_bduss"

    private const val TAG = "DomesticCookieByBdussDexKitResolver"
    private const val PROBE_BDUSS = "__puredupan_cookie_probe__"
    private const val COOKIE_UTILS_TOKEN = "CookiesUtils"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5
    private val COOKIE_OWNER_EVIDENCE_STRINGS = listOf(
        COOKIE_UTILS_TOKEN,
        "getCookieByBduss",
        "BDUSS=",
        "Cookie: BDUSS=",
    )
    private val COOKIE_DETAIL_STRINGS = COOKIE_OWNER_EVIDENCE_STRINGS + listOf(
        "STOKEN",
        "PANPSC",
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val usingStrings: Set<String>,
    )

    private data class DexScanResult(
        val candidates: List<DexMethodCandidate>,
        val evidenceMethods: List<DexMethodCandidate>,
    )

    fun cookieFor(cl: ClassLoader, bduss: String): String {
        val method = resolveCached(cl) ?: return ""
        return invokeCookieMethod(method, bduss)
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolveWithScan(cl) != null
    }

    private fun resolveCached(cl: ClassLoader): Method? {
        return when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateCachedResult(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> cached.value
            DexKitCompat.CachedResult.NotFound,
            DexKitCompat.CachedResult.Miss,
            -> null
        }
    }

    private fun resolveWithScan(cl: ClassLoader): Method? {
        resolveCached(cl)?.let { return it }

        val scan = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val candidates = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .modifiers(Modifier.STATIC)
                            .returnType(String::class.java)
                            .paramTypes(String::class.java),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    usingStrings = methodData.usingStrings.toSet(),
                )
            }
            val evidenceMethods = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .anyOf(
                                COOKIE_OWNER_EVIDENCE_STRINGS.map { token ->
                                    MethodMatcher.create().usingStrings(token)
                                },
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
                    usingStrings = methodData.usingStrings.toSet(),
                )
            }
            DexScanResult(candidates = candidates, evidenceMethods = evidenceMethods)
        } ?: return null

        val evidenceOwnerClasses = (scan.candidates + scan.evidenceMethods)
            .filter { candidate -> candidate.hasCookieEvidence() }
            .mapTo(mutableSetOf()) { candidate -> candidate.className }
        val rejected = mutableListOf<String>()
        val best = scan.candidates.asSequence()
            .filter { candidate ->
                candidate.isCookieHelperShape() &&
                    (candidate.hasCookieEvidence() || candidate.className in evidenceOwnerClasses)
            }
            .mapNotNull { candidate ->
                val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
                candidate to method
            }
            .sortedWith(
                compareByDescending<Pair<DexMethodCandidate, Method>> { score(it.first) }
                    .thenBy { it.first.className }
                    .thenBy { it.first.methodName },
            )
            .firstOrNull()

        if (best == null) {
            val diagnostic = buildNoCandidateDiagnostic(
                methods = scan.candidates,
                evidenceMethods = scan.evidenceMethods,
                evidenceOwnerClasses = evidenceOwnerClasses,
                rejected = rejected,
            )
            XposedCompat.log("[$TAG] no cookie helper candidate matched: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }

        val method = best.second
        XposedCompat.log(
            "[$TAG] resolved cookie helper by DexKit: " +
                "${method.declaringClass.name}.${method.name}, score=${score(best.first)}",
        )
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            CACHE_ID,
            buildResolvedDiagnostic(best.first, method, scan.evidenceMethods),
        )
        return method
    }

    private fun validateCachedResult(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        return runCatching {
            val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return@runCatching null
            val method = XposedCompat.findMethodOrNull(clazz, ref.methodName, String::class.java)
                ?: return@runCatching null
            if (!Modifier.isStatic(method.modifiers)) return@runCatching null
            if (method.returnType != String::class.java) return@runCatching null
            if (!probeLooksLikeBdussCookie(method)) return@runCatching null
            method
        }.getOrElse { t ->
            XposedCompat.logD(
                "[$TAG] cached/candidate validation failed: " +
                    "${ref.className}.${ref.methodName}, ${t.message}",
            )
            null
        }
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        return validateCachedResult(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        ) ?: run {
            rejected += "${candidate.memberName()} rejected: method/probe mismatch, " +
                "evidence=${candidate.evidenceText()}"
            null
        }
    }

    private fun invokeCookieMethod(method: Method, bduss: String): String {
        return runCatching { method.invoke(null, bduss) as? String }
            .getOrElse { t ->
                XposedCompat.logD("[$TAG] invoke cookie helper failed: ${t.message}")
                null
            }
            .orEmpty()
    }

    private fun DexMethodCandidate.isCookieHelperShape(): Boolean =
        !isConstructor &&
            Modifier.isStatic(modifiers) &&
            returnTypeName == "java.lang.String" &&
            paramTypeNames == listOf("java.lang.String")

    private fun DexMethodCandidate.hasCookieEvidence(): Boolean {
        return usingStrings.any { value ->
            COOKIE_OWNER_EVIDENCE_STRINGS.any { token -> value.contains(token, ignoreCase = true) }
        }
    }

    private fun score(candidate: DexMethodCandidate): Int {
        var score = 0
        if (candidate.containsEvidence(COOKIE_UTILS_TOKEN)) score += 200
        if (candidate.containsEvidence("Cookie: BDUSS=")) score += 120
        if (candidate.containsEvidence("BDUSS")) score += 100
        if (candidate.containsEvidence("getCookieByBduss")) score += 80
        if (candidate.containsEvidence("STOKEN")) score += 60
        if (candidate.containsEvidence("PANPSC")) score += 40
        if (candidate.usingStrings.isEmpty()) score -= 10
        return score
    }

    private fun probeLooksLikeBdussCookie(method: Method): Boolean {
        val value = invokeCookieMethod(method, PROBE_BDUSS)
        return value.contains(PROBE_BDUSS) &&
            (value.contains("BDUSS=$PROBE_BDUSS") || value.contains("Cookie: BDUSS=$PROBE_BDUSS"))
    }

    private fun buildResolvedDiagnostic(
        candidate: DexMethodCandidate,
        method: Method,
        evidenceMethods: List<DexMethodCandidate>,
    ): String {
        return buildString {
            append("resolved=").append(method.declaringClass.name).append('.').append(method.name).append('\n')
            append("score=").append(score(candidate)).append('\n')
            append("proof=probe returned BDUSS cookie for synthetic bduss").append('\n')
            append("strings=").append(candidate.evidenceText()).append('\n')
            append("ownerEvidence=").append(ownerEvidenceText(candidate.className, evidenceMethods))
        }
    }

    private fun buildNoCandidateDiagnostic(
        methods: List<DexMethodCandidate>,
        evidenceMethods: List<DexMethodCandidate>,
        evidenceOwnerClasses: Set<String>,
        rejected: List<String>,
    ): String {
        val shaped = methods
            .filter { it.isCookieHelperShape() }
            .sortedByDescending(::score)
        val ownerText = evidenceOwnerClasses
            .sorted()
            .joinToString()
            .ifBlank { "-" }
        val top = shaped
            .take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} score=${score(candidate)} evidence=${candidate.evidenceText()}"
            }
            .ifBlank { "-" }
        val evidenceText = evidenceMethods
            .sortedWith(
                compareByDescending<DexMethodCandidate> { score(it) }
                    .thenBy { it.className }
                    .thenBy { it.methodName },
            )
            .take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} evidence=${candidate.evidenceText()}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected
            .take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n")
            .ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(methods.size).append('\n')
            append("evidenceMethodCount=").append(evidenceMethods.size).append('\n')
            append("shapeMatched=").append(shaped.size).append('\n')
            append("evidenceOwnerClasses=").append(ownerText).append('\n')
            append("evidenceMethods=\n").append(evidenceText).append('\n')
            append("topCandidates=\n").append(top).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }

    private fun DexMethodCandidate.memberName(): String = "$className.$methodName"

    private fun DexMethodCandidate.containsEvidence(token: String): Boolean {
        return usingStrings.any { value -> value.contains(token, ignoreCase = true) }
    }

    private fun DexMethodCandidate.evidenceText(): String {
        return usingStrings
            .filter { value ->
                COOKIE_DETAIL_STRINGS.any { token -> value.contains(token, ignoreCase = true) } ||
                    value.contains("BDUSS", ignoreCase = true) ||
                    value.contains("Cookie", ignoreCase = true)
            }
            .sorted()
            .joinToString(prefix = "[", postfix = "]")
    }

    private fun ownerEvidenceText(
        className: String,
        evidenceMethods: List<DexMethodCandidate>,
    ): String {
        return evidenceMethods
            .filter { candidate -> candidate.className == className }
            .sortedWith(compareByDescending<DexMethodCandidate> { score(it) }.thenBy { it.methodName })
            .take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString(prefix = "[", postfix = "]") { candidate ->
                "${candidate.methodName}:${candidate.evidenceText()}"
            }
    }
}
