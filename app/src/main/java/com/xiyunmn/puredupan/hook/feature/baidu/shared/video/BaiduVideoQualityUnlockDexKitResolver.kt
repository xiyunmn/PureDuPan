package com.xiyunmn.puredupan.hook.feature.baidu.shared.video

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduVideoQualityHookPoints
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
 * Resolves video-quality privilege gates under weak and strong obfuscation.
 *
 * Strategy (project convention):
 * 1. DexKit cache / scan first.
 * 2. Stable class/method names only as verified fallback.
 * 3. Only quality-related methods are resolved; SVIP / high-speed / ad-skip stay out of scope.
 */
internal object BaiduVideoQualityUnlockDexKitResolver {
    const val VIDEO_PRIVILEGE_OWNER_CACHE_ID = "baidu_video_quality_privilege_owner_v1"
    const val VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID = "baidu_video_quality_privilege_methods_v2"

    private const val TAG = "BaiduVideoQualityUnlockDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"
    private const val OWNER_SENTINEL_METHOD = "<owner>"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

    // intl 13.11.9 R8 剥离 @Metadata 后改用 SpeedPanelUIState 方法形状扫描 owner。
    private fun isIntlHost(): Boolean = BaiduFeatureRuntime.isCurrentIntlHost()

    // fallback 只允许跨版本明文类；混淆 owner 必须由 DexKit 动态发现。
    private fun videoPrivilegeClassCandidates(): List<String> =
        listOf(BaiduVideoQualityHookPoints.VIDEO_PRIVILEGE)

    private val QUALITY_METHOD_NAMES = setOf(
        BaiduVideoQualityHookPoints.CAN_PLAY_720_METHOD,
        BaiduVideoQualityHookPoints.IS_SUPPORT_FHD_METHOD,
        BaiduVideoQualityHookPoints.PLAY_HD_ENABLED_METHOD,
        BaiduVideoQualityHookPoints.PLAY_FHD_ENABLED_METHOD,
        BaiduVideoQualityHookPoints.PLAY_ORIGINAL_ENABLED_METHOD,
    )

    private val QUALITY_PRIVILEGE_INVOKE_TOKENS = listOf(
        BaiduVideoQualityHookPoints.PRIVILEGE_VIDEO_PLAY_HD_METHOD,
        BaiduVideoQualityHookPoints.PRIVILEGE_VIDEO_PLAY_FHD_METHOD,
        BaiduVideoQualityHookPoints.PRIVILEGE_VIDEO_PLAY_ORIGINAL_METHOD,
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val invokeDescriptors: Set<String> = emptySet(),
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        val owner = resolveVideoPrivilegeOwner(cl) != null
        val qualityMethods = resolveVideoPrivilegeQualityMethods(cl).isNotEmpty()
        return owner || qualityMethods
    }

    fun resolveVideoPrivilegeOwner(cl: ClassLoader): Class<*>? {
        when (
            val cached = DexKitCompat.getCachedMethod(TAG, VIDEO_PRIVILEGE_OWNER_CACHE_ID) { ref ->
                if (ref.methodName != OWNER_SENTINEL_METHOD) return@getCachedMethod null
                XposedCompat.findClassOrNull(ref.className, cl)?.takeIf { isVideoPrivilegeOwner(it) }
            }
        ) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound ->
                return markStableOwnerFallback {
                    resolveDirectVideoPrivilegeOwner(cl)
                }
            DexKitCompat.CachedResult.Miss -> Unit
        }
        if (DexKitCompat.shouldSkipScan(TAG, VIDEO_PRIVILEGE_OWNER_CACHE_ID)) {
            return markStableOwnerFallback {
                resolveDirectVideoPrivilegeOwner(cl)
            }
        }

