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

    /**
     * True when the class carries a Kotlin @Metadata annotation whose d2 token
     * array is non-empty. On builds where R8 has globally stripped @Metadata
     * (e.g. intl 13.11.9), this returns false for every class, which callers can
     * use to decide whether the metadata gate is trustworthy at all.
     */
    fun hasMetadata(clazz: Class<*>): Boolean = metadataTokens(clazz).isNotEmpty()

    /**
     * Softened metadata gate: acts as [metadataContainsAll] when the class still
     * carries @Metadata, but returns true when @Metadata is absent so the caller
     * can fall back to plaintext class-name + method-signature-shape validation.
     *
     * Hosts that retain @Metadata (cn / samsung) keep the exact same strict
     * behavior as before; only builds that stripped @Metadata degrade to the
     * fallback path.
     */
    fun metadataContainsAllOrAbsent(clazz: Class<*>, tokens: Collection<String>): Boolean {
        if (!hasMetadata(clazz)) return true
        return metadataContainsAll(clazz, tokens)
    }
}
