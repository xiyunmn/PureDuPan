package com.xiyunmn.puredupan.hook.feature.baidu.shared.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.HostPackages
import com.xiyunmn.puredupan.hook.storage.StorageDestination
import com.xiyunmn.puredupan.hook.storage.StorageDestinationResolver
import com.xiyunmn.puredupan.hook.storage.StoragePathRules
import com.xiyunmn.puredupan.hook.storage.StorageRedirectConfig
import com.xiyunmn.puredupan.hook.storage.StorageRedirectSnapshot
import com.xiyunmn.puredupan.hook.storage.StorageRootGuard
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStorageHookPoints
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import com.xiyunmn.puredupan.hook.runtime.ModuleLifetime

/** 共享存储重定向逻辑，按宿主解析不同符号。 */
object StorageRedirectHook {
    // 适配缺失时使用宿主目录；目标授权或路径校验失败时不静默回退。
    private const val TAG = "StorageRedirectHook"
    private val installState = HookState()
    private var installedTargetCount = 0
    private val installedPathMethods = mutableSetOf<Method>()
    private var sdkInstalled = false
    private val rootGuardInstalled = AtomicBoolean(false)
    private var downloadItemContext = ThreadLocal<Array<Any?>>()
    private val downloadUnavailableNotified = AtomicBoolean(false)
    private val outerPathUnavailableNotified = AtomicBoolean(false)
    @Volatile private var downloadBindingsReady = false
    @Volatile private var folderBindingsReady = false
    @Volatile private var uriCreatorReady = false
    private var internationalStorageMode: Method? = null
    private var pathGate = ReentrantReadWriteLock()

    internal fun exportPathState(): Array<Any?> = arrayOf(pathGate, downloadItemContext)

    @Suppress("UNCHECKED_CAST")
    internal fun restorePathState(state: Array<*>) {
        pathGate = state[0] as ReentrantReadWriteLock
        downloadItemContext = state[1] as ThreadLocal<Array<Any?>>
    }

    fun hook(cl: ClassLoader) = pathGate.write { installHooks(cl) }

