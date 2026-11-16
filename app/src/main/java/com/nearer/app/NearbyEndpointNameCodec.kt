package com.nearer.app

/**
 * Decodes the display name and stable identity packed into a Nearby endpoint name.
 * Legacy names without a valid identity remain displayable with an empty stable ID.
 */
internal object NearbyEndpointNameCodec {
    fun decode(value: String): Pair<String, String> {
        val split = value.lastIndexOf('|')
        if (split in 1 until value.lastIndex) {
            val identity = NeaRerIdentity.normalize(value.substring(split + 1))
            if (identity != null) return value.substring(0, split).take(23) to identity
        }
        return value.take(23) to ""
    }
}
