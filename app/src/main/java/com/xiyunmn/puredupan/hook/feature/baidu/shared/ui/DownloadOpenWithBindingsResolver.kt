package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduTransferHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/** 复用宿主的数据转换方法，保留下载页的文件选择规则。 */
internal object DownloadOpenWithBindingsResolver {
    const val CACHE_ID = "download_open_with_bindings_v1"
    private const val PATH_ID = "download_edit_local_path_v1"
    private const val ALL_ID = "download_all_edit_conversion_v1"
    private const val COMPLETED_ID = "download_completed_edit_conversion_v1"
    val cacheIds = listOf(CACHE_ID, PATH_ID, ALL_ID, COMPLETED_ID)
    private const val TAG = "DownloadOpenWithBindingsResolver"
    private var ready: Bindings? = null

    data class Bindings(val path: Method, val allItem: Method, val completedItems: Method)

    fun resolve(cl: ClassLoader): Bindings? {
        ready?.let { return record(it) }
        val util = XposedCompat.findClassOrNull(BaiduTransferHookPoints.DOWNLOAD_EDIT_UTIL, cl) ?: return null
        val prefix = listOf(Integer.TYPE, String::class.java, String::class.java, java.lang.Long.TYPE,
            String::class.java, Integer.TYPE, Integer.TYPE, java.lang.Boolean.TYPE)
        val model = util.declaredMethods.filter {
            it.parameterCount == 2 && it.parameterTypes[1] == Activity::class.java
        }.map { it.parameterTypes[0] }.distinct().singleOrNull { type ->
            type.declaredConstructors.any { constructor ->
                val params = constructor.parameterTypes.toList()
                params.size >= prefix.size && params.take(prefix.size) == prefix &&
                    params.drop(prefix.size).all { it == String::class.java }
            }
        } ?: return null

        fun validate(id: String, ref: DexKitCompat.MethodRef): Method? {
            val owner = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
            return owner.declaredMethods.singleOrNull {
                it.name == ref.methodName && when (id) {
                    PATH_ID -> owner == model && !Modifier.isStatic(it.modifiers) &&
                        it.parameterCount == 0 && it.returnType == String::class.java
                    ALL_ID -> Modifier.isStatic(it.modifiers) && it.parameterCount == 1 && it.returnType == model
                    else -> Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(List::class.java)) &&
                        it.returnType == List::class.java
                }
            }?.apply { isAccessible = true }
        }
        val direct = mapOf(
            PATH_ID to DexKitCompat.MethodRef(model.name, "getLocalUrl"),
            ALL_ID to DexKitCompat.MethodRef(BaiduTransferHookPoints.ALL_DOWNLOAD_TASK_MODEL_KT, "toDownloadEditModel"),
            COMPLETED_ID to DexKitCompat.MethodRef(BaiduTransferHookPoints.COMPLETED_DOWNLOAD_DATA_INFO_KT, "toDownLoadEditModel"),
        )
        val methods = direct.mapValues { (id, ref) ->
            validate(id, ref) ?: when (val cached = DexKitCompat.getCachedMethod(TAG, id) { validate(id, it) }) {
                is DexKitCompat.CachedResult.Found -> cached.value
                else -> null
            }
        }.toMutableMap()
        if (methods.values.any { it == null }) {
            val refs = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
                bridge.setThreadNum(1)
                fun entry(owner: String, name: String) = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(owner).name(name).paramCount(0).returnType("void"),
                )).singleOrNull()
                val open = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(BaiduTransferHookPoints.DOWNLOAD_EDIT_UTIL)
                        .usingStrings("open_otherapp", "compressedfile_list_downloaded")
                        .paramTypes(model.name, Activity::class.java.name).returnType("void"),
                )).singleOrNull()
                val path = open?.invokes.orEmpty().singleOrNull {
                    it.className == model.name && it.paramTypeNames.isEmpty() && it.returnTypeName == "java.lang.String"
                }
                val all = entry(BaiduTransferHookPoints.ALL_DOWNLOAD_TASK_FRAGMENT, "detailClick")
                    ?.invokes.orEmpty().singleOrNull {
                        Modifier.isStatic(it.modifiers) && it.paramTypeNames.size == 1 && it.returnTypeName == model.name
                    }
                val vm = XposedCompat.findClassOrNull(BaiduTransferHookPoints.COMPLETED_DOWNLOAD_FRAGMENT, cl)
                    ?.getDeclaredMethod("getViewModel")?.returnType?.name
                val completedOpen = entry(BaiduTransferHookPoints.COMPLETED_DOWNLOAD_FRAGMENT, "otherAppClick")
                    ?.invokes.orEmpty().singleOrNull {
                        it.className == vm && it.returnTypeName == "void" && it.paramTypeNames == listOf(Activity::class.java.name)
                    }
                val completed = completedOpen?.invokes.orEmpty().singleOrNull {
                    Modifier.isStatic(it.modifiers) && it.returnTypeName == List::class.java.name &&
                        it.paramTypeNames == listOf(List::class.java.name)
                }
                mapOf(PATH_ID to path, ALL_ID to all, COMPLETED_ID to completed).mapValues { (_, method) ->
                    method?.let { DexKitCompat.MethodRef(it.className, it.name) }
                }
            } ?: return null
            refs.forEach { (id, ref) -> if (methods[id] == null && ref != null) methods[id] = validate(id, ref) }
        }
        if (methods.values.any { it == null }) {
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, "missing bindings=${methods.filterValues { it == null }.keys}")
            return null
        }
        return record(Bindings(methods.getValue(PATH_ID)!!, methods.getValue(ALL_ID)!!,
            methods.getValue(COMPLETED_ID)!!)).also { ready = it }
    }

    private fun record(bindings: Bindings): Bindings {
        listOf(PATH_ID to bindings.path, ALL_ID to bindings.allItem, COMPLETED_ID to bindings.completedItems).forEach { (id, method) ->
            DexKitCompat.putCachedMethod(TAG, id, DexKitCompat.MethodRef(method.declaringClass.name, method.name))
        }
        DexKitCompat.markTargetSuccess(TAG, CACHE_ID, "native edit model conversions and local path validated")
        return bindings
    }
}
