package com.nearer.app

/** Restricts received app archives to the exact bundle layout produced by NeaRer. */
internal object AppBundleEntryPolicy {
    private val splitEntry = Regex("^splits/[A-Za-z0-9._-]+\\.apk$")

    fun isAllowedApkEntry(name: String): Boolean =
        name == "base.apk" || splitEntry.matches(name)

    fun sessionName(name: String): String {
        require(isAllowedApkEntry(name)) { "Unsupported app bundle entry." }
        return if (name == "base.apk") "base.apk" else name.substringAfterLast('/')
    }
}