        val owners = DexKitCompat.withBridge(TAG, cl, resolverId = VIDEO_PRIVILEGE_OWNER_CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create().matcher(videoPrivilegeOwnerMatcher()),
            ).map { it.name }
        } ?: return markStableOwnerFallback {
            resolveDirectVideoPrivilegeOwner(cl)
        }

        val matched = owners.mapNotNull { className ->
            XposedCompat.findClassOrNull(className, cl)?.takeIf { isVideoPrivilegeOwner(it) }
        }
        val best = matched.singleOrNull()
        if (best == null) {
            val diagnostic = "ownerCandidates=${owners.joinToString().ifBlank { "-" }} matchCount=${matched.size}"
            DexKitCompat.markTargetScanMiss(TAG, VIDEO_PRIVILEGE_OWNER_CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, VIDEO_PRIVILEGE_OWNER_CACHE_ID, null)
            return markStableOwnerFallback {
                resolveDirectVideoPrivilegeOwner(cl)
            }
        }

        XposedCompat.log("[$TAG] resolved VideoPrivilege owner: ${best.name}")
        DexKitCompat.putCachedMethod(
            TAG,
            VIDEO_PRIVILEGE_OWNER_CACHE_ID,
            DexKitCompat.MethodRef(best.name, OWNER_SENTINEL_METHOD),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            VIDEO_PRIVILEGE_OWNER_CACHE_ID,
            "dexkit:${best.name}",
        )
        return best
    }

    fun resolveVideoPrivilegeQualityMethods(cl: ClassLoader): List<Method> {
        val owner = resolveVideoPrivilegeOwner(cl) ?: return emptyList()

        when (
            val cached = DexKitCompat.getCachedMethod(TAG, VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID) { ref ->
                if (ref.className != owner.name) return@getCachedMethod null
                val names = ref.methodName.split(',').filter { it.isNotBlank() }
                if (names.isEmpty()) return@getCachedMethod null
                val methods = names.mapNotNull { name ->
                    owner.declaredMethods.firstOrNull { method ->
                        method.name == name &&
                            !Modifier.isStatic(method.modifiers) &&
                            method.parameterTypes.isEmpty() &&
                            isBooleanLikeReturn(method.returnType)
                    }?.apply { isAccessible = true }
                }
                methods.takeIf { it.size == names.size }
            }
        ) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound ->
                return markStableQualityMethodsFallback(owner)
            DexKitCompat.CachedResult.Miss -> Unit
        }
        if (DexKitCompat.shouldSkipScan(TAG, VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID)) {
            return markStableQualityMethodsFallback(owner)
        }

        // DexKit path: scan no-arg boolean methods on the owner and keep those that
        // invoke quality privilege tokens (covers both weak names and strong short names).
        val candidates = DexKitCompat.withBridge(
            TAG,
            cl,
            resolverId = VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID,
        ) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .declaredClass(owner.name)
                            .returnType(Boolean::class.javaPrimitiveType!!)
                            .paramCount(0),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                )
            }
        } ?: return markStableQualityMethodsFallback(owner)

        val matches = candidates.mapNotNull { candidate ->
            if (candidate.isConstructor || Modifier.isStatic(candidate.modifiers)) return@mapNotNull null
            if (candidate.paramTypeNames.isNotEmpty()) return@mapNotNull null
            if (candidate.returnTypeName != "boolean" && candidate.returnTypeName != "java.lang.Boolean") {
                return@mapNotNull null
            }
            // Accept either stable quality names or invoke-shape quality gates.
            val isNamedQuality = candidate.methodName in QUALITY_METHOD_NAMES
            val isInvokeQuality = candidate.invokesQualityPrivilege() &&
                !candidate.invokesNonQualityPrivilege()
            if (!isNamedQuality && !isInvokeQuality) return@mapNotNull null
            val method = owner.declaredMethods.firstOrNull { declared ->
                declared.name == candidate.methodName &&
                    !Modifier.isStatic(declared.modifiers) &&
                    declared.parameterTypes.isEmpty() &&
                    isBooleanLikeReturn(declared.returnType)
            }?.apply { isAccessible = true } ?: return@mapNotNull null
            candidate to method
        }

        val methods = matches
            .map { it.second }
            .distinctBy { "${it.declaringClass.name}.${it.name}" }
        if (methods.isEmpty()) {
            val diagnostic = buildDiagnostic(
                candidates = candidates,
                matches = emptyList(),
                rejected = listOf("no quality gate methods resolved by DexKit"),
            )
            DexKitCompat.markTargetScanMiss(TAG, VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID, null)
            return markStableQualityMethodsFallback(owner)
        }

        val joinedNames = methods.joinToString(",") { it.name }
        XposedCompat.log(
            "[$TAG] resolved VideoPrivilege quality methods: " +
                methods.joinToString { "${it.declaringClass.name}.${it.name}" },
        )
        DexKitCompat.putCachedMethod(
            TAG,
            VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID,
            DexKitCompat.MethodRef(owner.name, joinedNames),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID,
            "dexkit:${methods.joinToString { "${it.declaringClass.name}.${it.name}" }}",
        )
        return methods
    }

    private fun markStableQualityMethodsFallback(owner: Class<*>): List<Method> {
        val named = owner.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    isBooleanLikeReturn(method.returnType) &&
                    method.name in QUALITY_METHOD_NAMES
            }
            .onEach { method -> method.isAccessible = true }
        if (named.isEmpty()) return emptyList()
        DexKitCompat.putCachedMethod(
            TAG,
            VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID,
            DexKitCompat.MethodRef(owner.name, named.joinToString(",") { it.name }),
        )
        DexKitCompat.markTargetSuccess(
            TAG,
            VIDEO_PRIVILEGE_QUALITY_METHODS_CACHE_ID,
            "fallback:${named.joinToString { "${it.declaringClass.name}.${it.name}" }}",
        )
        XposedCompat.log(
            "[$TAG] fallback quality methods: " +
                named.joinToString { "${it.declaringClass.name}.${it.name}" },
        )
        return named
    }

    private fun markStableOwnerFallback(stableFallback: () -> Class<*>?): Class<*>? {
        return stableFallback()?.also { owner ->
            DexKitCompat.putCachedMethod(
                TAG,
                VIDEO_PRIVILEGE_OWNER_CACHE_ID,
                DexKitCompat.MethodRef(owner.name, OWNER_SENTINEL_METHOD),
            )
            DexKitCompat.markTargetSuccess(
                TAG,
                VIDEO_PRIVILEGE_OWNER_CACHE_ID,
                "fallback:${owner.name}",
            )
            XposedCompat.log("[$TAG] fallback VideoPrivilege owner: ${owner.name}")
        }
    }

    private fun markStableFallback(
        cacheId: String,
        stableFallback: () -> Method?,
    ): Method? {
        return stableFallback()?.also { method ->
            DexKitCompat.putCachedMethod(
                TAG,
                cacheId,
                DexKitCompat.MethodRef(method.declaringClass.name, method.name),
            )
            XposedCompat.log(
                "[$TAG] fallback $cacheId: ${method.declaringClass.name}.${method.name}",
            )
        }
    }

    private fun resolveDirectVideoPrivilegeOwner(cl: ClassLoader): Class<*>? {
        // 仅允许明文 VideoPrivilege fallback；混淆 owner 由 DexKit 形状扫描。
        for (className in videoPrivilegeClassCandidates()) {
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (isVideoPrivilegeOwner(clazz)) return clazz
        }
        return null
    }

    private fun DexMethodCandidate.invokesQualityPrivilege(): Boolean {
        return invokeDescriptors.any { descriptor ->
            QUALITY_PRIVILEGE_INVOKE_TOKENS.any { token -> descriptor.contains(token) }
        }
    }

    private fun DexMethodCandidate.invokesNonQualityPrivilege(): Boolean {
        val blocked = listOf(
            "privilegeVideoHighSpeedChannelEnabled",
            "privilegeMediaSpeedEnable",
            "privilegeVideoMarkEnabled",
            "privilegeVideoToAudioEnabled",
            "isSVip",
        )
        return invokeDescriptors.any { descriptor ->
            blocked.any { token -> descriptor.contains(token) }
        }
    }

    private fun isVideoPrivilegeOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduVideoQualityHookPoints.VIDEO_PRIVILEGE) return true
        if (clazz.simpleName == BaiduVideoQualityHookPoints.VIDEO_PRIVILEGE_SIMPLE_NAME) return true
        return KotlinMetadataUtils.metadataContainsAllOrAbsent(
            clazz,
            listOf(BaiduVideoQualityHookPoints.VIDEO_PRIVILEGE_METADATA_TOKEN),
        ) || KotlinMetadataUtils.metadataContainsAll(
            clazz,
            listOf(
                BaiduVideoQualityHookPoints.VIDEO_PRIVILEGE_SIMPLE_NAME,
                BaiduVideoQualityHookPoints.CAN_PLAY_720_METHOD,
                BaiduVideoQualityHookPoints.IS_SUPPORT_FHD_METHOD,
            ),
        )
    }

    private fun isBooleanLikeReturn(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType ||
            type == Boolean::class.javaObjectType ||
            type.name == "java.lang.Boolean"
    }

    private fun videoPrivilegeOwnerMatcher(): ClassMatcher {
        // intl 13.11.9 R8 剥离 @Metadata、类名混淆为 sz0.a：改用 `boolean X(SpeedPanelUIState)`
        // 形状锚（intl 全 APK 仅 VideoPrivilege 等价类声明此形状，与倍速 owner 同类）。
        // cn/samsung 保留明文 VideoPrivilege + @Metadata d2 原路径，不受影响。
        if (isIntlHost()) {
            return ClassMatcher.create()
                .addMethod(
                    MethodMatcher.create()
                        .returnType(Boolean::class.javaPrimitiveType!!)
                        .paramTypes(BaiduVideoQualityHookPoints.SPEED_PANEL_UI_STATE),
                )
        }
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    addString(BaiduVideoQualityHookPoints.VIDEO_PRIVILEGE_SIMPLE_NAME)
                                    addString(BaiduVideoQualityHookPoints.CAN_PLAY_720_METHOD)
                                    addString(BaiduVideoQualityHookPoints.IS_SUPPORT_FHD_METHOD)
                                    addString(BaiduVideoQualityHookPoints.PLAY_ORIGINAL_ENABLED_METHOD)
                                },
                            ),
                    ),
            )
    }

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { it.memberName() }
            .ifBlank { "-" }
        val topMatches = matches.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(MAX_DIAGNOSTIC_CANDIDATES).joinToString("\n").ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
