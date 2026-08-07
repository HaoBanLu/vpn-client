package com.vpn.member.update

import org.junit.Assert.assertEquals
import org.junit.Test

/** App 更新安装器可单测部分（完整安装流程需真机/模拟器）。 */
class AppUpdateInstallerTest {
    @Test
    fun sanitizeFileName_replacesUnsafeChars() {
        assertEquals("3.7_beta", AppUpdateInstaller.sanitizeFileNameForTest("3.7 beta"))
    }

    @Test
    fun sanitizeFileName_fallbackWhenBlank() {
        assertEquals("latest", AppUpdateInstaller.sanitizeFileNameForTest("   "))
    }

    @Test
    fun isPendingInstallObsolete_whenVersionCodeNotNewer() {
        assertEquals(
            true,
            AppUpdateInstaller.isPendingInstallObsolete(37, "3.7", 37, "3.7"),
        )
        assertEquals(
            false,
            AppUpdateInstaller.isPendingInstallObsolete(38, "3.8", 37, "3.7"),
        )
    }

    @Test
    fun isPendingInstallObsolete_whenVersionNameMatches() {
        assertEquals(
            true,
            AppUpdateInstaller.isPendingInstallObsolete(0, "3.7", 37, "3.7"),
        )
    }

    @Test
    fun normalizePendingVersionLabel_fromApkFileName() {
        assertEquals("3.7", AppUpdateInstaller.normalizePendingVersionLabel("kuayun-3.7.apk"))
    }
}
