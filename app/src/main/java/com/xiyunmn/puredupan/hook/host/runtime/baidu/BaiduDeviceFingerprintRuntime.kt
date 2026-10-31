package com.xiyunmn.puredupan.hook.host.runtime.baidu

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.xiyunmn.puredupan.hook.host.HostDeviceFingerprintCollector
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduDeviceFingerprintSymbols
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object BaiduDomesticDeviceFingerprintRuntime : HostDeviceFingerprintCollector {
    override fun collect(context: Context): Map<String, Any?> {
        return BaiduDeviceFingerprintCollector.collect(context, "domestic")
    }
}

internal object BaiduIntlDeviceFingerprintRuntime : HostDeviceFingerprintCollector {
    override fun collect(context: Context): Map<String, Any?> {
        return BaiduDeviceFingerprintCollector.collect(context, "intl")
    }
}

private object BaiduDeviceFingerprintCollector {
    fun collect(context: Context, profile: String): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            put("collectorProfile", profile)
            putIfNotEmpty("deviceFingerprint", collectDeviceInfo(context, profile == "intl"))
            putIfNotEmpty("riskFingerprint", collectRiskFingerprint(context))
        }
    }

    @SuppressLint("HardwareIds")
    private fun collectDeviceInfo(context: Context, international: Boolean): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>()
        fields.putIfUseful(
            "Android ID",
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull(),
        )
        for ((outputKey, fieldName) in BaiduDeviceFingerprintSymbols.appCommonStringFields) {
            fields.putIfUseful(
                outputKey,
                readStaticField(
                    context = context,
                    className = BaiduDeviceFingerprintSymbols.APP_COMMON,
                    fieldName = fieldName,
                ).getOrNull(),
            )
        }
        fields.putIfUseful("OAID", readOaid(context, international, honor = false))
        fields.putIfUseful("HONOR OAID", readOaid(context, international, honor = true))
        fields.putIfUseful(
            "CUID",
            invokeStaticContextMethod(
                context = context,
                className = BaiduDeviceFingerprintSymbols.DEVICE_ID,
                methodName = "getCUID",
            ).getOrNull(),
        )
        fields.putIfUseful(
            "Device ID",
            invokeStaticContextMethod(
                context = context,
                className = BaiduDeviceFingerprintSymbols.DEVICE_ID,
                methodName = "getDeviceID",
            ).getOrNull(),
        )
        fields.putIfUseful(
            "CommonParam CUID",
            invokeStaticContextMethod(
                context = context,
                className = BaiduDeviceFingerprintSymbols.COMMON_PARAM,
                methodName = "getCUID",
            ).getOrNull(),
        )
        fields.putIfUseful("Swan UUID", readSwanUuid(context).getOrNull())
        return fields
    }

    private fun collectRiskFingerprint(context: Context): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            putIfUseful(
                "Sofire Version",
                invokeStaticContextMethod(
                    context = context,
                    className = BaiduDeviceFingerprintSymbols.SOFIRE_FH,
                    methodName = "getVersion",
                ).recoverCatching {
                    invokeStaticNoArgMethod(
                        context = context,
                        className = BaiduDeviceFingerprintSymbols.SOFIRE_FH,
                        methodName = "getVersion",
                    ).getOrThrow()
                }.getOrNull(),
            )
            putIfUseful(
                "Sofire GD",
                invokeStaticContextMethod(
                    context = context,
                    className = BaiduDeviceFingerprintSymbols.SOFIRE_FH,
                    methodName = "gd",
                ).getOrNull(),
            )
        }
    }

    private fun MutableMap<String, Any?>.putIfNotEmpty(key: String, value: Map<String, Any?>) {
        if (value.isNotEmpty()) {
            this[key] = value
        }
    }

    private fun MutableMap<String, Any?>.putIfUseful(key: String, value: Any?) {
        val text = value?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            this[key] = if (value is String) text else value
        }
    }

    private fun readOaid(context: Context, international: Boolean, honor: Boolean): String? {
        val className = if (international) BaiduDeviceFingerprintSymbols.INTL_APP_COMMON
            else BaiduDeviceFingerprintSymbols.APP_COMMON
        val fieldName = if (international) {
            if (honor) BaiduDeviceFingerprintSymbols.INTL_HONOR_OAID_FIELD else BaiduDeviceFingerprintSymbols.INTL_OAID_FIELD
        } else {
            if (honor) "HONOR_OAID" else "OAID"
        }
        val key = if (honor) BaiduDeviceFingerprintSymbols.HONOR_OAID_CONFIG_KEY
            else BaiduDeviceFingerprintSymbols.OAID_CONFIG_KEY
        val cached = (readStaticField(context, className, fieldName).getOrNull() as? String)
            ?.takeIf { it.isNotBlank() }
            ?: (readPersonalConfigString(context, key, international).getOrNull() as? String)
        // 当前字段返回失败标记时，保留失败结果，避免读取旧缓存标识。
        return originalOaid(cached)
    }

    /** Helios 格式为 A10-Base32(UTF-8 OAID)-BDCC，读取中间段获取标识。 */
    private fun originalOaid(cached: String?): String? {
        val value = cached?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val original = if (value.startsWith("A10-")) {
            val parts = value.split('-')
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            if (parts.size != 3 || parts[1].isEmpty() || parts[2].isEmpty() ||
                parts[2].any { it !in alphabet }
            ) return null
            val bytes = ByteArrayOutputStream()
            var buffer = 0
            var bits = 0
            for (char in parts[1]) {
                val digit = alphabet.indexOf(char)
                if (digit < 0) return null
                buffer = (buffer shl 5) or digit
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    bytes.write(buffer shr bits)
                    buffer = buffer and ((1 shl bits) - 1)
                }
            }
            if (bits >= 5 || buffer != 0) return null
            runCatching {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            }.getOrNull() ?: return null
        } else {
            value
        }
        return original.takeIf {
            it.isNotBlank() && it != "-1" && it != "-2" &&
                !it.all { char -> char == '0' || char == '-' } && !it.any { char -> char.isISOControl() }
        }
    }

    private fun readStaticField(context: Context, className: String, fieldName: String): Result<Any?> {
        return runCatching {
            val clazz = loadClass(context, className)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            if (!Modifier.isStatic(field.modifiers)) {
                error("$className.$fieldName is not static")
            }
            field.get(null)
        }
    }

    private fun readPersonalConfigString(context: Context, key: String, international: Boolean): Result<Any?> {
        return runCatching {
            val clazz = loadClass(context, if (international) BaiduDeviceFingerprintSymbols.INTL_PERSONAL_CONFIG
                else BaiduDeviceFingerprintSymbols.PERSONAL_CONFIG)
            val getInstanceMethod = clazz.getDeclaredMethod(
                if (international) BaiduDeviceFingerprintSymbols.INTL_CONFIG_INSTANCE else "getInstance",
            )
            getInstanceMethod.isAccessible = true
            val instance = getInstanceMethod.invoke(null) ?: return@runCatching null
            val getStringMethod = clazz.getMethod(
                if (international) BaiduDeviceFingerprintSymbols.INTL_CONFIG_STRING else "getString", String::class.java,
            )
            getStringMethod.isAccessible = true
            getStringMethod.invoke(instance, key)
        }
    }

    private fun invokeStaticContextMethod(context: Context, className: String, methodName: String): Result<Any?> {
        return invokeStaticMethod(
            context = context,
            className = className,
            methodName = methodName,
            parameterTypes = arrayOf<Class<*>>(Context::class.java),
            args = arrayOf<Any>(context.applicationContext ?: context),
        )
    }

    private fun invokeStaticNoArgMethod(context: Context, className: String, methodName: String): Result<Any?> {
        return invokeStaticMethod(
            context = context,
            className = className,
            methodName = methodName,
            parameterTypes = emptyArray<Class<*>>(),
            args = emptyArray<Any>(),
        )
    }

    private fun invokeStaticMethod(
        context: Context,
        className: String,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any>,
    ): Result<Any?> {
        return runCatching {
            val clazz = loadClass(context, className)
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true
            if (!Modifier.isStatic(method.modifiers)) {
                error("$className.$methodName is not static")
            }
            method.invoke(null, *args)
        }
    }

    private fun readSwanUuid(context: Context): Result<Any?> {
        return runCatching {
            val clazz = loadClass(context, BaiduDeviceFingerprintSymbols.SWAN_UUID)
            val ofMethod = clazz.getDeclaredMethod("of", Context::class.java)
            ofMethod.isAccessible = true
            val instance = ofMethod.invoke(null, context.applicationContext ?: context)
                ?: return@runCatching null
            val getUuidMethod = clazz.getDeclaredMethod("getUUID")
            getUuidMethod.isAccessible = true
            getUuidMethod.invoke(instance)
        }
    }

    private fun loadClass(context: Context, className: String): Class<*> {
        return Class.forName(className, false, context.classLoader)
    }
}
