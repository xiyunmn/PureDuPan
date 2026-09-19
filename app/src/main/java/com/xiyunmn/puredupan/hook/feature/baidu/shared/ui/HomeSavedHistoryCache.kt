package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.concurrent.Executors
import org.json.JSONObject

/** Small account-scoped snapshot in the existing host-private module state preferences. */
internal object HomeSavedHistoryCache {
    private const val KEY = "home_saved_history_snapshot_v1"
    private const val MAX_JSON_CHARS = 128 * 1024
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, "PureDuPan-save-cache").apply { isDaemon = true }
    }
    private val codecs = mutableMapOf<ClassLoader, JsonCodec>()
    private val identityFields = mutableMapOf<Class<*>, Pair<Field, Field?>>()

    private class JsonCodec(cl: ClassLoader) {
        private val type = requireNotNull(XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.GSON, cl))
        private val gson = type.getDeclaredConstructor().newInstance()
        private val toJson: Method = type.getMethod("toJson", Any::class.java)
        private val fromJson: Method = type.getMethod("fromJson", String::class.java, Class::class.java)

        fun encode(value: Any): String = toJson.invoke(gson, value) as String
        fun decode(json: String, target: Class<*>): Any? = fromJson.invoke(gson, json, target)
    }

    private fun codec(type: Class<*>): JsonCodec = synchronized(codecs) {
        val cl = requireNotNull(type.classLoader)
        codecs.getOrPut(cl) { JsonCodec(cl) }
    }

    fun convert(source: Any, target: Class<*>): Any? = runCatching {
        val json = codec(target)
        json.decode(json.encode(source), target)
    }.getOrNull()

    fun read(uid: String, itemClass: Class<*>): List<Any>? = runCatching {
        val context = HookSettings.appContext() ?: return null
        val raw = HookSettings.getModuleStatePrefs(context).getString(KEY, null) ?: return null
        if (raw.length > MAX_JSON_CHARS) return null
        val snapshot = JSONObject(raw)
        if (snapshot.optString("owner") != ownerKey(uid) ||
            snapshot.optString("itemClass") != itemClass.name ||
            snapshot.optLong("hostVersion") != hostVersion() ||
            !SavedHistorySnapshotRules.isFresh(snapshot.optLong("writtenAt"), System.currentTimeMillis())
        ) return null
        val arrayClass = java.lang.reflect.Array.newInstance(itemClass, 0).javaClass
        val items = codec(itemClass).decode(snapshot.getString("items"), arrayClass) as? Array<*>
            ?: return null
        if (items.size !in 1..SavedHistorySnapshotRules.MAX_ITEMS || items.any { !itemClass.isInstance(it) }) {
            return null
        }
        val list = items.filterNotNull()
        list.takeIf { SavedHistorySnapshotRules.extend(it, it, 10, ::identity) != null }
    }.getOrNull()

    fun write(uid: String, itemClass: Class<*>, items: List<Any>) {
        val context = HookSettings.appContext() ?: return
        val prefs = HookSettings.getModuleStatePrefs(context)
        val version = hostVersion()
        val snapshot = items.take(SavedHistorySnapshotRules.MAX_ITEMS)
        // 序列化与 apply 在后台串行完成，避免网络回调在滑动期间追加磁盘/JSON 工作。
        writer.execute {
            runCatching {
                val raw = JSONObject()
                    .put("owner", ownerKey(uid))
                    .put("itemClass", itemClass.name)
                    .put("hostVersion", version)
                    .put("writtenAt", System.currentTimeMillis())
                    .put("items", codec(itemClass).encode(snapshot))
                    .toString()
                if (raw.length <= MAX_JSON_CHARS) prefs.edit().putString(KEY, raw).apply()
            }
        }
    }

    fun extend(nativeItems: List<Any>, snapshot: List<Any>, limit: Int): List<Any>? =
        runCatching { SavedHistorySnapshotRules.extend(nativeItems, snapshot, limit, ::identity) }.getOrNull()

    fun refreshed(
        nativeItems: List<Any>, fresh: List<Any>, limit: Int, nativeUnchanged: Boolean,
    ): List<Any>? = runCatching {
        SavedHistorySnapshotRules.refreshed(nativeItems, fresh, limit, nativeUnchanged, ::identity)
    }.getOrNull()

    private fun identity(item: Any): Pair<Long, Long?>? {
        val fields = synchronized(identityFields) {
            identityFields.getOrPut(item.javaClass) {
                val named = item.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
                    .mapNotNull { field ->
                        val name = field.declaredAnnotations.firstNotNullOfOrNull { annotation ->
                            annotation.takeIf { it.annotationClass.java.name.endsWith(".SerializedName") }
                                ?.let { it.annotationClass.java.getMethod("value").invoke(it) as? String }
                        }
                        name?.let { it to field.apply { isAccessible = true } }
                    }.toMap()
                requireNotNull(named["fs_id"]) to named["transfer_time"]
            }
        }
        val id = (fields.first.get(item) as? Number)?.toLong()?.takeIf { it > 0 } ?: return null
        return id to (fields.second?.get(item) as? Number)?.toLong()
    }

    private fun ownerKey(uid: String): String = MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun hostVersion(): Long {
        val context = HookSettings.appContext() ?: return -1
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
}
