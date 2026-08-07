package com.vpn.member.vpn

import android.content.Context
import com.vpn.member.data.local.AppPreferences
import com.vpn.member.vpn.mihomo.MihomoGeoAssetManager
import java.io.File

/** 自研 App 与 VpnService 之间共享的 Mihomo 配置目录（避免 Intent 传递大段 YAML 被截断）。 */
object ClashConfigStore {
    private const val DIR_NAME = "clash"
    private const val FILE_NAME = "config.yaml"

    fun directory(context: Context): File = File(context.filesDir, DIR_NAME)

    fun configFile(context: Context): File = File(directory(context), FILE_NAME)

    /** 校验并写入 config.yaml，供 VpnTunnelService 读取。 */
    fun persist(context: Context, configYaml: String) {
        MihomoGeoAssetManager.scheduleInstall(context)
        val directBypassRules = DirectBypassRuleStore.enabledRules(AppPreferences(context))
        ClashConfigSanitizer.prepareConfigDirectory(
            configYaml,
            directory(context),
            geoReady = MihomoGeoAssetManager.isGeoReady(context),
            rulesetsReady = MihomoGeoAssetManager.areRulesetsReady(context),
            directBypassRules = directBypassRules,
        )
    }

    fun readOrNull(context: Context): String? {
        val file = configFile(context)
        if (!file.isFile) return null
        return file.readText().takeIf { it.isNotBlank() }
    }

    /** 登出 / 鉴权失效时清除本地节点配置，避免无授权继续使用。 */
    fun wipe(context: Context) {
        val dir = directory(context)
        runCatching {
            dir.listFiles()?.forEach { child ->
                if (child.isFile) child.delete()
            }
        }
    }
}
