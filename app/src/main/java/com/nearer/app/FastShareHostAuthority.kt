package com.nearer.app

import java.net.Inet6Address
import java.net.InetAddress

/** Strict parser for the authority portion of an HTTP Host header. */
internal object FastShareHostAuthority {
    fun isValid(value: String): Boolean {
        if (value.isEmpty() || value.any { it.code !in 0x21..0x7e || it in "/\\\\?#@" }) return false
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close <= 1) return false
            val suffix = value.substring(close + 1)
            if (suffix.isNotEmpty() && !validPortSuffix(suffix)) return false
            return runCatching { InetAddress.getByName(value.substring(1, close)) }.getOrNull() is Inet6Address
        }
        if (value.count { it == ':' } > 1) return false
        val colon = value.lastIndexOf(':')
        val host = if (colon >= 0) value.substring(0, colon) else value
        if (colon >= 0 && !validPortSuffix(value.substring(colon))) return false
        if (host.isEmpty() || host.length > 253) return false
        val normalized = host.removeSuffix(".")
        if (normalized.isEmpty()) return false
        return normalized.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun validPortSuffix(value: String): Boolean {
        if (!value.startsWith(":")) return false
        val digits = value.substring(1)
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return false
        val port = digits.toIntOrNull() ?: return false
        return port in 1..65535
    }
}
