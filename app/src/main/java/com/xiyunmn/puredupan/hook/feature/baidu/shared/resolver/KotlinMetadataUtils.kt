package com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver

internal object KotlinMetadataUtils {
    fun metadataTokens(clazz: Class<*>): Set<String> {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return emptySet()
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return emptySet()
        return d2.filterIsInstance<String>().toSet()
    }

    fun metadataContainsAll(clazz: Class<*>, tokens: Collection<String>): Boolean {
        val metadataTokens = metadataTokens(clazz)
        return tokens.all { token ->
            metadataTokens.any { it == token || it.contains(token) }
        }
    }

    /** 检查类是否保留非空的 Kotlin Metadata d2 标识。 */
    fun hasMetadata(clazz: Class<*>): Boolean = metadataTokens(clazz).isNotEmpty()

    /** 有 Metadata 时校验全部标识；缺失时交由调用方按类名和签名验证。 */
    fun metadataContainsAllOrAbsent(clazz: Class<*>, tokens: Collection<String>): Boolean {
        if (!hasMetadata(clazz)) return true
        return metadataContainsAll(clazz, tokens)
    }
}
