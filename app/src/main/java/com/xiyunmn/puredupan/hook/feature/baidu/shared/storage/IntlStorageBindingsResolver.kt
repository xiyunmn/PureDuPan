package com.xiyunmn.puredupan.hook.feature.baidu.shared.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduStorageHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData

/** 国际存储入口按用途独立恢复，缺失组保留宿主行为。 */
internal object IntlStorageBindingsResolver {
    const val CACHE_ID = "intl_storage_bindings_v1"
    private const val TAG = "IntlStorageBindingsResolver"
    private val context = Context::class.java.name
    private val string = String::class.java.name
    private val ordinaryParams = listOf(context, BaiduStorageHookPoints.DOWNLOADABLE, string, "boolean", "int")
    private val linkParams = listOf(BaiduStorageHookPoints.FILE_INFO_GENERATOR) + ordinaryParams
    private val ids = listOf("download", "link", "mode", "creator", "legacy", "default", "absolute", "file", "directory", "folder", "parent", "name")
    val cacheIds = listOf(CACHE_ID) + ids.map(::cacheId)
    private fun cacheId(role: String) = "intl_storage_${role}_v1"

    data class Bindings(val methods: Map<String, Method>) {
        operator fun get(role: String): Method? = methods[role]
        val primaryReady get() = listOf("download", "link", "mode").all { it in methods }
        val complete get() = methods.size == ids.size
        val folderReady get() = listOf("folder", "parent", "name").all { it in methods }
    }