    private fun installHooks(cl: ClassLoader) {
        if (!HookSettings.isStorageRedirectEnabled && !HookSettings.isStorageRootGuardEnabled) {
            XposedCompat.logD("[$TAG] skipped: all storage options disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        val international = XposedCompat.currentPackageName() == HostPackages.BAIDU_INTL
        if (!international && !installState.markInstalled()) return
        if (HookSettings.isStorageRedirectEnabled && HookSettings.isStorageDownloadRedirectEnabled) {
            try {
                if (international) {
                    installInternationalDownloadBindings(mod, cl)
                } else {
                    val partitionStorage: Method? = null
                    internationalStorageMode = partitionStorage
                    val installed = mutableSetOf<String>()
                    val extensionOwners = listOf(BaiduStorageHookPoints.DOMESTIC_DOWNLOAD_EXTENSION)
                        .filter { XposedCompat.findClassOrNull(it, cl) != null }
                    val pathNames = setOf("getDownloadPath", "getLinkDownloadPath")
                    // 各组独立安装，按全部关键入口的结果决定是否启用重定向。
                    val extensions = extensionOwners.sumOf { owner -> installDownloadBinding(owner) {
                        hookDownloadExtension(mod, cl, owner, pathNames, partitionStorage, installed)
                    } }
                    val creators = installDownloadBinding("URI creator") {
                        hookUriCreator(
                            mod, cl,
                            BaiduStorageHookPoints.DOMESTIC_TARGET30_STORAGE,
                            BaiduStorageHookPoints.URI_CREATOR,
                        )
                    }
                    val folderOwners = listOf(BaiduStorageHookPoints.FOLDER_TASK_STATE_UTIL to null)
                        .filter { XposedCompat.findClassOrNull(it.first, cl) != null }
                    val folders = folderOwners.sumOf { (owner, folderType) -> installDownloadBinding(owner) {
                        hookFolderTaskBinding(mod, cl, owner, folderType).also {
                            if (it > 0) installed.add(owner)
                        }
                    } }
                    val legacyOwner = BaiduStorageHookPoints.DEFAULT_SETTING
                    val legacyNames = setOf(BaiduStorageHookPoints.DEFAULT_SAVE_DIR)
                    val legacy = installDownloadBinding(legacyOwner) {
                        hookLegacyDefaultSaveDir(mod, cl, legacyOwner, legacyNames, installed)
                    }
                    uriCreatorReady = creators > 0
                    downloadBindingsReady = extensionOwners.isNotEmpty() &&
                        extensionOwners.all { owner -> pathNames.all { "$owner#$it" in installed } } &&
                        uriCreatorReady
                    val missingLegacy = legacyNames.filter { "$legacyOwner#$it" !in installed }
                    folderBindingsReady = folderOwners.isNotEmpty() && folderOwners.all { it.first in installed }
                    XposedCompat.logD("[$TAG] download bindings: creators=$creators extensions=$extensions folders=$folders legacy=$legacy")
                    val context = HookSettings.appContext()
                    val active = isDownloadRedirectActive(context)
                    val removeOuter = active && shouldRemoveOuterPath(context)
                    // BT 等入口独立读取默认目录，普通下载扩展可直接返回目标路径。
                    val legacyState = when (missingLegacy.size) {
                        0 -> if (active) "redirect" else "host"
                        legacyNames.size -> "unavailable"
                        else -> "partial"
                    }
                    XposedCompat.logD(
                        "[$TAG] download capabilities: primary=${if (active) "redirect" else "host"} " +
                            "uriCreator=$uriCreatorReady legacy=$legacyState removeOuter=$removeOuter " +
                            "extensions=$extensionOwners folders=${folderOwners.map { it.first }} " +
                            "bindingFailure=host-directory targetFailure=reject",
                    )
                    if (missingLegacy.isNotEmpty()) {
                        XposedCompat.logW("[$TAG] legacy directory bindings missing: owner=$legacyOwner methods=$missingLegacy")
                        if (active) showFailure(context, "部分下载可能使用宿主目录")
                    }
                    installDownloadBinding("download path queries") { hookStoragePathQueries(
                        mod,
                        cl,
                        BaiduStorageHookPoints.DOMESTIC_TARGET30_STORAGE,
                        emptySet(),
                        setOf(BaiduStorageHookPoints.QUERY_ABSOLUTE_PATH),
                        setOf(BaiduStorageHookPoints.QUERY_PATH),
                    ) }
                }
            } catch (error: Throwable) {
                // 已安装的入口保持旁路，继续安装 SDK 和根目录保护。
                downloadBindingsReady = false
                notifyDownloadUnavailable(HookSettings.appContext())
                XposedCompat.logW("[$TAG] download bindings failed: ${error.message}")
            }
        }
        if (HookSettings.isStorageRedirectEnabled && HookSettings.isStorageReaderSdkRedirectEnabled) {
            if (!sdkInstalled) sdkInstalled = installDownloadBinding("SDK public directory") { hookSdkPublicDirectory(mod, cl) } > 0
        }
        if (HookSettings.isStorageRootGuardEnabled) {
            installDownloadBinding("root directory protection") { hookRootGuard(mod) }
        }
        if (installedTargetCount == 0) {
            rootGuardInstalled.set(false)
            installState.reset()
            XposedCompat.logW("[$TAG] no targets installed; installation may retry")
        } else {
            XposedCompat.log("[$TAG] hook INSTALLED targets=$installedTargetCount")
        }
    }

    private fun installInternationalDownloadBindings(mod: XposedModule, cl: ClassLoader) {
        val bindings = IntlStorageBindingsResolver.resolve(cl)
        val installed = mutableSetOf<String>()
        val mode = bindings["mode"]
        internationalStorageMode = mode
        val extensions = (if (bindings.primaryReady) listOfNotNull(bindings["download"], bindings["link"]) else emptyList()).sumOf { method ->
            installDownloadBinding("download extension") {
                hookDownloadExtension(mod, cl, method.declaringClass.name, setOf(method.name), mode, installed, listOf(method))
            }
        }
        val creators = bindings["creator"]?.let { method -> installDownloadBinding("URI creator") {
            hookUriCreator(mod, cl, method.declaringClass.name, method.name, listOf(method))
        } } ?: 0
        val folders = if (bindings.folderReady) bindings["folder"]?.let { method -> installDownloadBinding("folder task") {
            hookFolderTaskBinding(mod, cl, method.declaringClass.name, method.parameterTypes[2].name,
                listOf(method), bindings["parent"], bindings["name"])
        } } ?: 0 else 0
        val legacy = listOfNotNull(bindings["legacy"], bindings["default"]).sumOf { method -> installDownloadBinding("legacy directory") {
            hookLegacyDefaultSaveDir(mod, cl, method.declaringClass.name, setOf(method.name), installed, listOf(method))
        } }
        uriCreatorReady = creators > 0
        downloadBindingsReady = bindings.primaryReady && extensions == 2
        folderBindingsReady = folders > 0
        listOf("absolute", "file", "directory").forEach { role -> bindings[role]?.let { method ->
            installDownloadBinding("path query $role") {
                hookStoragePathQueries(mod, cl, method.declaringClass.name,
                    if (role == "absolute") setOf(method.name) else emptySet(),
                    if (role == "file") setOf(method.name) else emptySet(),
                    if (role == "directory") setOf(method.name) else emptySet(), listOf(method))
            }
        } }
        XposedCompat.logD("[$TAG] international download bindings: creators=$creators extensions=$extensions folders=$folders legacy=$legacy")
        if (legacy < 2 && downloadBindingsReady) showFailure(HookSettings.appContext(), "部分下载可能使用宿主目录")
    }

    /** 嵌套路径调用使用同一代上下文，安装期间只暂停这些同步调用。 */
    private fun XposedInterface.HookBuilder.interceptPath(hooker: XposedInterface.Hooker) =
        intercept { chain -> pathGate.read { hooker.intercept(chain) } }

    private fun snapshot(): StorageRedirectSnapshot {
        val host = XposedCompat.currentPackageName().orEmpty()
        return StorageRedirectSnapshot(
            host,
            StorageRedirectConfig(
                enabled = HookSettings.isStorageRedirectEnabled,
                downloadRedirectEnabled = HookSettings.isStorageDownloadRedirectEnabled,
                downloadTreeUri = HookSettings.storageDownloadTreeUri,
                removeOuterPathEnabled = HookSettings.isStorageRemoveOuterPathEnabled,
                rootGuardEnabled = HookSettings.isStorageRootGuardEnabled,
                wechatBackupRedirectEnabled = HookSettings.isStorageWechatBackupRedirectEnabled,
                readerSdkRedirectEnabled = HookSettings.isStorageReaderSdkRedirectEnabled,
            ),
        )
    }

    private fun resolver(context: Context): StorageDestinationResolver =
        StorageDestinationResolver(context, snapshot())

    private fun hookUriCreator(mod: XposedModule, cl: ClassLoader, className: String, methodName: String, resolved: List<Method>? = null): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        (resolved ?: clazz.declaredMethods.filter { method ->
            method.name == methodName && method.parameterTypes.size == 3 &&
                Modifier.isStatic(method.modifiers) && !method.isSynthetic &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == String::class.java &&
                method.parameterTypes[2] == String::class.java &&
                method.returnType == String::class.java
        }).forEach { method ->
            if (method in installedPathMethods) { count++; return@forEach }
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookUriCreator:method").setExceptionMode(ExceptionMode.PASSTHROUGH).interceptPath { chain ->
                val context = chain.args.getOrNull(0) as? Context ?: return@interceptPath chain.proceed()
                if (!isDownloadRedirectActive(context)) return@interceptPath chain.proceed()
                val fileName = chain.args.getOrNull(1) as? String ?: return@interceptPath chain.proceed()
                val parent = chain.args.getOrNull(2) as? String
                val itemContext = downloadItemContext.get()
                val effectiveParent = if (itemContext != null) {
                    StoragePathRules.selectDownloadParent(
                        parent,
                        itemContext[0] as? String,
                        shouldRemoveOuterPath(context),
                    )
                } else {
                    parent
                }
                val isDirectory = itemContext?.get(1) == true
                XposedCompat.logD(
                    "[$TAG] create name=$fileName dir=$isDirectory parent=$parent effectiveParent=$effectiveParent",
                )
                val result = resolver(context).resolve(effectiveParent, fileName, isDirectory)
                when (result) {
                    is StorageDestination.ReadyDocumentUri -> result.uri
                    StorageDestination.Disabled -> chain.proceed()
                    is StorageDestination.Invalid,
                    is StorageDestination.Unsupported,
                    is StorageDestination.ReadyLegacyAbsolutePath,
                    is StorageDestination.ReadyMediaStoreRelativePath -> {
                        failWithoutFallback(context, "download", result.toString())
                        throw IllegalStateException("storage redirect failed: $result")
                    }
                }
            }
            installedPathMethods.add(method)
            installedTargetCount++
            count++
            XposedCompat.logD("[$TAG] URI creator hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun hookLegacyDefaultSaveDir(
        mod: XposedModule,
        cl: ClassLoader,
        className: String,
        methodNames: Set<String>,
        installed: MutableSet<String>,
        resolved: List<Method>? = null,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        (resolved ?: clazz.declaredMethods.filter { method ->
            method.name in methodNames &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.returnType == String::class.java && Modifier.isStatic(method.modifiers)
        }).forEach { method ->
            if (method in installedPathMethods) {
                installed.add("$className#${method.name}")
                count++
                return@forEach
            }
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookLegacyDefaultSaveDir:method").setExceptionMode(ExceptionMode.PASSTHROUGH).interceptPath { chain ->
                if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled) {
                    return@interceptPath chain.proceed()
                }
                val context = chain.args.firstOrNull() as? Context ?: return@interceptPath chain.proceed()
                if (!isDownloadRedirectActive(context)) return@interceptPath chain.proceed()
                when (val result = resolver(context).resolveLegacyAbsolutePath("")) {
                    is StorageDestination.ReadyLegacyAbsolutePath -> result.absolutePath
                    StorageDestination.Disabled -> chain.proceed()
                    else -> {
                        failWithoutFallback(context, "legacy", result.toString())
                        throw IllegalStateException("SAF directory cannot be used by legacy/native task: $result")
                    }
                }
            }
            installedPathMethods.add(method)
            installedTargetCount++
            count++
            installed.add("$className#${method.name}")
            XposedCompat.logD("[$TAG] default directory hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun hookDownloadExtension(
        mod: XposedModule,
        cl: ClassLoader,
        className: String,
        methodNames: Set<String>,
        partitionStorage: Method?,
        installed: MutableSet<String>,
        resolved: List<Method>? = null,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        (resolved ?: clazz.declaredMethods.filter { method ->
            method.name in methodNames && method.returnType == String::class.java &&
                Modifier.isStatic(method.modifiers) && !method.isSynthetic &&
                method.parameterTypes.any { Context::class.java.isAssignableFrom(it) }
        }).forEach { method ->
            if (method in installedPathMethods) {
                installed.add("$className#${method.name}")
                count++
                return@forEach
            }
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookDownloadExtension:method").setExceptionMode(ExceptionMode.PASSTHROUGH).interceptPath { chain ->
                if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled) {
                    return@interceptPath chain.proceed()
                }
                val context = chain.args.firstOrNull { it is Context } as? Context
                if (!isDownloadRedirectActive(context)) return@interceptPath chain.proceed()
                val fileName = findDownloadName(chain.args)
                val parent = findDownloadParent(chain.args)
                val removeOuterPath = shouldRemoveOuterPath(context)
                val sourceDirPath = if (removeOuterPath) {
                    runCatching { findDownloadSourceDir(chain.args) }.getOrElse { error ->
                        XposedCompat.logW("[$TAG] task parent unavailable: ${error.message}")
                        notifyOuterPathUnavailable(context)
                        // 保留完整云端父路径，仍下载到用户所选目录内。
                        parent
                    }
                } else null
                val isDirectory = findDownloadIsDirectory(chain.args)
                val effectiveParent = StoragePathRules.selectDownloadParent(
                    parent,
                    sourceDirPath,
                    removeOuterPath,
                )
                if (partitionStorage != null) {
                    val legacyPath = runCatching {
                        checkNotNull(context) { "下载上下文不可用" }
                        if (partitionStorage.invoke(null, context) == false) {
                            // 国际版传统下载链路用文件路径创建临时文件并完成重命名。
                            resolveIntlLegacyDownload(context, effectiveParent, fileName, isDirectory)
                        } else null
                    }.getOrElse { error ->
                        failWithoutFallback(context, "international download", error.message.orEmpty())
                        throw IllegalStateException("storage redirect failed", error)
                    }
                    if (legacyPath != null) return@interceptPath legacyPath
                }
                // 部分下载分支绕过 URI 创建入口，需在原方法落盘前预检目标目录。
                if (context != null && fileName != null && chain.args.any { it is Boolean && it }) {
                    when (val destination = resolver(context).resolve(effectiveParent, fileName, isDirectory)) {
                        is StorageDestination.ReadyDocumentUri -> return@interceptPath destination.uri
                        StorageDestination.Disabled -> Unit
                        else -> {
                            failWithoutFallback(context, "${clazz.simpleName}.${method.name}", destination.toString())
                            throw IllegalStateException("storage redirect failed: $destination")
                        }
                    }
                }
                val previousContext = downloadItemContext.get()
                downloadItemContext.set(arrayOf(sourceDirPath, isDirectory))
                val originalResult = try {
                    chain.proceed()
                } finally {
                    if (previousContext == null) downloadItemContext.remove() else downloadItemContext.set(previousContext)
                }
                val original = originalResult as? String ?: return@interceptPath originalResult
                // 新文件已由创建入口重定向；续传任务保留原 URI。
                if (original.startsWith("content://") || original.isBlank()) return@interceptPath original
                val actualContext = context ?: return@interceptPath original
                val actualFileName = fileName ?: original.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: return@interceptPath original
                when (val destination = resolver(actualContext).resolve(effectiveParent, actualFileName, isDirectory)) {
                    is StorageDestination.ReadyDocumentUri -> destination.uri
                    StorageDestination.Disabled -> original
                    else -> {
                        failWithoutFallback(actualContext, "${clazz.simpleName}.${method.name}", destination.toString())
                        throw IllegalStateException("storage redirect failed: $destination")
                    }
                }
            }
            installedPathMethods.add(method)
            installedTargetCount++
            count++
            installed.add("$className#${method.name}")
            XposedCompat.logD("[$TAG] download path hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun resolveIntlLegacyDownload(
        context: Context,
        parent: String,
        fileName: String?,
        isDirectory: Boolean,
    ): String {
        val relative = StoragePathRules.join(parent, fileName)
        val destinationResolver = resolver(context)
        val destination = destinationResolver.resolveLegacyAbsolutePath(relative)
        check(destination is StorageDestination.ReadyLegacyAbsolutePath) { destination.toString() }
        val directory = destinationResolver.resolveDirectory(if (isDirectory) relative else parent)
        check(directory is StorageDestination.ReadyDocumentUri) { directory.toString() }
        // 文件由宿主创建，保留临时文件、续传和完成收尾的一致路径。
        return destination.absolutePath
    }

    /** 为宿主下载列表及目录跳转提供 SAF 任务路径。 */
    private fun hookStoragePathQueries(
        mod: XposedModule,
        cl: ClassLoader,
        className: String,
        absoluteLocalMethodNames: Set<String>,
        relativeFileMethodNames: Set<String>,
        relativeDirectoryMethodNames: Set<String>,
        resolved: List<Method>? = null,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        (resolved ?: clazz.declaredMethods.filter { method ->
            method.name in (absoluteLocalMethodNames + relativeFileMethodNames + relativeDirectoryMethodNames) &&
                method.parameterTypes.size == 2 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == String::class.java &&
                method.returnType == String::class.java
        }).forEach { method ->
            if (method in installedPathMethods) { count++; return@forEach }
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookStoragePathQueries:method").interceptPath { chain ->
                val context = chain.args.getOrNull(0) as? Context
                val uri = chain.args.getOrNull(1) as? String
                if (!isDownloadRedirectActive(context)) return@interceptPath chain.proceed()
                val relative = if (context != null && uri != null) {
                    resolver(context).relativePathForDocumentUri(uri)
                } else {
                    null
                }
                if (relative == null) {
                    chain.proceed()
                } else if (method.name in absoluteLocalMethodNames) {
                    if (relative.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relative"
                } else if (method.name in relativeFileMethodNames) {
                    relative
                } else {
                    val slash = relative.lastIndexOf('/')
                    if (slash < 0) "" else relative.substring(0, slash + 1)
                }
            }
            installedPathMethods.add(method)
            installedTargetCount++
            count++
            XposedCompat.logD("[$TAG] path query hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun findDownloadName(args: List<Any?>): String? {
        args.filterNotNull().forEach { value ->
            val method = value.javaClass.methods.firstOrNull {
                (it.name == "getFileName" || it.name == "getFilename") && it.parameterTypes.isEmpty()
            } ?: return@forEach
            runCatching { method.invoke(value) as? String }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun findDownloadParent(args: List<Any?>): String {
        args.filterNotNull().forEach { value ->
            val parentObject = runCatching {
                value.javaClass.methods.firstOrNull {
                    it.name == "getParent" && it.parameterTypes.isEmpty()
                }?.invoke(value)
            }.getOrNull()
            val parentPath = parentObject?.let { parent ->
                runCatching {
                    parent.javaClass.methods.firstOrNull {
                        it.name == "getFilePath" && it.parameterTypes.isEmpty()
                    }?.invoke(parent) as? String
                }.getOrNull()
            }
            if (!parentPath.isNullOrBlank()) return parentPath
        }
        return ""
    }

    private fun findDownloadSourceDir(args: List<Any?>): String? {
        args.filterNotNull().forEach { value ->
            val getter = value.javaClass.methods.firstOrNull {
                it.name == "getSourceDirPath" && it.parameterTypes.isEmpty() && it.returnType == String::class.java
            } ?: return@forEach
            // 单文件和选中的根文件夹允许 null；反射失败时保留完整父路径。
            return getter.invoke(value) as String?
        }
        throw IllegalStateException("下载任务相对父路径接口不可用")
    }

    private fun findDownloadIsDirectory(args: List<Any?>): Boolean {
        args.filterNotNull().forEach { value ->
            val method = value.javaClass.methods.firstOrNull {
                (it.name == "isDir" || it.name == "isDirectory") && it.parameterTypes.isEmpty()
            } ?: return@forEach
            runCatching { method.invoke(value) }.getOrNull()?.let { result ->
                val directory = when (result) {
                    is Boolean -> result
                    is Number -> result.toInt() != 0
                    else -> false
                }
                if (directory) return true
            }
        }
        return false
    }

    private fun hookFolderTaskBinding(
        mod: XposedModule, cl: ClassLoader,
        className: String, folderClassName: String?,
        resolved: List<Method>? = null, parentMethod: Method? = null, nameMethod: Method? = null,
    ): Int {
        val methodName = BaiduStorageHookPoints.ADD_DOWNLOAD_FOLDER_TASK
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        (resolved ?: clazz.declaredMethods.filter { method ->
            method.name == methodName && Modifier.isStatic(method.modifiers) && !method.isSynthetic &&
                method.parameterTypes.size == 6 &&
                ContentResolver::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                Uri::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                (folderClassName == null || method.parameterTypes[2].name == folderClassName) &&
                method.parameterTypes[3] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[4] == Int::class.javaPrimitiveType &&
                method.parameterTypes[5] == Boolean::class.javaPrimitiveType &&
                method.returnType == Uri::class.java
        }).forEach { method ->
            if (method in installedPathMethods) { count++; return@forEach }
            val folderType = method.parameterTypes[2]
            val parentName = "getTransferParentPath"
            val nameName = "getName"
            val getters = folderType.methods.filter { it.parameterTypes.isEmpty() && it.returnType == String::class.java }
            val parentGetter = parentMethod ?: getters.singleOrNull { it.name == parentName }
            val nameGetter = nameMethod ?: getters.singleOrNull { it.name == nameName }
            if (parentGetter == null || nameGetter == null) {
                XposedCompat.logW("[$TAG] folder task getters unavailable: ${folderType.name}")
                return@forEach
            }
            parentGetter.isAccessible = true
            nameGetter.isAccessible = true
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookFolderTaskBinding:method").setExceptionMode(ExceptionMode.PASSTHROUGH).interceptPath { chain ->
                if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled ||
                    !shouldRemoveOuterPath(HookSettings.appContext())
                ) {
                    return@interceptPath chain.proceed()
                }
                val context = HookSettings.appContext()
                if (!isDownloadRedirectActive(context)) return@interceptPath chain.proceed()
                val (contentResolver, name, preflight) = runCatching {
                    checkNotNull(context) { "模块上下文不可用" }
                    val contentResolver = checkNotNull(chain.args.getOrNull(0) as? ContentResolver) {
                        "目录任务 ContentResolver 不可用"
                    }
                    val folderInfo = checkNotNull(chain.args.getOrNull(2)) { "目录任务信息不可用" }
                    // 空父路径表示根目录；getter 异常按路径读取失败处理。
                    val transferParent = (parentGetter.invoke(folderInfo) as String?).orEmpty()
                    val name = StoragePathRules.validateName(nameGetter.invoke(folderInfo) as String?)
                    val relative = StoragePathRules.join(transferParent, name)
                    val destination = resolver(context).resolveLegacyAbsolutePath(relative)
                    check(destination is StorageDestination.ReadyLegacyAbsolutePath) { destination.toString() }
                    Triple(contentResolver, name, destination)
                }.getOrElse { error ->
                    val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
                    val reason = "目录任务路径预检失败：${cause.javaClass.simpleName}: ${cause.message}"
                    failWithoutFallback(context, "folder", reason)
                    throw IllegalStateException("storage redirect failed: $reason", cause)
                }
                val originalResult = chain.proceed()
                val result = originalResult as? Uri ?: return@interceptPath originalResult
                val update = ContentValues(1).apply {
                    put("local_url", preflight.absolutePath)
                }
                runCatching {
                    check(contentResolver.update(result, update, null, null) > 0) {
                        "宿主未接受目录任务路径更新"
                    }
                }
                    .onFailure { error ->
                        failWithoutFallback(context, "folder", "无法更新目录任务路径：${error.message}")
                        throw IllegalStateException("storage redirect failed: ${error.message}", error)
                    }
                XposedCompat.logD(
                    "[$TAG] folder task redirected name=$name local=${preflight.absolutePath}",
                )
                result
            }
            installedPathMethods.add(method)
            installedTargetCount++
            count++
            XposedCompat.logD("[$TAG] folder task hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun hookSdkPublicDirectory(mod: XposedModule, cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(BaiduStorageHookPoints.SDK_FILE_UTILS, cl) ?: return 0
        val method = clazz.declaredMethods.singleOrNull {
            it.name == BaiduStorageHookPoints.SDK_PUBLIC_DIRECTORY && Modifier.isStatic(it.modifiers) &&
                it.returnType == File::class.java &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
        } ?: return 0
        method.isAccessible = true
        mod.hook(method).setId("StorageRedirectHook:hookSdkPublicDirectory:method").interceptPath { chain ->
            if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageReaderSdkRedirectEnabled) {
                return@interceptPath chain.proceed()
            }
            val context = HookSettings.appContext() ?: return@interceptPath chain.proceed()
            val namespace = StoragePathRules.normalizeRelativePath(chain.args[1] as? String)
            val child = StoragePathRules.normalizeRelativePath(chain.args[0] as? String)
            val root = File(context.filesDir, "sdk/baidu/$namespace")
            // 此处创建 SDK 根目录，子目录由调用方创建。
            if (!root.isDirectory && !root.mkdirs()) null else File(root, child)
        }
        installedTargetCount++
        XposedCompat.logD("[$TAG] SDK public directory hooked: ${clazz.name}.${method.name}")
        return 1
    }

    private fun hookRootGuard(mod: XposedModule): Int {
        if (!rootGuardInstalled.compareAndSet(false, true)) return 0
        var count = 0
        listOf("mkdir", "mkdirs").forEach { name ->
            val method = File::class.java.getDeclaredMethod(name)
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookRootGuard:method").intercept { chain ->
                val file = chain.thisObject as? File
                if (StorageRootGuard.shouldBlock(file?.path, snapshot())) false else chain.proceed()
            }
            installedTargetCount++
            count++
        }
        val filesClass = Files::class.java
        filesClass.declaredMethods.filter { method ->
            (method.name == "createDirectory" || method.name == "createDirectories") &&
                method.parameterTypes.isNotEmpty() && Path::class.java.isAssignableFrom(method.parameterTypes[0])
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).setId("StorageRedirectHook:hookRootGuard:method:2").setExceptionMode(ExceptionMode.PASSTHROUGH).intercept { chain ->
                val path = chain.args.firstOrNull() as? Path
                if (StorageRootGuard.shouldBlock(path?.toString(), snapshot())) {
                    throw StorageRootGuard.accessDenied(path?.toString(), snapshot())
                }
                chain.proceed()
            }
            installedTargetCount++
            count++
        }
        return count
    }

    private fun failWithoutFallback(context: Context?, type: String, reason: String) {
        XposedCompat.logW(
            "storage_redirect_failed host=${XposedCompat.currentPackageName()} uri=${HookSettings.storageDownloadTreeUri} " +
                "taskType=$type reason=$reason",
        )
        showFailure(context, "SAF授权失效，请重新选择下载目录")
    }

    private fun installDownloadBinding(name: String, install: () -> Int): Int = try {
        install()
    } catch (error: Throwable) {
        XposedCompat.logW("[$TAG] critical binding failed: $name: ${error.message}")
        0
    }

    private fun isDownloadRedirectActive(context: Context?): Boolean {
        if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled) return false
        if (downloadBindingsReady) {
            if (uriCreatorReady) return true
            // 国际传统文件路径由扩展入口直接返回，不依赖 URI 创建器。
            val mode = internationalStorageMode
            if (mode != null && context != null) {
                try {
                    if (mode.invoke(null, context) == false) return true
                } catch (error: ReflectiveOperationException) {
                    XposedCompat.logW("[$TAG] storage mode read failed: ${error.message}")
                }
            }
        }
        notifyDownloadUnavailable(context)
        return false
    }

    private fun shouldRemoveOuterPath(context: Context?): Boolean {
        if (!HookSettings.isStorageRemoveOuterPathEnabled) return false
        if (folderBindingsReady) return true
        notifyOuterPathUnavailable(context)
        return false
    }

    private fun notifyOuterPathUnavailable(context: Context?) {
        if (context != null && outerPathUnavailableNotified.compareAndSet(false, true)) {
            XposedCompat.logW("[$TAG] outer path removal unavailable: preserving cloud parent paths")
            showFailure(context, "移除外层路径不可用，将保留原层级")
        }
    }

    private fun notifyDownloadUnavailable(context: Context?) {
        if (context == null || !downloadUnavailableNotified.compareAndSet(false, true)) return
        XposedCompat.logW("[$TAG] download redirection unavailable: using host download directory")
        showFailure(context, "下载重定向不可用，将使用宿主目录")
    }

    private fun showFailure(context: Context?, message: String) {
        runCatching {
            if (context == null) return@runCatching
            ModuleLifetime.postResult(android.os.Handler(android.os.Looper.getMainLooper())) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

}
