package com.xiyunmn.puredupan.hook.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.config.ConfigManager
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/** 在授权目录内解析下载目标，失败时返回明确原因。 */
class StorageDestinationResolver(
    private val context: Context,
    val snapshot: StorageRedirectSnapshot,
) {
    private val resolver = context.contentResolver

    /** 从文档 URI 提取主存储相对路径，提供方不支持时返回 null。 */
    fun relativePathForDocumentUri(uriString: String?): String? {
        val uri = uriString?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        return documentIdToPrimaryRelativePath(documentId)
    }

    fun displayPathForDocumentUri(uriString: String?): String? {
        return relativePathForDocumentUri(uriString)?.let { relative ->
            if (relative.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relative"
        }
    }

    private fun documentIdToPrimaryRelativePath(documentId: String): String? {
        val lower = documentId.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("primary:") -> documentId.substringAfter(':').trim('/')
            lower.startsWith("raw:/storage/emulated/0") ->
                documentId.substringAfter("/storage/emulated/0").trim('/')
            else -> null
        }
    }

    fun resolve(
        relativePath: String?,
        fileName: String? = null,
        isDirectory: Boolean = false,
    ): StorageDestination {
        if (!snapshot.enabled || !snapshot.downloadRedirectEnabled) return StorageDestination.Disabled
        val tree = snapshot.downloadTreeUri?.takeIf { it.isNotBlank() }
            ?: return StorageDestination.Invalid("未选择公共下载目录")
        val treeUri = runCatching { Uri.parse(tree) }.getOrElse {
            return StorageDestination.Invalid("下载目录 URI 无效")
        }
        if (!hasPersistedPermission(treeUri)) {
            return StorageDestination.Invalid("下载目录授权已失效，请重新选择")
        }
        val relative = try {
            StoragePathRules.stripDefaultPublicPrefix(relativePath)
        } catch (e: IllegalArgumentException) {
            return StorageDestination.Invalid(e.message ?: "路径不合法")
        }
        return try {
            val name = fileName?.takeIf { it.isNotBlank() }?.let(StoragePathRules::validateName)
            StorageDestination.ReadyDocumentUri(resolveDocument(treeUri, relative, name, isDirectory).toString())
        } catch (e: SecurityException) {
            StorageDestination.Invalid("下载目录不可写或权限已撤销")
        } catch (e: IllegalArgumentException) {
            StorageDestination.Invalid(e.message ?: "路径不合法")
        } catch (e: Throwable) {
            StorageDestination.Invalid("无法创建下载目录：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun resolveDirectory(relativePath: String?): StorageDestination = resolve(relativePath, null)

    /** 为 BT/P2P 提供主存储真实路径，不支持转换的目录返回失败。 */
    fun resolveLegacyAbsolutePath(relativePath: String?): StorageDestination {
        if (!snapshot.enabled || !snapshot.downloadRedirectEnabled) return StorageDestination.Disabled
        val tree = snapshot.downloadTreeUri?.takeIf { it.isNotBlank() }
            ?: return StorageDestination.Unsupported("SAF 下载目录未配置")
        val uri = runCatching { Uri.parse(tree) }.getOrElse {
            return StorageDestination.Invalid("下载目录 URI 无效")
        }
        if (!hasPersistedPermission(uri)) return StorageDestination.Invalid("下载目录授权已失效")
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrElse {
            return StorageDestination.Unsupported("provider 不支持真实路径转换")
        }
        val root = primaryTreePath(id) ?: return StorageDestination.Unsupported(
            "当前 SAF provider 无法转换为主存储真实路径",
        )
        val relative = try {
            StoragePathRules.stripDefaultPublicPrefix(relativePath)
        } catch (e: IllegalArgumentException) {
            return StorageDestination.Invalid(e.message ?: "路径不合法")
        }
        val path = if (relative.isEmpty()) root else "$root/${relative.replace('/', File.separatorChar)}"
        return StorageDestination.ReadyLegacyAbsolutePath(path)
    }

    private fun hasPersistedPermission(uri: Uri): Boolean {
        val permissions = resolver.persistedUriPermissions
        return permissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
            || (context.checkUriPermission(
                uri,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    private fun resolveDocument(treeUri: Uri, relative: String, name: String?, isDirectory: Boolean): Uri {
        fun resolveIn(parent: Uri): Uri {
            if (name == null) return parent
            return findChild(parent, name, isDirectory) ?: DocumentsContract.createDocument(
                resolver,
                parent,
                if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream",
                name,
            ) ?: throw IllegalStateException("provider refused file creation")
        }

        // 系统外部存储的文档 ID 随路径变化，其它提供方沿用实时寻址。
        val cacheable = relative.isNotEmpty() && treeUri.authority == "com.android.externalstorage.documents"
        val key = treeUri to relative
        val cached = if (cacheable) directoryCache.get(key) else null
        if (cached != null) {
            if (SystemClock.elapsedRealtime() - cached.resolvedAt < DIRECTORY_CACHE_TTL_MS) {
                try {
                    // 传统下载随后使用文件路径，返回前确认缓存目录仍存在。
                    if (name == null && !directoryExists(cached.uri)) {
                        throw FileNotFoundException("cached directory no longer exists")
                    }
                    return resolveIn(cached.uri)
                } catch (error: Exception) {
                    directoryCache.remove(key)
                    // 目录被删除时从授权树重新寻址一次，其它错误交回原失败处理。
                    if (error !is FileNotFoundException) throw error
                }
            } else {
                directoryCache.remove(key)
            }
        }

        val parent = ensureDirectory(treeUri, relative)
        val result = resolveIn(parent)
        if (cacheable) {
            directoryCache.put(key, CachedDirectory(parent, SystemClock.elapsedRealtime()))
        }
        return result
    }

    private fun directoryExists(uri: Uri): Boolean {
        return resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
        } == true
    }

    private fun ensureDirectory(treeUri: Uri, relative: String): Uri {
        var parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        if (relative.isEmpty()) return parent
        relative.split('/').forEach { segment ->
            parent = findChild(parent, segment, true)
                ?: DocumentsContract.createDocument(
                    resolver,
                    parent,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                ) ?: throw IllegalStateException("provider refused directory creation")
        }
        return parent
    }

    private fun findChild(parent: Uri, name: String, directory: Boolean? = null): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        return resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                OpenableColumns.DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == name && idIndex >= 0) {
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (directory == null || directory == isDirectory) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idIndex))
                    }
                    throw IllegalStateException(
                        if (directory) {
                            "目标位置已存在同名文件，无法创建目录：$name"
                        } else {
                            "目标位置已存在同名目录，无法创建文件：$name"
                        },
                    )
                }
            }
            null
        }
    }

    private fun primaryTreePath(documentId: String): String? {
        val lower = documentId.lowercase(Locale.ROOT)
        if (lower.startsWith("primary:")) {
            val relative = documentId.substringAfter(':').trim('/')
            return if (relative.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$relative"
        }
        if (lower.startsWith("raw:")) {
            val raw = documentId.substringAfter(':')
            return if (raw == "/storage/emulated/0" || raw.startsWith("/storage/emulated/0/")) raw else null
        }
        return null
    }

    companion object {
        private const val DIRECTORY_CACHE_TTL_MS = 30_000L
        private data class CachedDirectory(val uri: Uri, val resolvedAt: Long)

        // Resolver 随下载调用创建，目录缓存由本代实例共享，不持有宿主 Context。
        private val directoryCache = LruCache<Pair<Uri, String>, CachedDirectory>(64)

        fun snapshotFor(
            hostPackageName: String,
            prefs: SharedPreferences,
        ): StorageRedirectSnapshot {
            return StorageRedirectSnapshot(
                hostPackageName = hostPackageName,
                config = StorageRedirectConfig(
                    enabled = prefs.getBoolean(FeatureKeys.KEY_STORAGE_REDIRECT_ENABLED, false),
                    downloadRedirectEnabled = prefs.getBoolean(
                        FeatureKeys.KEY_STORAGE_DOWNLOAD_REDIRECT_ENABLED,
                        false,
                    ),
                    downloadTreeUri = prefs.getString(FeatureKeys.KEY_STORAGE_DOWNLOAD_TREE_URI, null),
                    removeOuterPathEnabled = prefs.getBoolean(
                        FeatureKeys.KEY_STORAGE_REMOVE_OUTER_PATH,
                        false,
                    ),
                    rootGuardEnabled = prefs.getBoolean(FeatureKeys.KEY_STORAGE_ROOT_GUARD_ENABLED, false),
                    wechatBackupRedirectEnabled = prefs.getBoolean(
                        FeatureKeys.KEY_STORAGE_WECHAT_BACKUP_REDIRECT_ENABLED,
                        false,
                    ),
                    readerSdkRedirectEnabled = prefs.getBoolean(
                        FeatureKeys.KEY_STORAGE_READER_SDK_REDIRECT_ENABLED,
                        false,
                    ),
                ),
            )
        }

        fun fromContext(context: Context): StorageDestinationResolver {
            val prefs = context.getSharedPreferences(
                ConfigManager.userSettingsPrefsNameFor(context.packageName),
                Context.MODE_PRIVATE,
            )
            return StorageDestinationResolver(context, snapshotFor(context.packageName, prefs))
        }

        /** 生成主共享存储目录的可读路径。 */
        fun displayPathForTreeUri(context: Context, uriString: String?): String? {
            return StorageDestinationResolver(
                context,
                StorageRedirectSnapshot(context.packageName, StorageRedirectConfig()),
            ).displayPathForDocumentUri(uriString)
        }
    }
}