    fun resolve(cl: ClassLoader): Bindings {
        fun validate(role: String, ref: DexKitCompat.MethodRef): Method? {
            val owner = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
            return owner.declaredMethods.singleOrNull { method ->
                val params = method.parameterTypes.map { it.name }
                method.name == ref.methodName && !method.isSynthetic && when (role) {
                    "download" -> Modifier.isStatic(method.modifiers) && params == ordinaryParams && method.returnType == String::class.java
                    "link" -> Modifier.isStatic(method.modifiers) && params == linkParams && method.returnType == String::class.java
                    "mode" -> Modifier.isStatic(method.modifiers) && params == listOf(context) && method.returnType == Boolean::class.javaPrimitiveType
                    "creator" -> Modifier.isStatic(method.modifiers) && params == listOf(context, string, string) && method.returnType == String::class.java
                    "legacy", "default" -> Modifier.isStatic(method.modifiers) && params == listOf(context) && method.returnType == String::class.java
                    "absolute", "file", "directory" -> Modifier.isStatic(method.modifiers) && params == listOf(context, string) && method.returnType == String::class.java
                    "folder" -> Modifier.isStatic(method.modifiers) && params.size == 6 &&
                        params.take(2) == listOf(ContentResolver::class.java.name, Uri::class.java.name) &&
                        params.drop(3) == listOf("boolean", "int", "boolean") && method.returnType == Uri::class.java
                    else -> !Modifier.isStatic(method.modifiers) && params.isEmpty() && method.returnType == String::class.java
                }
            }?.apply { isAccessible = true }
        }
        val methods = mutableMapOf<String, Method>()
        ids.forEach { role ->
            val cached = DexKitCompat.getCachedMethod(TAG, cacheId(role)) { validate(role, it) }
            if (cached is DexKitCompat.CachedResult.Found) methods[role] = cached.value
        }
        if (methods.size != ids.size) {
            DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
                bridge.setThreadNum(1)
                fun find(params: List<String>, result: String = string, owner: String? = null, tokens: List<String> = emptyList()): List<MethodData> =
                    bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().apply {
                        paramTypes(*params.toTypedArray())
                        returnType(result)
                        owner?.let { declaredClass(it) }
                        if (tokens.isNotEmpty()) usingStrings(*tokens.toTypedArray())
                    })).filter { Modifier.isStatic(it.modifiers) && (it.modifiers and 0x1000) == 0 }
                fun put(role: String, data: MethodData?) {
                    if (data == null) return
                    validate(role, DexKitCompat.MethodRef(data.className, data.name))?.let { methods[role] = it }
                }
                val download = find(ordinaryParams, tokens = listOf("getDownloadPath", "filename:")).singleOrNull()
                val link = download?.let { find(linkParams, owner = it.className).singleOrNull() }
                put("download", download)
                put("link", link)
                val creator = find(listOf(context, string, string), tokens = listOf("createDownloadUriStr ", "mime_type", "relative_path")).singleOrNull()
                put("creator", creator)
                if (download != null && link != null) {
                    val sharedCalls = download.invokes.filter { call -> link.invokes.any { it.descriptor == call.descriptor } }
                    val mode = sharedCalls.singleOrNull { (creator == null || it.className == creator.className) &&
                        Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(context) && it.returnTypeName == "boolean" }
                    // 两个下载入口共同消费模式判定；URI 创建器可独立缺失。
                    if (creator == null || sharedCalls.any { it.descriptor == creator.descriptor }) put("mode", mode)
                }
                val legacy = find(listOf(context), tokens = listOf("Setting", "default_directory")).singleOrNull()
                put("legacy", legacy)
                put("default", legacy?.callers?.singleOrNull { it.className == legacy.className && it.paramTypeNames == listOf(context) && it.returnTypeName == string })
                creator?.let {
                    val queries = find(listOf(context, string), owner = it.className)
                    val file = queries.singleOrNull { "relative_path" in it.usingStrings && "_display_name" in it.usingStrings }
                    put("file", file)
                    put("directory", queries.singleOrNull { "relative_path" in it.usingStrings && "_display_name" !in it.usingStrings })
                    put("absolute", queries.singleOrNull { candidate -> "content://" in candidate.usingStrings && file != null && candidate.invokes.any { call -> call.descriptor == file.descriptor } })
                }
                val folder = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramCount(6).returnType(Uri::class.java.name)
                    .usingStrings("local_url", "local_parent_path", "file_name", "is_download_dir")))
                    .singleOrNull { data -> validate("folder", DexKitCompat.MethodRef(data.className, data.name)) != null }
                put("folder", folder)
                folder?.let { data ->
                    val type = data.paramTypeNames[2]
                    val getters = data.invokes.filter { it.className == type && it.paramTypeNames.isEmpty() && it.returnTypeName == string }.distinctBy { it.descriptor }
                    val model = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                        .declaredClass(type).name("toString").paramCount(0).returnType(string)
                        .usingStrings("FolderInfo(transferParentPath=", ", serverPath=", ", serverParentPath=", ", name=", ", downloadFrom="))).singleOrNull()
                    val constructor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                        .declaredClass(type).name("<init>").paramTypes(string, string, string, string, "int")
                        .usingStrings("transferParentPath", "serverPath", "serverParentPath", "name"))).singleOrNull()
                    fun getter(stable: String, verifiedObfuscated: String) = getters.singleOrNull { it.name == stable }
                        ?: if (model != null && constructor != null) getters.singleOrNull {
                            it.name == verifiedObfuscated && it.opNames == listOf("iget-object", "return-object") &&
                                it.usingFields.singleOrNull()?.field?.let { field -> field.className == type && field.typeName == string } == true
                        } else null
                    // 两批原始 DEX 已核实 f/c 的属性语义；Metadata 已被移除，保留这两个窄兼容名。
                    val parent = getter("getTransferParentPath", "f")
                    val name = getter("getName", "c")
                    if (parent != null && name != null && parent.usingFields.singleOrNull()?.field?.descriptor != name.usingFields.singleOrNull()?.field?.descriptor) {
                        put("parent", parent)
                        put("name", name)
                    }
                }
            }
        }
        // 缓存恢复时仍核对组内类型关系，避免只凭各自签名启用路径逻辑。
        if (methods["download"]?.declaringClass != methods["link"]?.declaringClass ||
            (methods["creator"] != null && methods["mode"]?.declaringClass != methods["creator"]?.declaringClass)) methods.remove("mode")
        val folderType = methods["folder"]?.parameterTypes?.get(2)
        if (methods["parent"]?.declaringClass != folderType || methods["name"]?.declaringClass != folderType) {
            methods.remove("parent")
            methods.remove("name")
        }
        if (DexKitCompat.isTargetResolutionPersistenceAllowed()) {
            methods.forEach { (role, method) -> DexKitCompat.putCachedMethod(TAG, cacheId(role), DexKitCompat.MethodRef(method.declaringClass.name, method.name)) }
            val missing = ids.filter { it !in methods }
            if (missing.isEmpty()) DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "all storage groups validated")
            else DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "missing groups=$missing; available=${methods.keys}")
        }
        return Bindings(methods)
    }
}
