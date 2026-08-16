package com.xiyunmn.puredupan.hook.feature.baidu.shared.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.storage.StorageDestination
import com.xiyunmn.puredupan.hook.storage.StorageDestinationResolver
import com.xiyunmn.puredupan.hook.storage.StoragePathRules
import com.xiyunmn.puredupan.hook.storage.StorageRedirectConfig
import com.xiyunmn.puredupan.hook.storage.StorageRedirectSnapshot
import com.xiyunmn.puredupan.hook.storage.StorageRootGuard
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStorageHookPoints
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.reflect.Method
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Shared storage hook. Domestic and international variants differ only in symbol resolution. */
object StorageRedirectHook {
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
        val context = HookSettings.appContext()
        var count = 0
        try {
            if (HookSettings.isStorageRedirectEnabled && HookSettings.isStorageDownloadRedirectEnabled) {
                count += hookUriCreator(mod, cl, BaiduStorageHookPoints.DOMESTIC_TARGET30_STORAGE, BaiduStorageHookPoints.URI_CREATOR)
                count += hookUriCreator(mod, cl, BaiduStorageHookPoints.INTL_TARGET30_STORAGE, BaiduStorageHookPoints.INTL_URI_CREATOR)
                count += hookDownloadExtension(mod, cl, BaiduStorageHookPoints.DOMESTIC_DOWNLOAD_EXTENSION, setOf("getDownloadPath", "getLinkDownloadPath"))
                count += hookDownloadExtension(mod, cl, BaiduStorageHookPoints.INTL_DOWNLOAD_EXTENSION, setOf("___", "_____"))
                count += hookFolderTaskState(mod, cl)
                count += hookLegacyDefaultSaveDir(mod, cl, BaiduStorageHookPoints.DEFAULT_SETTING)
                count += hookLegacyDefaultSaveDir(mod, cl, BaiduStorageHookPoints.DEFAULT_SETTING_INTL)
                count += hookStoragePathQueries(
                    mod,
                    cl,
                    BaiduStorageHookPoints.DOMESTIC_TARGET30_STORAGE,
                    emptySet(),
                    setOf(BaiduStorageHookPoints.QUERY_ABSOLUTE_PATH),
                    setOf(BaiduStorageHookPoints.QUERY_PATH),
                )
                count += hookStoragePathQueries(
                    mod,
                    cl,
                    BaiduStorageHookPoints.INTL_TARGET30_STORAGE,
                    setOf(BaiduStorageHookPoints.INTL_GET_PARTITION_LOCAL_PATH),
                    setOf(BaiduStorageHookPoints.INTL_QUERY_ABSOLUTE_PATH),
                    setOf(BaiduStorageHookPoints.INTL_QUERY_PATH),
                )
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
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == String::class.java &&
                method.parameterTypes[2] == String::class.java &&
                method.returnType == String::class.java
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
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

    private fun hookLegacyDefaultSaveDir(mod: XposedModule, cl: ClassLoader, className: String): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name in setOf(BaiduStorageHookPoints.DEFAULT_SAVE_DIR, "m12634_") &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.returnType == String::class.java && java.lang.reflect.Modifier.isStatic(method.modifiers)
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
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
                method.parameterTypes.any { Context::class.java.isAssignableFrom(it) }
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
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

    private fun hookFolderTaskState(mod: XposedModule, cl: ClassLoader): Int {
        val clazz = XposedCompat.findClassOrNull(BaiduStorageHookPoints.FOLDER_TASK_STATE_UTIL, cl) ?: return 0
        var count = 0
        clazz.declaredMethods.filter { method ->
            method.name == BaiduStorageHookPoints.ADD_DOWNLOAD_FOLDER_TASK &&
                method.parameterTypes.size == 6 &&
                ContentResolver::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                Uri::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                method.returnType == Uri::class.java
        }.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (!HookSettings.isStorageRedirectEnabled || !HookSettings.isStorageDownloadRedirectEnabled ||
                    !HookSettings.isStorageRemoveOuterPathEnabled
                ) {
                    return@intercept chain.proceed()
                }
                val contentResolver = chain.args.getOrNull(0) as? ContentResolver
                    ?: return@intercept chain.proceed()
                val folderInfo = chain.args.getOrNull(2) ?: return@intercept chain.proceed()
                val transferParent = invokeString(folderInfo, "getTransferParentPath").orEmpty()
                val name = invokeString(folderInfo, "getName")
                    ?: return@intercept chain.proceed()
                val context = HookSettings.appContext() ?: return@intercept chain.proceed()
                val relative = StoragePathRules.join(transferParent, name)
                val preflight = resolver(context).resolveLegacyAbsolutePath(relative)
                if (preflight !is StorageDestination.ReadyLegacyAbsolutePath) {
                    failWithoutFallback(context, "folder", preflight.toString())
                    throw IllegalStateException("storage redirect failed: $preflight")
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
                    "[$TAG] folder task redirected name=$name transferParent=$transferParent local=${preflight.absolutePath}",
                )
                result
            }
            count++
            XposedCompat.logD("[$TAG] folder task hooked: ${clazz.name}.${method.name}")
        }
        return count
    }

    private fun invokeString(target: Any, methodName: String): String? {
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty()
            }?.invoke(target) as? String
        }.getOrNull()
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
            mod.hook(method).intercept { chain ->
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

    private fun failWithoutFallback(context: Context, type: String, reason: String) {
        XposedCompat.logW(
            "storage_redirect_failed host=${XposedCompat.currentPackageName()} uri=${HookSettings.storageDownloadTreeUri} " +
                "taskType=$type reason=$reason",
        )
        runCatching {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "下载目录不可用，任务已阻止：$reason", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class DownloadItemContext(
        val sourceDirPath: String?,
        val isDirectory: Boolean,
    )
}
