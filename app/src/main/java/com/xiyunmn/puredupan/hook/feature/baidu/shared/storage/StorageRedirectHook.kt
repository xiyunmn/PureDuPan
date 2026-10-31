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
import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Shared storage hook. Domestic and international variants differ only in symbol resolution. */
object StorageRedirectHook {
    // 拒绝下载/路径创建的入口必须使用 PASSTHROUGH，防止框架保护模式吞掉拒绝异常后执行原方法。
    private const val TAG = "StorageRedirectHook"
    private val installState = HookState()
    private val rootGuardInstalled = AtomicBoolean(false)
    private val downloadItemContext = ThreadLocal<DownloadItemContext?>()

    fun hook(cl: ClassLoader) {
        if (!HookSettings.isStorageRedirectEnabled && !HookSettings.isStorageRootGuardEnabled) {
            XposedCompat.logD("[$TAG] skipped: all storage options disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!installState.markInstalled()) return
        val international = XposedCompat.currentPackageName() == HostPackages.BAIDU_INTL
        var count = 0
        try {
            if (HookSettings.isStorageRedirectEnabled && HookSettings.isStorageDownloadRedirectEnabled) {
                val creators = hookUriCreator(
                    mod,
                    cl,
                    if (international) BaiduStorageHookPoints.INTL_TARGET30_STORAGE else BaiduStorageHookPoints.DOMESTIC_TARGET30_STORAGE,
                    if (international) BaiduStorageHookPoints.INTL_URI_CREATOR else BaiduStorageHookPoints.URI_CREATOR,
                )
                val extensionOwners = if (international) BaiduStorageHookPoints.INTL_DOWNLOAD_EXTENSIONS
                    else listOf(BaiduStorageHookPoints.DOMESTIC_DOWNLOAD_EXTENSION)
                val extensions = extensionOwners.sumOf { owner -> hookDownloadExtension(
                    mod,
                    cl,
                    owner,
                    if (international) setOf(BaiduStorageHookPoints.INTL_DOWNLOAD_PATH, BaiduStorageHookPoints.INTL_LINK_DOWNLOAD_PATH)
                    else setOf("getDownloadPath", "getLinkDownloadPath"),
                ) }
                val folders = hookFolderTaskState(mod, cl, international)
                val legacy = hookLegacyDefaultSaveDir(
                    mod,
                    cl,
                    if (international) BaiduStorageHookPoints.DEFAULT_SETTING_INTL else BaiduStorageHookPoints.DEFAULT_SETTING,
                    if (international) setOf(BaiduStorageHookPoints.INTL_DEFAULT_SAVE_DIR, BaiduStorageHookPoints.INTL_LEGACY_SAVE_DIR)
                    else setOf(BaiduStorageHookPoints.DEFAULT_SAVE_DIR),
                )
                count += creators + extensions + folders + legacy
                XposedCompat.logD("[$TAG] download bindings: creators=$creators extensions=$extensions folders=$folders legacy=$legacy")
                if (creators == 0 || extensions == 0 || legacy == 0 ||
                    (HookSettings.isStorageRemoveOuterPathEnabled && folders == 0)
                ) {
                    XposedCompat.logW("[$TAG] download bindings incomplete; path query/root guard hooks do not redirect downloads")
                }
                count += hookStoragePathQueries(
                    mod,
                    cl,
                    if (international) BaiduStorageHookPoints.INTL_TARGET30_STORAGE else BaiduStorageHookPoints.DOMESTIC_TARGET30_STORAGE,
                    if (international) setOf(BaiduStorageHookPoints.INTL_GET_PARTITION_LOCAL_PATH) else emptySet(),
                    setOf(if (international) BaiduStorageHookPoints.INTL_QUERY_ABSOLUTE_PATH else BaiduStorageHookPoints.QUERY_ABSOLUTE_PATH),
                    setOf(if (international) BaiduStorageHookPoints.INTL_QUERY_PATH else BaiduStorageHookPoints.QUERY_PATH),
                )
            }
            if (HookSettings.isStorageRedirectEnabled && HookSettings.isStorageReaderSdkRedirectEnabled) {
                val sdk = hookSdkPublicDirectory(mod, cl)
                count += sdk
                if (sdk == 0) XposedCompat.logD("[$TAG] optional SDK public directory entry unavailable")
            }
            if (HookSettings.isStorageRootGuardEnabled) count += hookRootGuard(mod)
            if (count == 0) {
                XposedCompat.logW("[$TAG] no targets installed")
                installState.reset()
            } else {
                XposedCompat.log("[$TAG] hook INSTALLED targets=$count")
            }
        } catch (t: Throwable) {
            installState.reset()
            XposedCompat.log("[$TAG] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

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

    private fun hookUriCreator(mod: XposedModule, cl: ClassLoader, className: String, methodName: String): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name == methodName && method.parameterTypes.size == 3 &&
                Modifier.isStatic(method.modifiers) && !method.isSynthetic &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == String::class.java &&
                method.parameterTypes[2] == String::class.java &&
                method.returnType == String::class.java
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept { chain ->
                val context = chain.args.getOrNull(0) as? Context ?: return@intercept chain.proceed()
                val fileName = chain.args.getOrNull(1) as? String ?: return@intercept chain.proceed()
                val parent = chain.args.getOrNull(2) as? String
                val itemContext = downloadItemContext.get()
                val effectiveParent = if (itemContext != null) {
                    StoragePathRules.selectDownloadParent(
                        parent,
                        itemContext.sourceDirPath,
                        HookSettings.isStorageRemoveOuterPathEnabled,
                    )
                } else {
                    parent
                }
                val isDirectory = itemContext?.isDirectory == true
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
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name in methodNames &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.returnType == String::class.java && Modifier.isStatic(method.modifiers)
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept { chain ->
                if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled) {
                    return@intercept chain.proceed()
                }
                val context = chain.args.firstOrNull() as? Context ?: return@intercept chain.proceed()
                when (val result = resolver(context).resolveLegacyAbsolutePath("")) {
                    is StorageDestination.ReadyLegacyAbsolutePath -> result.absolutePath
                    StorageDestination.Disabled -> chain.proceed()
                    else -> {
                        failWithoutFallback(context, "legacy", result.toString())
                        throw IllegalStateException("SAF directory cannot be used by legacy/native task: $result")
                    }
                }
            }
            count++
            XposedCompat.logD("[$TAG] default directory hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun hookDownloadExtension(
        mod: XposedModule,
        cl: ClassLoader,
        className: String,
        methodNames: Set<String>,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name in methodNames && method.returnType == String::class.java &&
                Modifier.isStatic(method.modifiers) && !method.isSynthetic &&
                if (className in BaiduStorageHookPoints.INTL_DOWNLOAD_EXTENSIONS) {
                    val parameters = listOf(
                        Context::class.java.name, BaiduStorageHookPoints.DOWNLOADABLE,
                        String::class.java.name, "boolean", "int",
                    )
                    val expected = if (method.name == BaiduStorageHookPoints.INTL_LINK_DOWNLOAD_PATH) {
                        listOf(BaiduStorageHookPoints.FILE_INFO_GENERATOR) + parameters
                    } else {
                        parameters
                    }
                    method.parameterTypes.map { it.name } == expected
                } else {
                    method.parameterTypes.any { Context::class.java.isAssignableFrom(it) }
                }
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept { chain ->
                val context = chain.args.firstOrNull { it is Context } as? Context
                val fileName = findDownloadName(chain.args)
                val parent = findDownloadParent(chain.args)
                val sourceDirPath = findDownloadSourceDir(chain.args)
                val isDirectory = findDownloadIsDirectory(chain.args)
                val effectiveParent = StoragePathRules.selectDownloadParent(
                    parent,
                    sourceDirPath,
                    HookSettings.isStorageRemoveOuterPathEnabled,
                )
                // Smooth-video/old branches bypass createDownloadUriStr. Preflight those branches
                // before the original method can materialize a public default path.
                if (context != null && fileName != null && chain.args.any { it is Boolean && it }) {
                    when (val destination = resolver(context).resolve(effectiveParent, fileName, isDirectory)) {
                        is StorageDestination.ReadyDocumentUri -> return@intercept destination.uri
                        StorageDestination.Disabled -> Unit
                        else -> {
                            failWithoutFallback(context, "${clazz.simpleName}.${method.name}", destination.toString())
                            throw IllegalStateException("storage redirect failed: $destination")
                        }
                    }
                }
                val previousContext = downloadItemContext.get()
                downloadItemContext.set(DownloadItemContext(sourceDirPath, isDirectory))
                val originalResult = try {
                    chain.proceed()
                } finally {
                    if (previousContext == null) downloadItemContext.remove() else downloadItemContext.set(previousContext)
                }
                val original = originalResult as? String ?: return@intercept originalResult
                // The creator above already redirects new documents. Keep an existing task's URI
                // when the host reuses it for resume; do not migrate saved downloads implicitly.
                if (original.startsWith("content://") || original.isBlank()) return@intercept original
                val actualContext = context ?: return@intercept original
                val actualFileName = fileName ?: original.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: return@intercept original
                when (val destination = resolver(actualContext).resolve(effectiveParent, actualFileName, isDirectory)) {
                    is StorageDestination.ReadyDocumentUri -> destination.uri
                    StorageDestination.Disabled -> original
                    else -> {
                        failWithoutFallback(actualContext, "${clazz.simpleName}.${method.name}", destination.toString())
                        throw IllegalStateException("storage redirect failed: $destination")
                    }
                }
            }
            count++
            XposedCompat.logD("[$TAG] download path hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    /** Makes SAF task locations readable to the host download list and its location jump action. */
    private fun hookStoragePathQueries(
        mod: XposedModule,
        cl: ClassLoader,
        className: String,
        absoluteLocalMethodNames: Set<String>,
        relativeFileMethodNames: Set<String>,
        relativeDirectoryMethodNames: Set<String>,
    ): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name in (absoluteLocalMethodNames + relativeFileMethodNames + relativeDirectoryMethodNames) &&
                method.parameterTypes.size == 2 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == String::class.java &&
                method.returnType == String::class.java
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val context = chain.args.getOrNull(0) as? Context
                val uri = chain.args.getOrNull(1) as? String
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
            val source = runCatching {
                value.javaClass.methods.firstOrNull {
                    it.name == "getSourceDirPath" && it.parameterTypes.isEmpty()
                }?.invoke(value) as? String
            }.getOrNull()
            if (source != null) return source
        }
        return null
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

    private fun hookFolderTaskState(mod: XposedModule, cl: ClassLoader, international: Boolean): Int {
        val bindings = if (international) BaiduStorageHookPoints.INTL_FOLDER_TASK_BINDINGS
            else listOf(BaiduStorageHookPoints.FOLDER_TASK_STATE_UTIL to null)
        return bindings.sumOf { (className, folderClassName) ->
            hookFolderTaskBinding(mod, cl, international, className, folderClassName)
        }
    }

    private fun hookFolderTaskBinding(
        mod: XposedModule, cl: ClassLoader, international: Boolean,
        className: String, folderClassName: String?,
    ): Int {
        val methodName = if (international) BaiduStorageHookPoints.INTL_ADD_DOWNLOAD_FOLDER_TASK
            else BaiduStorageHookPoints.ADD_DOWNLOAD_FOLDER_TASK
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name == methodName && Modifier.isStatic(method.modifiers) && !method.isSynthetic &&
                method.parameterTypes.size == 6 &&
                ContentResolver::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                Uri::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                (!international || method.parameterTypes[2].name == folderClassName) &&
                method.parameterTypes[3] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[4] == Int::class.javaPrimitiveType &&
                method.parameterTypes[5] == Boolean::class.javaPrimitiveType &&
                method.returnType == Uri::class.java
        }.forEach { method ->
            val folderType = method.parameterTypes[2]
            val parentName = if (international) BaiduStorageHookPoints.INTL_FOLDER_TRANSFER_PARENT else "getTransferParentPath"
            val nameName = if (international) BaiduStorageHookPoints.INTL_FOLDER_NAME else "getName"
            val getters = folderType.methods.filter { it.parameterTypes.isEmpty() && it.returnType == String::class.java }
            val parentGetter = getters.singleOrNull { it.name == parentName }
            val nameGetter = getters.singleOrNull { it.name == nameName }
            if (parentGetter == null || nameGetter == null) {
                XposedCompat.logW("[$TAG] folder task getters unavailable: ${folderType.name}")
                return@forEach
            }
            parentGetter.isAccessible = true
            nameGetter.isAccessible = true
            method.isAccessible = true
            mod.hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept { chain ->
                if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled ||
                    !HookSettings.isStorageRemoveOuterPathEnabled
                ) {
                    return@intercept chain.proceed()
                }
                val context = HookSettings.appContext()
                val (contentResolver, name, preflight) = runCatching {
                    checkNotNull(context) { "模块上下文不可用" }
                    val contentResolver = checkNotNull(chain.args.getOrNull(0) as? ContentResolver) {
                        "目录任务 ContentResolver 不可用"
                    }
                    val folderInfo = checkNotNull(chain.args.getOrNull(2)) { "目录任务信息不可用" }
                    // 根目录父路径可为空；反射失败不能冒充合法空父路径。
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
                val result = originalResult as? Uri ?: return@intercept originalResult
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
        mod.hook(method).intercept { chain ->
            if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageReaderSdkRedirectEnabled) {
                return@intercept chain.proceed()
            }
            val context = HookSettings.appContext() ?: return@intercept chain.proceed()
            val namespace = StoragePathRules.normalizeRelativePath(chain.args[1] as? String)
            val child = StoragePathRules.normalizeRelativePath(chain.args[0] as? String)
            val root = File(context.filesDir, "sdk/baidu/$namespace")
            // The SDK creates only its root here; callers own creation of the returned child.
            if (!root.isDirectory && !root.mkdirs()) null else File(root, child)
        }
        XposedCompat.logD("[$TAG] SDK public directory hooked: ${clazz.name}.${method.name}")
        return 1
    }

    private fun hookRootGuard(mod: XposedModule): Int {
        if (!rootGuardInstalled.compareAndSet(false, true)) return 0
        var count = 0
        listOf("mkdir", "mkdirs").forEach { name ->
            val method = File::class.java.getDeclaredMethod(name)
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val file = chain.thisObject as? File
                if (StorageRootGuard.shouldBlock(file?.path, snapshot())) false else chain.proceed()
            }
            count++
        }
        val filesClass = Files::class.java
        filesClass.declaredMethods.filter { method ->
            (method.name == "createDirectory" || method.name == "createDirectories") &&
                method.parameterTypes.isNotEmpty() && Path::class.java.isAssignableFrom(method.parameterTypes[0])
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept { chain ->
                val path = chain.args.firstOrNull() as? Path
                if (StorageRootGuard.shouldBlock(path?.toString(), snapshot())) {
                    throw StorageRootGuard.accessDenied(path?.toString(), snapshot())
                }
                chain.proceed()
            }
            count++
        }
        return count
    }

    private fun failWithoutFallback(context: Context?, type: String, reason: String) {
        XposedCompat.logW(
            "storage_redirect_failed host=${XposedCompat.currentPackageName()} uri=${HookSettings.storageDownloadTreeUri} " +
                "taskType=$type reason=$reason",
        )
        runCatching {
            if (context == null) return@runCatching
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "SAF授权失效，请重新选择下载目录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class DownloadItemContext(
        val sourceDirPath: String?,
        val isDirectory: Boolean,
    )
}
