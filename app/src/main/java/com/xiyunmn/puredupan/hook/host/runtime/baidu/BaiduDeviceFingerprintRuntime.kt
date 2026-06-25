package com.xiyunmn.puredupan.hook.host.runtime.baidu

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.xiyunmn.puredupan.hook.host.HostDeviceFingerprintCollector
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduDeviceFingerprintSymbols
import java.io.File
import java.lang.reflect.Modifier

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
        return linkedMapOf(
            "collectorProfile" to profile,
            "baiduDeviceInfo" to collectDeviceInfo(context),
            "baiduRiskFingerprint" to collectRiskFingerprint(context),
            "cuidPersistenceHints" to collectCuidPersistenceHints(context),
            "environmentRiskContext" to collectEnvironmentRiskContext(context),
        )
    }

    @SuppressLint("HardwareIds")
    private fun collectDeviceInfo(context: Context): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>(
            "systemAndroidId" to runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrElse(::unavailable),
        )
        for ((outputKey, fieldName) in BaiduDeviceFingerprintSymbols.appCommonStringFields) {
            fields[outputKey] = readStaticField(
                context = context,
                className = BaiduDeviceFingerprintSymbols.APP_COMMON,
                fieldName = fieldName,
            ).getOrElse(::unavailable)
        }
        fields["cuid"] = invokeStaticContextMethod(
            context = context,
            className = BaiduDeviceFingerprintSymbols.DEVICE_ID,
            methodName = "getCUID",
        ).getOrElse(::unavailable)
        fields["device_id"] = invokeStaticContextMethod(
            context = context,
            className = BaiduDeviceFingerprintSymbols.DEVICE_ID,
            methodName = "getDeviceID",
        ).getOrElse(::unavailable)
        fields["commonParamCuid"] = invokeStaticContextMethod(
            context = context,
            className = BaiduDeviceFingerprintSymbols.COMMON_PARAM,
            methodName = "getCUID",
        ).getOrElse(::unavailable)
        fields["swanUuid"] = readSwanUuid(context).getOrElse(::unavailable)
        return fields
    }

    private fun collectRiskFingerprint(context: Context): Map<String, Any?> {
        return linkedMapOf(
            "sofireVersion" to invokeStaticContextMethod(
                context = context,
                className = BaiduDeviceFingerprintSymbols.SOFIRE_FH,
                methodName = "getVersion",
            ).recoverCatching {
                invokeStaticNoArgMethod(
                    context = context,
                    className = BaiduDeviceFingerprintSymbols.SOFIRE_FH,
                    methodName = "getVersion",
                ).getOrThrow()
            }.getOrElse(::unavailable),
            "sofireGd" to invokeStaticContextMethod(
                context = context,
                className = BaiduDeviceFingerprintSymbols.SOFIRE_FH,
                methodName = "gd",
            ).getOrElse(::unavailable),
            "sofireGz" to "not_collected: may initialize active Sofire fingerprint collection",
            "sofireTaskAntiCheatGzfi" to "not_collected: task anti-cheat fingerprint",
        )
    }

    private fun collectCuidPersistenceHints(context: Context): Map<String, Any?> {
        val filesDir = context.filesDir
        return linkedMapOf(
            "filesLibcuidExists" to File(filesDir, "libcuid.so").exists(),
            "bohriumLibbhExists" to File(filesDir, "bohrium/libbh.so").exists(),
            "settingsSecureDeviceIdV2" to secureSettingStatus(context, "com.baidu.deviceid.v2"),
            "settingsSecureDeviceId" to secureSettingStatus(context, "com.baidu.deviceid"),
            "settingsSystemBdSettingI" to systemSettingStatus(context, "bd_setting_i"),
        )
    }

    private fun collectEnvironmentRiskContext(context: Context): Map<String, Any?> {
        return linkedMapOf(
            "httpProxyHost" to System.getProperty("http.proxyHost"),
            "httpProxyPort" to System.getProperty("http.proxyPort"),
            "vpnTransportDetected" to vpnTransportDetected(context),
            "hostDebuggable" to ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0),
            "buildTags" to Build.TAGS,
            "buildFingerprint" to Build.FINGERPRINT,
            "buildBrand" to Build.BRAND,
            "buildManufacturer" to Build.MANUFACTURER,
            "buildModel" to Build.MODEL,
            "buildDevice" to Build.DEVICE,
            "buildProduct" to Build.PRODUCT,
            "buildHardware" to Build.HARDWARE,
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
        )
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

    private fun secureSettingStatus(context: Context, key: String): String {
        return runCatching {
            valueStatus(Settings.Secure.getString(context.contentResolver, key))
        }.getOrElse(::unavailable)
    }

    @SuppressLint("HardwareIds")
    private fun systemSettingStatus(context: Context, key: String): String {
        return runCatching {
            valueStatus(Settings.System.getString(context.contentResolver, key))
        }.getOrElse(::unavailable)
    }

    private fun valueStatus(value: String?): String {
        return if (value.isNullOrBlank()) {
            "missing"
        } else {
            "present(length=${value.length})"
        }
    }

    @SuppressLint("MissingPermission")
    private fun vpnTransportDetected(context: Context): Any {
        return runCatching {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
                ?: return@runCatching false
            val activeNetwork = connectivityManager.activeNetwork ?: return@runCatching false
            connectivityManager.getNetworkCapabilities(activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.fold(
            onSuccess = { it },
            onFailure = ::unavailable,
        )
    }

    private fun unavailable(t: Throwable): String {
        val name = t::class.java.simpleName.ifBlank { "Throwable" }
        val message = t.message?.takeIf { it.isNotBlank() }
        return if (message == null) {
            "unavailable:$name"
        } else {
            "unavailable:$name:$message"
        }
    }
}
