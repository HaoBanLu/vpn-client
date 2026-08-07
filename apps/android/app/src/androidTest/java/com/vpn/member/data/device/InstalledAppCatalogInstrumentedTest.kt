package com.vpn.member.data.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机回归：USB 设备需已安装 com.qihoo.magic（分身大师）。
 * 旧 listLaunchableAppsOnly(MATCH_DEFAULT_ONLY) 会漏掉该包；listInstalledApps 应能枚举到。
 */
@RunWith(AndroidJUnit4::class)
class InstalledAppCatalogInstrumentedTest {
    @Test
    fun listInstalledApps_includesCloneMasterWhenInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = InstalledAppCatalog(context.applicationContext)

        val all = catalog.listInstalledApps()
        val launcherOnly = catalog.listLaunchableAppsOnly()

        val magic = all.find { it.packageName == CLONE_MASTER_PACKAGE }
        assertTrue(
            "本机应能通过 getInstalledApplications 找到 $CLONE_MASTER_PACKAGE（分身大师），" +
                "共 ${all.size} 个应用；请确认已授予 GET_INSTALLED_APPS / QUERY_ALL_PACKAGES",
            magic != null,
        )

        // 记录旧逻辑是否漏掉（回归说明，不强制失败以免无该 App 的设备误报）
        if (launcherOnly.none { it.packageName == CLONE_MASTER_PACKAGE }) {
            android.util.Log.w(
                TAG,
                "listLaunchableAppsOnly 未包含 $CLONE_MASTER_PACKAGE（MATCH_DEFAULT_ONLY 已知问题）",
            )
        }

        assertTrue(
            "全部应用数(${all.size})应不少于桌面应用数(${launcherOnly.size})",
            all.size >= launcherOnly.size,
        )
    }

    private companion object {
        private const val TAG = "InstalledAppCatalogTest"
        private const val CLONE_MASTER_PACKAGE = "com.qihoo.magic"
    }
}
