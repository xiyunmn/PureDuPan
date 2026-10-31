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
    const val AUTO_INSTALL_DECISION_CACHE_ID = "domestic_dynamic_plugin_auto_install_decision"

    private const val TAG = "DomesticDynamicPluginAutoDecisionDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5
    private val installedDecisionHooks = ConcurrentHashMap.newKeySet<String>()

    private data class DexCallSiteCandidate(
        val className: String,
        val methodName: String,
        val usingStrings: Set<String>,
        val invokeDescriptors: Set<String>,
    ) {
        fun memberName(): String = "$className.$methodName"
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
            returnTypeName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_DOWNLOADER,
            stableMethodName = "getDownloader",
        )
    }

    private fun resolveAutoDownloadDecisionInterfaceMethod(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_DOWNLOADER, cl)
            ?: return null
        val plugin = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL, cl)
            ?: return null
        return owner.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) && method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.contentEquals(arrayOf(plugin))
        }?.apply { isAccessible = true }
    }

    private fun resolveAutoInstallDecisionInterfaceMethod(cl: ClassLoader): Method? {
        val ref = DexKitCompat.MethodRef(
            BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_EXECUTOR,
            "isAutoInstall",
        )
        validateDecisionInterfaceRef(cl, ref, ref.className)?.let { method ->
            DexKitCompat.putCachedMethod(TAG, AUTO_INSTALL_DECISION_CACHE_ID, ref)
            DexKitCompat.markTargetSuccess(TAG, AUTO_INSTALL_DECISION_CACHE_ID, "direct:${ref.className}.${ref.methodName}")
            return method
        }
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
            returnTypeName = BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_SUB_EXECUTOR,
            stableMethodName = "getExecutor",
        )
    }

    private fun resolveFactory(
        cl: ClassLoader,
        returnTypeName: String,
        stableMethodName: String,
    ): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_HELPER_STABLE_CLASS, cl)
            ?: return null
        val helper = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_HELPER, cl)
            ?: return null
        val plugin = XposedCompat.findClassOrNull(BaiduDomesticDexKitHookPoints.DYNAMIC_PLUGIN_MODEL, cl)
            ?: return null
        val result = XposedCompat.findClassOrNull(returnTypeName, cl) ?: return null
        if (!helper.isAssignableFrom(owner)) return null
        val matches = owner.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && !it.isBridge &&
                it.parameterTypes.contentEquals(arrayOf(plugin)) && result.isAssignableFrom(it.returnType)
        }
        return (matches.singleOrNull { it.name == stableMethodName } ?: matches.singleOrNull())
            ?.apply { isAccessible = true }
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
            DexKitCompat.markTargetScanMiss(TAG, cacheId, diagnostic)
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
