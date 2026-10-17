package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints
import com.xiyunmn.puredupan.hook.ui.UiText
import java.io.File
import java.lang.reflect.Method
import java.util.Locale

internal object DownloadOpenWithHook {
    private const val TAG = "DownloadOpenWithHook"
    private val installed = mutableSetOf<Method>()
    private class OpenButtonState(var path: String? = null)

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val bindings = DownloadOpenWithBindingsResolver.resolve(cl) ?: return
        try {
            val base = Class.forName(BaiduTransferHookPoints.DOWNLOAD_LIST_BASE_FRAGMENT, false, cl)
            val transmission = Class.forName(BaiduTransferHookPoints.TRANSMISSION_DOWNLOAD_FRAGMENT, false, cl)
            // Hidden tabs also resume, but only the current tab owns the shared toolbar selection.
            val currentFragment = transmission.getDeclaredField("mCurFragment").apply { isAccessible = true }
            val getActivity = base.getMethod("getActivity")
            val getView = base.getMethod("getView")
            val fileProvider = Class.forName(BaiduTransferHookPoints.ANDROIDX_FILE_PROVIDER, false, cl)
            val getUri = fileProvider.getMethod("getUriForFile", android.content.Context::class.java, String::class.java, File::class.java)

            fun update(instance: Any, models: List<*>?, preserveSelection: Boolean = false) {
                if (!transmission.isInstance(instance)) return
                val activity = getActivity.invoke(instance) as? Activity ?: return
                val root = getView.invoke(instance) as? View ?: return
                val resources = root.resources
                val deleteId = resources.getIdentifier("edit_tools_delete_btn", "id", activity.packageName)
                val delete = root.findViewById<Button>(deleteId) ?: return
                val bar = delete.parent as? LinearLayout ?: return
                var button = (0 until bar.childCount).map { bar.getChildAt(it) }
                    .singleOrNull { it.tag is OpenButtonState } as? Button
                if (!HookSettings.settingsSnapshot().isDownloadOpenWithEnabled) {
                    button?.let { bar.removeView(it) }
                    return
                }
                if (button == null) {
                    val layoutId = resources.getIdentifier("transfer_bottom_edit_tools", "layout", activity.packageName)
                    val template = LayoutInflater.from(activity).inflate(layoutId, bar, false) as ViewGroup
                    val openId = resources.getIdentifier("edit_tools_other_app_open", "id", activity.packageName)
                    button = template.findViewById<Button>(openId)
                    (button.parent as ViewGroup).removeView(button)
                    button.id = View.generateViewId()
                    button.tag = OpenButtonState()
                    button.text = UiText.Settings.OPEN_WITH_BUTTON
                    button.contentDescription = UiText.Settings.OPEN_WITH
                    button.visibility = View.VISIBLE
                    button.isEnabled = false
                    button.layoutParams = LinearLayout.LayoutParams(delete.layoutParams as LinearLayout.LayoutParams)
                    button.setOnClickListener { view ->
                        val path = (view.tag as OpenButtonState).path ?: return@setOnClickListener
                        runCatching {
                            openWith(activity, path) { file ->
                                getUri.invoke(null, activity, "${activity.packageName}.fileprovider", file) as Uri
                            }
                        }.onFailure {
                            XposedCompat.logW("[$TAG] open failed: ${it.message}")
                            Toast.makeText(activity, UiText.Settings.OPEN_WITH_FAILED, Toast.LENGTH_SHORT).show()
                        }
                    }
                    bar.addView(button, bar.indexOfChild(delete) + 1)
                }
                if (preserveSelection) return
                val paths = models.orEmpty().mapNotNull { model ->
                    model?.let { bindings.path.invoke(it) as? String }?.takeIf { it.isNotBlank() }
                }.distinct()
                // Selecting an expanded directory also selects its child rows.
                val path = paths.singleOrNull() ?: paths.singleOrNull { parent ->
                    !parent.startsWith("content://") && localFile(parent).isDirectory &&
                        paths.all { it == parent || it.startsWith(parent.trimEnd('/') + "/") }
                }
                (button.tag as OpenButtonState).path = path
                button.isEnabled = path != null && (path.startsWith("content://") || localFile(path).exists())
            }

            fun hookAfter(method: Method, after: (Any, List<Any?>) -> Unit) {
                if (method !in installed) {
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        runCatching { after(chain.thisObject!!, chain.args) }
                            .onFailure { XposedCompat.logW("[$TAG] button update failed: ${it.message}") }
                        result
                    }
                    installed += method
                }
            }
            listOf("showEditToolsBox", "hideEditToolsBox").forEach { name ->
                val method = base.declaredMethods.single { it.name == name }
                hookAfter(method) { instance, _ -> update(instance, null, preserveSelection = name == "showEditToolsBox") }
            }
            val other = base.getDeclaredMethod("showOtherAppBtn", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            if (other !in installed) {
                mod.hook(other).intercept { chain ->
                    if (HookSettings.settingsSnapshot().isDownloadOpenWithEnabled) chain.proceed(arrayOf(false, false))
                    else chain.proceed()
                }
                installed += other
            }

            val all = Class.forName(BaiduTransferHookPoints.ALL_DOWNLOAD_TASK_FRAGMENT, false, cl)
            val getAdapter = all.getDeclaredMethod("getAdapter").apply { isAccessible = true }
            val getSelected = getAdapter.returnType.methods.single {
                it.parameterCount == 0 && it.returnType.name == BaiduTransferHookPoints.ANDROIDX_ARRAY_MAP
            }.apply { isAccessible = true }
            val allOwner = all.getDeclaredField("selectStateListener").apply { isAccessible = true }
            val refresh = all.declaredMethods.single {
                it.name == "refreshEdit" && (it.parameterCount == 0 || it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)))
            }
            hookAfter(refresh) { instance, _ ->
                val owner = allOwner.get(instance)!!
                if (transmission.isInstance(owner) && currentFragment.get(owner) === instance) {
                    val selected = getSelected.invoke(getAdapter.invoke(instance)) as Map<*, *>
                    val model = selected.values.singleOrNull()?.let { bindings.allItem.invoke(null, it) }
                    update(owner, listOfNotNull(model))
                }
            }
            val completed = Class.forName(BaiduTransferHookPoints.COMPLETED_DOWNLOAD_FRAGMENT, false, cl)
            val completedOwner = completed.getDeclaredField("selectStateListener").apply { isAccessible = true }
            hookAfter(completed.getDeclaredMethod("handleSelectedList", List::class.java)) { instance, args ->
                val owner = completedOwner.get(instance)!!
                if (transmission.isInstance(owner) && currentFragment.get(owner) === instance) {
                    update(owner, bindings.completedItems.invoke(null, args[0]) as List<*>)
                }
            }
            XposedCompat.logD("[$TAG] hooks ready")
        } catch (e: ReflectiveOperationException) {
            XposedCompat.logW("[$TAG] install failed: ${e.message}")
        }
    }

    private fun localFile(path: String): File =
        if (path.startsWith("file://")) File(requireNotNull(Uri.parse(path).path)) else File(path)

    private fun openWith(activity: Activity, path: String, fileUri: (File) -> Uri) {
        val isContent = path.startsWith("content://")
        val file = if (isContent) null else localFile(path)
        if (file != null && !file.exists()) {
            Toast.makeText(activity, UiText.Settings.OPEN_WITH_MISSING_FILE, Toast.LENGTH_SHORT).show()
            return
        }
        var uri = if (file != null) fileUri(file) else Uri.parse(path)
        if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(activity, uri)) {
            uri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        }
        val type = if (file?.isDirectory == true) DocumentsContract.Document.MIME_TYPE_DIR else {
            activity.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                (file?.extension ?: uri.lastPathSegment?.substringAfterLast('.', ""))?.lowercase(Locale.ROOT),
            ) ?: "application/octet-stream"
        }
        val isDirectory = type == DocumentsContract.Document.MIME_TYPE_DIR
        val intents = mutableListOf<Intent>()
        fun addViews(target: Uri, grant: Boolean) {
            val types = if (isDirectory) listOf(
                DocumentsContract.Document.MIME_TYPE_DIR, "resource/folder", "inode/directory", "application/vnd.folder",
            ) else listOf(type)
            types.forEach { mime ->
                intents += Intent(Intent.ACTION_VIEW).setDataAndType(target, mime).apply {
                    if (grant) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri(file?.name ?: "download", target)
                    }
                }
            }
            if (isDirectory) {
                intents += Intent(intents.last()).setAction("org.openintents.action.VIEW_DIRECTORY")
                    .setDataAndType(target, "resource/folder")
            }
        }
        // A document URI lets file managers recognize a public directory. The host's own
        // FileProvider is also offered for handlers that accept granted content URIs.
        if (isDirectory && file != null) externalDirectoryUri(activity, file)?.let { addViews(it, false) }
        addViews(uri, true)
        val excluded = linkedSetOf<ComponentName>()
        var hasHandler = false
        intents.forEach { intent ->
            activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { result ->
                val info = result.activityInfo
                if (info.packageName == activity.packageName) {
                    excluded += ComponentName(info.packageName, info.name)
                } else if (info.exported && info.enabled) {
                    hasHandler = true
                }
            }
        }
        if (!hasHandler) {
            Toast.makeText(activity, UiText.Settings.OPEN_WITH_UNAVAILABLE, Toast.LENGTH_SHORT).show()
            return
        }
        val chooser = Intent.createChooser(intents.first(), UiText.Settings.OPEN_WITH)
        // Alternate intents let Android resolve the whole app list for each supported
        // directory contract; initial explicit targets are capped by the system chooser.
        if (intents.size > 1) chooser.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, intents.drop(1).toTypedArray())
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        chooser.clipData = ClipData.newRawUri(file?.name ?: "download", uri)
        activity.startActivity(chooser)
    }

    private fun externalDirectoryUri(activity: Activity, file: File): Uri? {
        val path = file.canonicalPath
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory().canonicalPath
        val roots = mutableListOf(primary to "primary")
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getSystemService(StorageManager::class.java).storageVolumes.forEach { volume ->
                if (!volume.isPrimary) {
                    val root = volume.directory?.canonicalPath
                    val uuid = volume.uuid
                    if (root != null && uuid != null) roots += root to uuid
                }
            }
        }
        val (root, volume) = roots.firstOrNull { (root, _) -> path == root || path.startsWith("$root/") }
            ?: return null
        val relative = path.removePrefix(root).trimStart('/')
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "$volume:$relative")
    }
}
