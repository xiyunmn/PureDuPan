package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAlbumBackupBarHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 国内系固定 AddUseCase 类内定位真实实现，国际版使用独立的备份栏入口。 */
internal object AlbumBackupBarAddUseCaseResolver {
    fun resolve(cl: ClassLoader): Method? {
        val owner = XposedCompat.findClassOrNull(BaiduAlbumBackupBarHookPoints.ALBUM_BACKUP_BAR_ADD_USE_CASE, cl)
            ?: return null
        val methods = owner.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.map(Class<*>::getName) == listOf(
                    BaiduAlbumBackupBarHookPoints.FILE_LIST_VIEW_MODEL, "java.util.Map",
                )
        }
        return (methods.filter { !it.isBridge && !it.isSynthetic }.singleOrNull() ?: methods.singleOrNull())
            ?.apply { isAccessible = true }
    }
}
