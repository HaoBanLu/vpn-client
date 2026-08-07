package com.vpn.kuayun.vpn

import java.io.File

/** 将 /client/config 返回的 Clash YAML 写入 Mihomo 可读路径。 */
object ClashConfigSanitizer {
    fun prepareConfigFile(configYaml: String, configDir: File): File {
        configDir.mkdirs()
        val configFile = File(configDir, "config.yaml")
        configFile.writeText(configYaml.trim())
        return configFile
    }
}
