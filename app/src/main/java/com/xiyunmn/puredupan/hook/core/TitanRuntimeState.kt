package com.xiyunmn.puredupan.hook.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object TitanRuntimeState {
    private const val TITAN_DIR = "titan"
    private const val HEAD_FILE = "head"
    private const val PATCHES_DIR = "patches"
    private const val PATCH_FILE = "patch.apk"
    private const val PATCH_INFO_PATH = "assets/patchinfo"
    private const val LOADER_MANAGER_CLASS = "com.baidu.titan.sdk.loader.LoaderManager"

    fun buildFingerprint(context: Context): String {
        return snapshot(context).fingerprint()
    }

    fun logStartup(context: Context, cl: ClassLoader) {
        try {
            val snapshot = snapshot(context)
            val loaderState = readLoaderState(cl)
            logAlways("[TitanRuntime] startup: ${loaderState.describeForLog()}, ${snapshot.describeForLog()}")
        } catch (t: Throwable) {
            logAlways("[TitanRuntime] startup: unavailable, error=${t.javaClass.simpleName}")
        }
    }

    private fun snapshot(context: Context): Snapshot {
        val appCtx = context.applicationContext ?: context
        val baseDir = File(appCtx.applicationInfo.dataDir, TITAN_DIR)
        val headFile = File(baseDir, HEAD_FILE)
        val headLength = if (headFile.exists()) headFile.length() else -1L
        val headLastModified = if (headFile.exists()) headFile.lastModified() else -1L

        if (!headFile.isFile) {
            return Snapshot(
                headExists = false,
                headLength = headLength,
                headLastModified = headLastModified,
            )
        }

        val headContent = try {
            headFile.readText(Charsets.UTF_8)
        } catch (t: Throwable) {
            return Snapshot(
                headExists = true,
                headLength = headLength,
                headLastModified = headLastModified,
                headError = t.javaClass.simpleName,
            )
        }

        val head = parseHead(headContent)
        if (head == null) {
            return Snapshot(
                headExists = true,
                headLength = headLength,
                headLastModified = headLastModified,
                headSha256 = sha256(headContent),
                headError = "parse",
            )
        }

        val patchFile = File(File(File(baseDir, PATCHES_DIR), head.patchHash), PATCH_FILE)
        val patchMetaResult = readPatchMeta(patchFile)
        return Snapshot(
            headExists = true,
            headLength = headLength,
            headLastModified = headLastModified,
            headSha256 = sha256(headContent),
            head = head,
            patchFileLength = if (patchFile.exists()) patchFile.length() else -1L,
            patchFileLastModified = if (patchFile.exists()) patchFile.lastModified() else -1L,
            patchStatusExists = patchFile.parentFile?.let { File(it, "status").isFile } ?: false,
            patchMeta = patchMetaResult.meta,
            patchMetaError = patchMetaResult.error,
        )
    }

    private fun parseHead(content: String): Head? {
        return try {
            val json = JSONObject(content)
            val patchHash = json.optString("patchHash").takeIf { it.isNotBlank() } ?: return null
            val targetId = json.optString("targetId").takeIf { it.isNotBlank() }
            Head(
                patchHash = patchHash,
                targetId = targetId,
                crashCount = json.optInt("crashCount", 0),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun readPatchMeta(patchFile: File): PatchMetaResult {
        if (!patchFile.isFile) return PatchMetaResult(error = "missing")
        return try {
            ZipFile(patchFile).use { zip ->
                val entry = zip.getEntry(PATCH_INFO_PATH) ?: return PatchMetaResult(error = "entryMissing")
                val content = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                PatchMetaResult(meta = parsePatchMeta(content))
            }
        } catch (t: Throwable) {
            PatchMetaResult(error = t.javaClass.simpleName)
        }
    }

    private fun parsePatchMeta(content: String): PatchMeta? {
        val json = JSONObject(content)
        val versionInfo = json.optJSONObject("versionInfo")
        return PatchMeta(
            targetId = json.optString("targetId").takeIf { it.isNotBlank() },
            status = json.optIntOrNull("status"),
            loadPolicy = json.optIntOrNull("loadPolicy"),
            bootLoadSyncPolicy = json.optIntOrNull("bootLoadSyncPolicy"),
            patchVersionCode = versionInfo?.optLongOrNull("patchVersionCode"),
            patchVersionName = versionInfo?.optString("patchVersionName")?.takeIf { it.isNotBlank() },
            hostVersionCode = versionInfo?.optLongOrNull("hostVersionCode"),
            hostVersionName = versionInfo?.optString("hostVersionName")?.takeIf { it.isNotBlank() },
        )
    }

    private fun readLoaderState(cl: ClassLoader): LoaderState {
        return try {
            val managerClass = Class.forName(LOADER_MANAGER_CLASS, false, cl)
            val getInstance = managerClass.getDeclaredMethod("getInstance").apply { isAccessible = true }
            val manager = getInstance.invoke(null)
            val loadState = managerClass.getDeclaredMethod("getLoadState")
                .apply { isAccessible = true }
                .invoke(manager) as? Int
            val patchInfo = managerClass.getDeclaredMethod("getCurrentPatchInfo")
                .apply { isAccessible = true }
                .invoke(manager)
            val currentPatchId = patchInfo?.javaClass
                ?.getDeclaredMethod("getId")
                ?.apply { isAccessible = true }
                ?.invoke(patchInfo) as? String
            LoaderState(loadState = loadState, currentPatchId = currentPatchId)
        } catch (t: Throwable) {
            LoaderState(error = t.javaClass.simpleName)
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val v = byte.toInt() and 0xff
            out.append(HEX[v ushr 4])
            out.append(HEX[v and 0x0f])
        }
        return out.toString()
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name)) optInt(name) else null
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (has(name)) optLong(name) else null
    }

    private fun logAlways(message: String) {
        try {
            XposedCompat.log(message)
        } catch (t: Throwable) {
            XposedCompat.logD("TitanRuntimeState: ${t.message}")
        }
    }

    private data class Snapshot(
        val headExists: Boolean,
        val headLength: Long,
        val headLastModified: Long,
        val headSha256: String? = null,
        val head: Head? = null,
        val headError: String? = null,
        val patchFileLength: Long = -1L,
        val patchFileLastModified: Long = -1L,
        val patchStatusExists: Boolean = false,
        val patchMeta: PatchMeta? = null,
        val patchMetaError: String? = null,
    ) {
        fun fingerprint(): String = buildString {
            append("titan:")
            append("head=").append(if (headExists) headSha256 ?: "unreadable" else "absent")
            append(":headStat=").append(headLength).append(':').append(headLastModified)
            append(":patchHash=").append(head?.patchHash ?: "none")
            append(":targetId=").append(head?.targetId ?: "none")
            append(":crashCount=").append(head?.crashCount ?: -1)
            append(":patchFile=").append(patchFileLength).append(':').append(patchFileLastModified)
            append(":patchVersionCode=").append(patchMeta?.patchVersionCode ?: "none")
            append(":metaTargetId=").append(patchMeta?.targetId ?: "none")
            append(":metaStatus=").append(patchMeta?.status ?: "none")
            append(":metaLoadPolicy=").append(patchMeta?.loadPolicy ?: "none")
            append(":metaBootPolicy=").append(patchMeta?.bootLoadSyncPolicy ?: "none")
            if (headError != null) append(":headError=").append(headError)
            if (patchMetaError != null) append(":patchMetaError=").append(patchMetaError)
        }

        fun describeForLog(): String = buildString {
            append("headExists=").append(headExists)
            append(", headPatchHash=").append(head?.patchHash ?: "none")
            append(", headTargetId=").append(head?.targetId ?: "none")
            append(", headCrashCount=").append(head?.crashCount ?: -1)
            append(", patchStatusExists=").append(patchStatusExists)
            append(", patchMeta=")
            if (patchMeta == null) {
                append("none")
            } else {
                append("{targetId=").append(patchMeta.targetId ?: "none")
                append(", status=").append(patchMeta.status ?: "none")
                append(", loadPolicy=").append(patchMeta.loadPolicy ?: "none")
                append(", bootLoadSyncPolicy=").append(patchMeta.bootLoadSyncPolicy ?: "none")
                append(", patchVersionCode=").append(patchMeta.patchVersionCode ?: "none")
                append(", patchVersionName=").append(patchMeta.patchVersionName ?: "none")
                append(", hostVersionCode=").append(patchMeta.hostVersionCode ?: "none")
                append(", hostVersionName=").append(patchMeta.hostVersionName ?: "none")
                append('}')
            }
            if (headError != null) append(", headError=").append(headError)
            if (patchMetaError != null) append(", patchMetaError=").append(patchMetaError)
        }
    }

    private data class Head(
        val patchHash: String,
        val targetId: String?,
        val crashCount: Int,
    )

    private data class PatchMeta(
        val targetId: String?,
        val status: Int?,
        val loadPolicy: Int?,
        val bootLoadSyncPolicy: Int?,
        val patchVersionCode: Long?,
        val patchVersionName: String?,
        val hostVersionCode: Long?,
        val hostVersionName: String?,
    )

    private data class PatchMetaResult(
        val meta: PatchMeta? = null,
        val error: String? = null,
    )

    private data class LoaderState(
        val loadState: Int? = null,
        val currentPatchId: String? = null,
        val error: String? = null,
    ) {
        fun describeForLog(): String = buildString {
            append("loaderState=").append(loadState ?: "unavailable")
            append(", currentPatchId=").append(currentPatchId ?: "none")
            if (error != null) append(", loaderError=").append(error)
        }
    }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
