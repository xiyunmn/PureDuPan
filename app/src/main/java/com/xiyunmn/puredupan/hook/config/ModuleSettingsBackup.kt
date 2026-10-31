package com.xiyunmn.puredupan.hook.config

import android.content.Context
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.host.HostRegistry
import com.xiyunmn.puredupan.hook.host.HostRuntimeState
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** 跨宿主备份用户选项，排除本地资源和运行状态。 */
internal object ModuleSettingsBackup {
    private const val FORMAT = "PureDuPan.settings"
    private const val VERSION = 1
    private const val MAX_BYTES = 1024 * 1024

    private val localKeys = setOf(
        FeatureKeys.KEY_STORAGE_DOWNLOAD_TREE_URI,
        FeatureKeys.KEY_REPLACE_MEMBER_CARD_BACKGROUND,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_URI,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_SIZE_SIGNATURE,
    )
    private val intKeys = setOf(
        FeatureKeys.KEY_HOME_RECENT_ITEM_LIMIT,
        FeatureKeys.KEY_HOME_SAVE_ITEM_LIMIT,
        FeatureKeys.KEY_MY_PAGE_CONTENT_OFFSET_Y_DP,
        FeatureKeys.KEY_MEMBER_CARD_SIZE_WIDTH_DP,
        FeatureKeys.KEY_MEMBER_CARD_SIZE_HEIGHT_DP,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX,
    )
    private val stringKeys = setOf(
        FeatureKeys.KEY_DEFAULT_STARTUP_TAB,
        FeatureKeys.KEY_STORAGE_DOWNLOAD_TREE_URI,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_URI,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_SIZE_SIGNATURE,
    )
    data class ImportPreview(
        val sourcePackage: String,
        val keys: Set<String>,
        val values: Map<String, Any>,
        val skippedCount: Int,
    )

    private fun portableKeys(packageName: String): Set<String> =
        HostRuntimeState.featureStatusMapForPackage(packageName)
            .filterValues { it.isSupported() }.keys - localKeys

    private fun valueType(key: String): String = when (key) {
        in intKeys -> "int"
        in stringKeys -> "string"
        else -> "boolean"
    }

    private fun checkedValue(key: String, entry: JSONObject): Any {
        val type = valueType(key)
        require(entry.get("type") == type) { "设置值类型不正确" }
        val value = entry.get("value")
        return when (type) {
            "boolean" -> {
                require(value is Boolean) { "设置值类型不正确" }
                value
            }
            "string" -> {
                require(value is String) { "设置值类型不正确" }
                value
            }
            else -> {
                require(value is Int || value is Long) { "设置值类型不正确" }
                val number = (value as Number).toLong()
                require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "设置数值超出范围" }
                number.toInt()
            }
        }
    }

    fun write(context: Context, output: OutputStream) {
        val scope = portableKeys(context.packageName).sorted()
        val saved = ConfigManager.getPrefs(context).all
        val values = JSONObject()
        scope.forEach { key ->
            if (saved.containsKey(key)) {
                val entry = JSONObject().put("type", valueType(key)).put("value", saved[key])
                checkedValue(key, entry)
                values.put(key, entry)
            }
        }
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("sourcePackage", context.packageName)
            .put("moduleVersionCode", BuildConfig.VERSION_CODE)
            .put("exportedAt", System.currentTimeMillis())
            .put("scope", JSONArray(scope))
            .put("values", values)
        output.write(json.toString(2).toByteArray(Charsets.UTF_8))
    }

    fun read(context: Context, input: InputStream): ImportPreview {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_BYTES) { "备份文件不能超过 1 MiB" }
            bytes.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val tokener = JSONTokener(decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString())
        val json = tokener.nextValue() as? JSONObject ?: error("不是模块设置备份")
        require(tokener.nextClean() == '\u0000') { "备份文件内容不完整或已损坏" }
        require(json.get("format") == FORMAT && json.get("version") == VERSION) { "不支持此备份格式或版本" }
        val source = json.getString("sourcePackage")
        require(source in HostRegistry.supportedPackageNames) { "不支持此备份来源" }
        val scopeArray = json.getJSONArray("scope")
        val scope = linkedSetOf<String>()
        for (index in 0 until scopeArray.length()) {
            val key = scopeArray.get(index)
            require(key is String && scope.add(key)) { "备份范围无效" }
        }
        val entries = json.getJSONObject("values")
        val values = linkedMapOf<String, Any>()
        entries.keys().forEach { key ->
            require(key in scope) { "设置值不在备份范围内" }
            // 忽略未知配置键，并校验已知键的值类型。
            if (key in HostRegistry.supportedFeatureKeys) {
                values[key] = checkedValue(key, entries.getJSONObject(key))
            }
        }
        val eligible = scope intersect portableKeys(source) intersect portableKeys(context.packageName)
        return ImportPreview(source, eligible, values.filterKeys { it in eligible }, scope.size - eligible.size)
    }

    fun restore(context: Context, preview: ImportPreview): Boolean {
        val editor = ConfigManager.getPrefs(context).edit()
        // 整体替换可迁移选项，清除备份中缺失的旧值。
        portableKeys(context.packageName).forEach(editor::remove)
        preview.values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        return editor.commit()
    }
}
