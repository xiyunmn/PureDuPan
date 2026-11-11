package com.xiyunmn.puredupan.hook.host.runtime.baidu

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduDeviceFingerprintSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData

/** 从宿主 OAID 启动任务的配置读取与回调写入关系恢复诊断符号。 */
internal object IntlDeviceFingerprintDexKitResolver {
    const val CACHE_ID = "intl_device_fingerprint_v1"
    private const val TAG = "IntlDeviceFingerprintDexKitResolver"
    private const val HELIOS = "com.baidu.helios.HeliosManager"

    data class Bindings(val oaid: Field, val honorOaid: Field, val configFactory: Method, val configString: Method)

    /** UI 只恢复已有结果，扫描统一由 DexKit 预热执行。 */
    fun cached(cl: ClassLoader): Bindings? = when (
        val result = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { restore(cl, it) }
    ) {
        is DexKitCompat.CachedResult.Found -> result.value
        else -> null
    }

    fun resolve(cl: ClassLoader): Bindings? {
        cached(cl)?.let { return it }
        val ref = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val task = bridge.findMethod(FindMethod.create().matcher(
                MethodMatcher.create().returnType("java.lang.String").paramTypes()
                    .usingEqStrings("OAIDStartupTask"),
            )).singleOrNull() ?: return@withBridge null
            val methods = bridge.findMethod(FindMethod.create().matcher(
                MethodMatcher.create().declaredClass(task.className),
            ))
            fun source(key: String, request: String): MethodData? = methods.filter { method ->
                key in method.usingStrings && method.returnTypeName == "void" &&
                    !Modifier.isStatic(method.modifiers) && method.paramCount == 2 &&
                    method.paramTypeNames[1] == HELIOS && method.invokes.any {
                        it.className == HELIOS && it.methodName == request && it.returnTypeName == "void" &&
                            it.paramTypeNames == listOf("com.baidu.helios.OnGetIdResultCallback")
                    }
            }.singleOrNull()
            val oaid = source(BaiduDeviceFingerprintSymbols.OAID_CONFIG_KEY, "requestOid")
                ?: return@withBridge null
            val honor = source(BaiduDeviceFingerprintSymbols.HONOR_OAID_CONFIG_KEY, "requestHonorOaid")
                ?: return@withBridge null
            val configOwner = oaid.paramTypeNames[0]
            if (honor.paramTypeNames[0] != configOwner) return@withBridge null
            fun getter(method: MethodData) = method.invokes.filter {
                it.className == configOwner && !Modifier.isStatic(it.modifiers) &&
                    it.returnTypeName == "java.lang.String" && it.paramTypeNames == listOf("java.lang.String")
            }.distinctBy { it.descriptor }.singleOrNull()
            val getString = getter(oaid) ?: return@withBridge null
            if (getter(honor)?.descriptor != getString.descriptor) return@withBridge null
            fun field(method: MethodData, key: String) = method.usingFields.filter {
                it.usingType == FieldUsingType.Write && Modifier.isStatic(it.field.modifiers) &&
                    it.field.typeName == "java.lang.String"
            }.map { it.field }.distinctBy { it.descriptor }.singleOrNull()?.takeIf { field ->
                field.writers.any { writer ->
                    key in writer.usingStrings && writer.returnTypeName == "void" &&
                        writer.paramTypeNames == listOf("java.lang.String", "android.os.Bundle") &&
                        writer.invokes.any { call ->
                            call.className == configOwner && call.returnTypeName == "void" &&
                                call.paramTypeNames == listOf("java.lang.String", "java.lang.String")
                        }
                }
            }
            val oaidField = field(oaid, BaiduDeviceFingerprintSymbols.OAID_CONFIG_KEY)
                ?: return@withBridge null
            val honorField = field(honor, BaiduDeviceFingerprintSymbols.HONOR_OAID_CONFIG_KEY)
                ?: return@withBridge null
            if (oaidField.className != honorField.className || oaidField.descriptor == honorField.descriptor) {
                return@withBridge null
            }
            val factory = methods.flatMap { it.invokes }.filter {
                it.className == configOwner && Modifier.isStatic(it.modifiers) &&
                    it.paramCount == 0 && it.returnTypeName == configOwner
            }.distinctBy { it.descriptor }.singleOrNull() ?: return@withBridge null
            DexKitCompat.MethodRef(oaidField.className, listOf(
                oaidField.fieldName, honorField.fieldName, configOwner, factory.methodName, getString.methodName,
            ).joinToString(";"))
        } ?: run {
            if (DexKitCompat.isTargetResolutionPersistenceAllowed()) {
                DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "OAID task/config/callback contract unresolved")
            }
            return null
        }
        val result = restore(cl, ref)
        if (result == null) {
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "OAID reflection contract invalid")
            return null
        }
        DexKitCompat.putCachedMethod(TAG, CACHE_ID, ref)
        return result
    }

    private fun restore(cl: ClassLoader, ref: DexKitCompat.MethodRef): Bindings? = runCatching {
        val names = ref.methodName.split(';')
        if (names.size != 5 || names.any { it.isBlank() }) return null
        val owner = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val oaid = owner.getDeclaredField(names[0])
        val honor = owner.getDeclaredField(names[1])
        if (oaid == honor || listOf(oaid, honor).any {
            !Modifier.isStatic(it.modifiers) || it.type != String::class.java
        }) return null
        val config = XposedCompat.findClassOrNull(names[2], cl) ?: return null
        val factory = config.getDeclaredMethod(names[3])
        val getter = config.getMethod(names[4], String::class.java)
        if (!Modifier.isStatic(factory.modifiers) || factory.returnType != config ||
            Modifier.isStatic(getter.modifiers) || getter.returnType != String::class.java
        ) return null
        Bindings(oaid.apply { isAccessible = true }, honor.apply { isAccessible = true },
            factory.apply { isAccessible = true }, getter.apply { isAccessible = true })
    }.getOrNull()
}
