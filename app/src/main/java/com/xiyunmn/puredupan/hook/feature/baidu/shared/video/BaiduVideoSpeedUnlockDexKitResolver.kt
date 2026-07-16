package com.xiyunmn.puredupan.hook.feature.baidu.shared.video

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduVideoSpeedHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * Resolves video-speed privilege gates under weak and strong obfuscation.
 *
 * Strategy:
 * 1. Prefer stable method names when present (weak domestic builds).
 * 2. Fall back to string/call-shape DexKit scans for renamed Present methods.
 * 3. Resolve obfuscated [VideoPrivilege] via Kotlin Metadata when needed.
 */
internal object BaiduVideoSpeedUnlockDexKitResolver {
    const val ONLINE_ENABLE_CACHE_ID = "baidu_video_speed_online_enable_v2"
    const val HAS_PRIVILEGE_CACHE_ID = "baidu_video_speed_has_privilege_v2"
    const val VIDEO_PRIVILEGE_ONLINE_CACHE_ID = "baidu_video_speed_privilege_online_v1"
    const val VIDEO_PRIVILEGE_PANEL_CACHE_ID = "baidu_video_speed_privilege_panel_v1"

    private const val TAG = "BaiduVideoSpeedUnlockDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5
    private const val KOTLIN_METADATA = "kotlin.Metadata"
    private val BOOLEAN_INVOKE_PATTERN = Regex("""->.+\(Z\)Z""")

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val usingStrings: Set<String>,
        val invokeDescriptors: Set<String>,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        val online = resolveIsSpeedUpOnlineEnable(cl) != null
        val privilege = resolveHasSpeedPrivilege(cl) != null
        val onlineGate = resolveVideoPrivilegeOnlineSpeedEnable(cl) != null
        val panelGate = resolveVideoPrivilegeSpeedEnable(cl) != null
        return online || privilege || onlineGate || panelGate
    }

    fun resolveIsSpeedUpOnlineEnable(cl: ClassLoader): Method? {
        resolveDirectPresentMethod(
            cl,
            BaiduVideoSpeedHookPoints.IS_SPEED_UP_ONLINE_ENABLE_METHOD,
        )?.let { return it }

        return resolveCachedOrScan(
            cl = cl,
            cacheId = ONLINE_ENABLE_CACHE_ID,
            matcherFactory = {
                MethodMatcher.create()
                    .declaredClass(BaiduVideoSpeedHookPoints.VIDEO_SPEED_UP_PRESENT)
                    .usingStrings(BaiduVideoSpeedHookPoints.E_VIDEO_PRIVILEGE_KEY)
            },
            accept = { candidate ->
                candidate.isPresentBooleanGateShape() &&
                    candidate.usingStrings.contains(BaiduVideoSpeedHookPoints.E_VIDEO_PRIVILEGE_KEY)
            },
            label = "isSpeedUpOnlineEnable",
        )
    }

    fun resolveHasSpeedPrivilege(cl: ClassLoader): Method? {
        resolveDirectPresentMethod(
            cl,
            BaiduVideoSpeedHookPoints.HAS_SPEED_PRIVILEGE_METHOD,
        )?.let { return it }

        return resolveCachedOrScan(
            cl = cl,
            cacheId = HAS_PRIVILEGE_CACHE_ID,
            matcherFactory = {
                MethodMatcher.create()
                    .declaredClass(BaiduVideoSpeedHookPoints.VIDEO_SPEED_UP_PRESENT)
                    .returnType(Boolean::class.javaPrimitiveType!!)
                    .paramCount(0)
            },
            accept = { candidate ->
                candidate.isPresentBooleanGateShape() &&
                    !candidate.usingStrings.contains(BaiduVideoSpeedHookPoints.E_VIDEO_PRIVILEGE_KEY) &&
                    candidate.looksLikeHasSpeedPrivilege()
            },
            label = "hasSpeedPrivilege",
        )
    }

    fun resolveVideoPrivilegeOnlineSpeedEnable(cl: ClassLoader): Method? {
        resolveDirectVideoPrivilegeMethod(
            cl = cl,
            methodName = BaiduVideoSpeedHookPoints.ONLINE_SPEED_ENABLE_METHOD,
            paramCount = 1,
            firstParamBoolean = true,
        )?.let { return it }

        return resolveVideoPrivilegeMethod(
            cl = cl,
            cacheId = VIDEO_PRIVILEGE_ONLINE_CACHE_ID,
            paramCount = 1,
            firstParamBoolean = true,
            label = "onLineSpeedEnable",
        )
    }

    fun resolveVideoPrivilegeSpeedEnable(cl: ClassLoader): Method? {
        resolveDirectVideoPrivilegeMethod(
            cl = cl,
            methodName = BaiduVideoSpeedHookPoints.SPEED_ENABLE_METHOD,
            paramCount = 1,
            firstParamBoolean = false,
        )?.let { return it }

        return resolveVideoPrivilegeMethod(
            cl = cl,
            cacheId = VIDEO_PRIVILEGE_PANEL_CACHE_ID,
            paramCount = 1,
            firstParamBoolean = false,
            label = "speedEnable",
        )
    }

    private fun resolveDirectPresentMethod(cl: ClassLoader, methodName: String): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduVideoSpeedHookPoints.VIDEO_SPEED_UP_PRESENT,
            cl,
        ) ?: return null
        return XposedCompat.findMethodOrNull(clazz, methodName)?.takeIf(::isPresentBooleanGate)
    }

    private fun resolveDirectVideoPrivilegeMethod(
        cl: ClassLoader,
        methodName: String,
        paramCount: Int,
        firstParamBoolean: Boolean,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduVideoSpeedHookPoints.VIDEO_PRIVILEGE, cl)
            ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == methodName &&
                isVideoPrivilegeBooleanMethod(method, paramCount, firstParamBoolean)
        }?.apply { isAccessible = true }
    }

    private fun resolveVideoPrivilegeMethod(
        cl: ClassLoader,
        cacheId: String,
        paramCount: Int,
        firstParamBoolean: Boolean,
        label: String,
    ): Method? {
        when (
            val cached = DexKitCompat.getCachedMethod(TAG, cacheId) { ref ->
                validateVideoPrivilegeRef(cl, ref, paramCount, firstParamBoolean)
            }
        ) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }
        if (DexKitCompat.shouldSkipScan(TAG, cacheId)) return null

        val ownerClasses = DexKitCompat.withBridge(TAG, cl, resolverId = cacheId) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create().matcher(videoPrivilegeOwnerMatcher()),
            ).map { it.name }
        } ?: return null

        val rejected = mutableListOf<String>()
        val matches = ownerClasses.flatMap { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@flatMap emptyList()
            if (!isVideoPrivilegeOwner(clazz)) return@flatMap emptyList()
            clazz.declaredMethods.mapNotNull { method ->
                if (!isVideoPrivilegeBooleanMethod(method, paramCount, firstParamBoolean)) {
                    return@mapNotNull null
                }
                method.isAccessible = true
                method
            }
        }

        val best = matches.singleOrNull()
            ?: matches.firstOrNull()?.takeIf { matches.size == 1 }
        if (best == null) {
            val diagnostic = buildString {
                append("ownerClasses=").append(ownerClasses.joinToString().ifBlank { "-" }).append('\n')
                append("matchCount=").append(matches.size).append('\n')
                append("rejected=\n").append(rejected.joinToString("\n").ifBlank { "-" })
            }
            XposedCompat.logW("[$TAG] $label unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, cacheId, diagnostic)
            DexKitCompat.putCachedMethod(TAG, cacheId, null)
            return null
        }

        XposedCompat.log("[$TAG] resolved $label: ${best.declaringClass.name}.${best.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            cacheId,
            DexKitCompat.MethodRef(best.declaringClass.name, best.name),
        )
        return best
    }

    private fun resolveCachedOrScan(
        cl: ClassLoader,
        cacheId: String,
        matcherFactory: () -> MethodMatcher,
        accept: (DexMethodCandidate) -> Boolean,
        label: String,
    ): Method? {
        when (
            val cached = DexKitCompat.getCachedMethod(TAG, cacheId) { ref ->
                validatePresentRef(cl, ref)
            }
        ) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }
        if (DexKitCompat.shouldSkipScan(TAG, cacheId)) return null

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = cacheId) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create().matcher(matcherFactory()),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    usingStrings = methodData.usingStrings.toSet(),
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                )
            }
        } ?: return null

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!accept(candidate)) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedByDescending { score(it.first, label) }

        val topScore = matches.firstOrNull()?.let { score(it.first, label) } ?: Int.MIN_VALUE
        val top = matches.filter { score(it.first, label) == topScore }
        val best = top.singleOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] $label unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, cacheId, diagnostic)
            DexKitCompat.putCachedMethod(TAG, cacheId, null)
            return null
        }

        val method = best.second
        XposedCompat.log("[$TAG] resolved $label: ${method.declaringClass.name}.${method.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            cacheId,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validatePresentRef(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        )
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validatePresentRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isPresentOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isPresentBooleanGate(method)
        }?.apply { isAccessible = true }
    }

    private fun validateVideoPrivilegeRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
        paramCount: Int,
        firstParamBoolean: Boolean,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isVideoPrivilegeOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                isVideoPrivilegeBooleanMethod(method, paramCount, firstParamBoolean)
        }?.apply { isAccessible = true }
    }

    private fun isPresentOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduVideoSpeedHookPoints.VIDEO_SPEED_UP_PRESENT) return true
        if (clazz.simpleName == BaiduVideoSpeedHookPoints.PRESENT_SIMPLE_NAME) return true
        return KotlinMetadataUtils.metadataContainsAll(
            clazz,
            listOf(
                BaiduVideoSpeedHookPoints.PRESENT_METADATA_TOKEN,
                BaiduVideoSpeedHookPoints.HAS_SPEED_PRIVILEGE_METHOD,
                BaiduVideoSpeedHookPoints.IS_SPEED_UP_ONLINE_ENABLE_METHOD,
            ),
        ) || KotlinMetadataUtils.metadataContainsAll(
            clazz,
            listOf(
                BaiduVideoSpeedHookPoints.HAS_SPEED_PRIVILEGE_METHOD,
                BaiduVideoSpeedHookPoints.IS_SPEED_UP_ONLINE_ENABLE_METHOD,
            ),
        )
    }

    private fun isVideoPrivilegeOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduVideoSpeedHookPoints.VIDEO_PRIVILEGE) return true
        if (clazz.simpleName == BaiduVideoSpeedHookPoints.VIDEO_PRIVILEGE_SIMPLE_NAME) return true
        return KotlinMetadataUtils.metadataContainsAll(
            clazz,
            listOf(BaiduVideoSpeedHookPoints.VIDEO_PRIVILEGE_METADATA_TOKEN),
        ) || KotlinMetadataUtils.metadataContainsAll(
            clazz,
            listOf(
                BaiduVideoSpeedHookPoints.VIDEO_PRIVILEGE_SIMPLE_NAME,
                BaiduVideoSpeedHookPoints.ONLINE_SPEED_ENABLE_METHOD,
                BaiduVideoSpeedHookPoints.SPEED_ENABLE_METHOD,
            ),
        )
    }

    private fun DexMethodCandidate.isPresentBooleanGateShape(): Boolean {
        return !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            paramTypeNames.isEmpty() &&
            (returnTypeName == "boolean" || returnTypeName == "java.lang.Boolean") &&
            (
                className == BaiduVideoSpeedHookPoints.VIDEO_SPEED_UP_PRESENT ||
                    className.endsWith(".${BaiduVideoSpeedHookPoints.PRESENT_SIMPLE_NAME}")
                )
    }

    private fun DexMethodCandidate.looksLikeHasSpeedPrivilege(): Boolean {
        if (methodName == BaiduVideoSpeedHookPoints.HAS_SPEED_PRIVILEGE_METHOD) return true
        // Strong/intl: calls VideoPrivilege.onLineSpeedEnable(boolean) or enterprise hasPrivilege(int).
        val invokesBooleanGate = invokeDescriptors.any { descriptor ->
            BOOLEAN_INVOKE_PATTERN.containsMatchIn(descriptor)
        }
        val invokesEnterprise = invokeDescriptors.any { descriptor ->
            descriptor.contains("hasPrivilege") ||
                descriptor.contains("Enterprise")
        }
        return invokesBooleanGate || invokesEnterprise
    }

    private fun isPresentBooleanGate(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            isBooleanLikeReturn(method.returnType)
    }

    private fun isVideoPrivilegeBooleanMethod(
        method: Method,
        paramCount: Int,
        firstParamBoolean: Boolean,
    ): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (!isBooleanLikeReturn(method.returnType)) return false
        if (method.parameterTypes.size != paramCount) return false
        if (paramCount == 0) return true
        val first = method.parameterTypes[0]
        return if (firstParamBoolean) {
            first == Boolean::class.javaPrimitiveType || first == Boolean::class.javaObjectType
        } else {
            // speedEnable(SpeedPanelUIState)
            first.name == BaiduVideoSpeedHookPoints.SPEED_PANEL_UI_STATE ||
                first.simpleName == BaiduVideoSpeedHookPoints.SPEED_PANEL_UI_STATE_SIMPLE_NAME
        }
    }

    private fun isBooleanLikeReturn(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType ||
            type == Boolean::class.javaObjectType ||
            type.name == "java.lang.Boolean"
    }

    private fun videoPrivilegeOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    addString(BaiduVideoSpeedHookPoints.VIDEO_PRIVILEGE_SIMPLE_NAME)
                                    addString(BaiduVideoSpeedHookPoints.ONLINE_SPEED_ENABLE_METHOD)
                                    addString(BaiduVideoSpeedHookPoints.SPEED_ENABLE_METHOD)
                                },
                            ),
                    ),
            )
    }

    private fun score(candidate: DexMethodCandidate, label: String): Int {
        var score = 0
        if (candidate.className == BaiduVideoSpeedHookPoints.VIDEO_SPEED_UP_PRESENT) score += 40
        if (candidate.methodName == label) score += 35
        if (candidate.usingStrings.contains(BaiduVideoSpeedHookPoints.E_VIDEO_PRIVILEGE_KEY)) {
            score += if (label == "isSpeedUpOnlineEnable") 30 else -20
        }
        if (candidate.looksLikeHasSpeedPrivilege()) score += 25
        if (candidate.usingStrings.contains(BaiduVideoSpeedHookPoints.PRIVILEGE_MEDIA_SPEED_ENABLE_STAT_KEY)) {
            score -= 20
        }
        if (candidate.invokeDescriptors.any { BOOLEAN_INVOKE_PATTERN.containsMatchIn(it) }) {
            score += 15
        }
        return score
    }

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} strings=${candidate.usingStrings.size} " +
                    "invokes=${candidate.invokeDescriptors.size}"
            }
            .ifBlank { "-" }
        val topMatches = matches.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n")
            .ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
