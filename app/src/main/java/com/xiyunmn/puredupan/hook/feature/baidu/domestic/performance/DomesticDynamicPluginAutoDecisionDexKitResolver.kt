package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticDynamicPluginAutoDecisionDexKitResolver {
    const val AUTO_DOWNLOAD_FACTORY_CACHE_ID = "domestic_dynamic_plugin_auto_download_factory"
    const val AUTO_INSTALL_FACTORY_CACHE_ID = "domestic_dynamic_plugin_auto_install_factory"
    const val AUTO_DOWNLOAD_DECISION_CACHE_ID = "domestic_dynamic_plugin_auto_download_decision"
    const val AUTO_INSTALL_DECISION_CACHE_ID = "domestic_dynamic_plugin_auto_install_decision"

    private const val TAG = "DomesticDynamicPluginAutoDecisionDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5
    private val installedDecisionHooks = ConcurrentHashMap.newKeySet<String>()

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

    private data class DexCallSiteCandidate(
        val className: String,
        val methodName: String,
        val usingStrings: Set<String>,
        val invokeDescriptors: Set<String>,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        val download = resolveAutoDownloadFactory(cl) != null
        val install = resolveAutoInstallFactory(cl) != null
        val downloadDecision = warmUpAutoDownloadDecisionCache(cl)
        val installDecision = warmUpAutoInstallDecisionCache(cl)
        return download || install || downloadDecision || installDecision
    }

    fun warmUpAutoDownloadFactoryCache(cl: ClassLoader): Boolean {
        return resolveAutoDownloadFactory(cl) != null
    }

    fun warmUpAutoInstallFactoryCache(cl: ClassLoader): Boolean {
        return resolveAutoInstallFactory(cl) != null
    }

    fun warmUpAutoDownloadDecisionCache(cl: ClassLoader): Boolean {
        return resolveAutoDownloadDecisionInterfaceMethod(cl) != null
    }

    fun warmUpAutoInstallDecisionCache(cl: ClassLoader): Boolean {
        return resolveAutoInstallDecisionInterfaceMethod(cl) != null
    }

    fun hookResolvedDecisionFactories(
        cl: ClassLoader,
        blockedPluginTypes: Set<Int>,
        logTag: String,
        isEnabled: () -> Boolean,
    ): Int {
        val pluginClass = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL,
            cl,
        ) ?: run {
            XposedCompat.log("[$logTag] dynamic plugin model class NOT FOUND")
            return 0
        }
        val downloadFactory = resolveAutoDownloadFactory(cl)
        val installFactory = resolveAutoInstallFactory(cl)
        if (downloadFactory == null && installFactory == null) return 0
        val downloadDecision = if (downloadFactory != null) {
            resolveAutoDownloadDecisionInterfaceMethod(cl)
        } else {
            null
        }
        val installDecision = if (installFactory != null) {
            resolveAutoInstallDecisionInterfaceMethod(cl)
        } else {
            null
        }
        if (downloadFactory != null && downloadDecision == null) {
            XposedCompat.logD("[$logTag] autoDownload decision interface method unresolved")
        }
        if (installFactory != null && installDecision == null) {
            XposedCompat.logD("[$logTag] autoInstall decision interface method unresolved")
        }

        var installedCount = 0
        for (type in blockedPluginTypes) {
            val plugin = newProbePlugin(pluginClass, type) ?: continue
            val downloader = downloadFactory?.let { invokeFactory(it, plugin) }
            if (downloader != null && downloadDecision != null) {
                installedCount += hookDecisionMethod(
                    decisionOwner = downloader.javaClass,
                    pluginClass = pluginClass,
                    decisionInterfaceMethod = downloadDecision,
                    decisionName = "autoDownload",
                    blockedPluginTypes = blockedPluginTypes,
                    logTag = logTag,
                    isEnabled = isEnabled,
                )
            }
            val executor = installFactory?.let { invokeFactory(it, plugin) }
            if (executor != null && installDecision != null) {
                installedCount += hookDecisionMethod(
                    decisionOwner = executor.javaClass,
                    pluginClass = pluginClass,
                    decisionInterfaceMethod = installDecision,
                    decisionName = "autoInstall",
                    blockedPluginTypes = blockedPluginTypes,
                    logTag = logTag,
                    isEnabled = isEnabled,
                )
            }
        }
        if (installedCount > 0) {
            XposedCompat.log("[$logTag] DexKit dynamic plugin decision hooks INSTALLED: count=$installedCount")
        }
        return installedCount
    }

    private fun resolveAutoDownloadFactory(cl: ClassLoader): Method? {
        return resolveFactory(
            cl = cl,
            cacheId = AUTO_DOWNLOAD_FACTORY_CACHE_ID,
            returnTypeName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_DOWNLOADER,
            label = "autoDownloadFactory",
        )
    }

    private fun resolveAutoDownloadDecisionInterfaceMethod(cl: ClassLoader): Method? {
        return resolveDecisionInterfaceMethod(
            cl = cl,
            cacheId = AUTO_DOWNLOAD_DECISION_CACHE_ID,
            hostClassName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_DOWNLOADER,
            interfaceClassName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_DOWNLOADER,
            callSiteToken = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_AUTO_DOWNLOAD_CALL_SITE_TOKEN,
            label = "autoDownloadDecision",
        )
    }

    private fun resolveAutoInstallDecisionInterfaceMethod(cl: ClassLoader): Method? {
        return resolveDecisionInterfaceMethod(
            cl = cl,
            cacheId = AUTO_INSTALL_DECISION_CACHE_ID,
            hostClassName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_EXECUTOR,
            interfaceClassName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_EXECUTOR,
            callSiteToken = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_AUTO_INSTALL_CALL_SITE_TOKEN,
            label = "autoInstallDecision",
        )
    }

    private fun resolveAutoInstallFactory(cl: ClassLoader): Method? {
        return resolveFactory(
            cl = cl,
            cacheId = AUTO_INSTALL_FACTORY_CACHE_ID,
            returnTypeName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_EXECUTOR,
            label = "autoInstallFactory",
        )
    }

    private fun resolveFactory(
        cl: ClassLoader,
        cacheId: String,
        returnTypeName: String,
        label: String,
    ): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, cacheId) { ref ->
            validateFactoryRef(cl, ref, returnTypeName)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = cacheId) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .paramTypes(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL),
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
        } ?: return null

        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isFactoryShape(returnTypeName)) return@mapNotNull null
            val method = validateFactoryRef(
                cl,
                DexKitCompat.MethodRef(candidate.className, candidate.methodName),
                returnTypeName,
            ) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            compareBy<Pair<DexMethodCandidate, Method>> { it.first.className }
                .thenBy { it.first.methodName },
        )

        val best = matches.singleOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(label, candidates, matches)
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

    private fun resolveDecisionInterfaceMethod(
        cl: ClassLoader,
        cacheId: String,
        hostClassName: String,
        interfaceClassName: String,
        callSiteToken: String,
        label: String,
    ): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, cacheId) { ref ->
            validateDecisionInterfaceRef(cl, ref, interfaceClassName)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = cacheId) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .declaredClass(hostClassName),
                    ),
            ).map { methodData ->
                DexCallSiteCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    usingStrings = methodData.usingStrings.toSet(),
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                )
            }
        } ?: return null

        val callSiteMatches = candidates.filter { candidate ->
            candidate.usingStrings.any { value -> value.contains(callSiteToken) }
        }
        val methodNames = callSiteMatches
            .flatMap { candidate -> candidate.invokedDecisionMethodNames(interfaceClassName) }
            .distinct()
            .sorted()
        val matches = methodNames.mapNotNull { methodName ->
            val method = validateDecisionInterfaceRef(
                cl,
                DexKitCompat.MethodRef(interfaceClassName, methodName),
                interfaceClassName,
            ) ?: return@mapNotNull null
            methodName to method
        }

        val best = matches.singleOrNull()
        if (best == null) {
            val diagnostic = buildDecisionDiagnostic(label, candidates, callSiteMatches, methodNames, matches)
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

    private fun validateFactoryRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
        returnTypeName: String,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val helperClass = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_HELPER, cl)
            ?: return null
        val pluginClass = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL, cl)
            ?: return null
        val returnClass = XposedCompat.findClassOrNull(returnTypeName, cl) ?: return null
        if (!helperClass.isAssignableFrom(clazz)) return null
        val method = XposedCompat.findMethodOrNull(clazz, ref.methodName, pluginClass) ?: return null
        if (Modifier.isStatic(method.modifiers)) return null
        if (!returnClass.isAssignableFrom(method.returnType)) return null
        return method.apply { isAccessible = true }
    }

    private fun validateDecisionInterfaceRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
        interfaceClassName: String,
    ): Method? {
        if (ref.className != interfaceClassName) return null
        val interfaceClass = XposedCompat.findClassOrNull(interfaceClassName, cl) ?: return null
        val pluginClass = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL, cl)
            ?: return null
        if (!interfaceClass.isInterface) return null
        val method = XposedCompat.findMethodOrNull(interfaceClass, ref.methodName, pluginClass) ?: return null
        if (Modifier.isStatic(method.modifiers)) return null
        if (method.returnType != Boolean::class.javaPrimitiveType) return null
        return method.apply { isAccessible = true }
    }

    private fun newProbePlugin(pluginClass: Class<*>, type: Int): Any? {
        return runCatching {
            val plugin = pluginClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            pluginClass.getDeclaredField("type").apply { isAccessible = true }.setInt(plugin, type)
            pluginClass.getDeclaredField("id").apply { isAccessible = true }.set(plugin, "puredupan_probe_$type")
            plugin
        }.getOrElse { t ->
            XposedCompat.logD("[$TAG] create dynamic plugin probe failed: type=$type, ${t.message}")
            null
        }
    }

    private fun invokeFactory(method: Method, plugin: Any): Any? {
        return runCatching {
            val owner = method.declaringClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            method.invoke(owner, plugin)
        }.getOrElse { t ->
            XposedCompat.logD(
                "[$TAG] invoke factory failed: ${method.declaringClass.name}.${method.name}, ${t.message}",
            )
            null
        }
    }

    private fun hookDecisionMethod(
        decisionOwner: Class<*>,
        pluginClass: Class<*>,
        decisionInterfaceMethod: Method,
        decisionName: String,
        blockedPluginTypes: Set<Int>,
        logTag: String,
        isEnabled: () -> Boolean,
    ): Int {
        val mod = XposedCompat.module ?: return 0
        if (!decisionInterfaceMethod.declaringClass.isAssignableFrom(decisionOwner)) {
            XposedCompat.logD(
                "[$logTag] ${decisionOwner.name} does not implement " +
                    decisionInterfaceMethod.declaringClass.name,
            )
            return 0
        }
        val methodName = decisionInterfaceMethod.name
        val method = XposedCompat.findMethodOrNull(decisionOwner, methodName, pluginClass) ?: run {
            XposedCompat.logD("[$logTag] ${decisionOwner.name}.$methodName NOT FOUND")
            return 0
        }
        if (method.returnType != Boolean::class.javaPrimitiveType) return 0
        val installKey = "${decisionOwner.name}#$methodName"
        if (!installedDecisionHooks.add(installKey)) return 0

        mod.hook(method).intercept { chain ->
            val plugin = chain.args.firstOrNull()
            val type = pluginTypeOf(plugin)
            if (isEnabled() && type != null && type in blockedPluginTypes) {
                XposedCompat.logD(
                    "[$logTag] $decisionName blocked by DexKit: " +
                        "type=$type, id=${pluginIdOf(plugin)}",
                )
                false
            } else {
                chain.proceed()
            }
        }
        XposedCompat.logD("[$logTag] DexKit decision hook installed: ${decisionOwner.name}.$methodName")
        return 1
    }

    private fun pluginTypeOf(plugin: Any?): Int? {
        if (plugin == null) return null
        return runCatching { XposedCompat.getObjectField(plugin, "type") as? Int }.getOrNull()
    }

    private fun pluginIdOf(plugin: Any?): String? {
        if (plugin == null) return null
        return runCatching { XposedCompat.getObjectField(plugin, "id") as? String }.getOrNull()
    }

    private fun DexMethodCandidate.isFactoryShape(returnTypeName: String): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            sameType(this.returnTypeName, returnTypeName) &&
            paramTypeNames == listOf(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL)

    private fun DexCallSiteCandidate.invokedDecisionMethodNames(interfaceClassName: String): Set<String> {
        val ownerPrefix = "L${interfaceClassName.replace('.', '/')};->"
        val decisionDescriptor = "(L${BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL.replace('.', '/')};)Z"
        return invokeDescriptors.mapNotNullTo(linkedSetOf()) { descriptor ->
            if (!descriptor.startsWith(ownerPrefix) || !descriptor.contains(decisionDescriptor)) {
                return@mapNotNullTo null
            }
            descriptor.substringAfter("->").substringBefore("(").takeIf { it.isNotBlank() }
        }
    }

    private fun sameType(left: String, right: String): Boolean {
        return left == right || left.replace('$', '.') == right.replace('$', '.')
    }

    private fun buildDiagnostic(
        label: String,
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
    ): String {
        val candidateText = candidates.take(5)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} ${candidate.returnTypeName}(${candidate.paramTypeNames.joinToString()})"
            }
            .ifBlank { "-" }
        val matchText = matches.take(5)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        return buildString {
            append("target=").append(label).append('\n')
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("candidates=\n").append(candidateText).append('\n')
            append("matches=\n").append(matchText)
        }
    }

    private fun buildDecisionDiagnostic(
        label: String,
        candidates: List<DexCallSiteCandidate>,
        callSiteMatches: List<DexCallSiteCandidate>,
        methodNames: List<String>,
        matches: List<Pair<String, Method>>,
    ): String {
        val candidateText = candidates.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} strings=${candidate.usingStrings.size} " +
                    "invokes=${candidate.invokeDescriptors.size}"
            }
            .ifBlank { "-" }
        val callSiteText = callSiteMatches.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} invokes=${candidate.invokeDescriptors.joinToString(limit = 3)}"
            }
            .ifBlank { "-" }
        val methodNameText = methodNames.joinToString().ifBlank { "-" }
        val matchText = matches.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { (_, method) ->
                "${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        return buildString {
            append("target=").append(label).append('\n')
            append("candidateCount=").append(candidates.size).append('\n')
            append("callSiteMatchCount=").append(callSiteMatches.size).append('\n')
            append("methodNames=").append(methodNameText).append('\n')
            append("candidates=\n").append(candidateText).append('\n')
            append("callSites=\n").append(callSiteText).append('\n')
            append("matches=\n").append(matchText)
        }
    }
}
