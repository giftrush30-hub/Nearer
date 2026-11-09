package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBundleEntryPolicyTest {
    @Test fun acceptsOnlyBaseAndNeaRerSplitApkEntries() {
        assertTrue(AppBundleEntryPolicy.isAllowedApkEntry("base.apk"))
        assertTrue(AppBundleEntryPolicy.isAllowedApkEntry("splits/1-split_config.arm64_v8a.apk"))
        assertFalse(AppBundleEntryPolicy.isAllowedApkEntry("INSTALLATION.txt"))
        assertFalse(AppBundleEntryPolicy.isAllowedApkEntry("../base.apk"))
        assertFalse(AppBundleEntryPolicy.isAllowedApkEntry("splits/../../payload.apk"))
        assertFalse(AppBundleEntryPolicy.isAllowedApkEntry("splits/not-an-apk.txt"))
    }

    @Test fun mapsSplitEntriesToSafeSessionNames() {
        assertEquals("base.apk", AppBundleEntryPolicy.sessionName("base.apk"))
        assertEquals("1-split_config.arm64_v8a.apk", AppBundleEntryPolicy.sessionName("splits/1-split_config.arm64_v8a.apk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSessionEntryNames() {
        AppBundleEntryPolicy.sessionName("splits/../../payload.apk")
    }
}
