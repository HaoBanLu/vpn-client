package com.vpn.member.vpn

import com.vpn.member.debug.AppDebugLogger

/** 将用户规则直连注入 Mihomo config.yaml 的 rules 段（MATCH 前 → DIRECT）。 */
object ClashDirectBypassPatcher {
    fun inject(yaml: String, rules: List<DirectBypassRule>): String {
        val enabled = DirectBypassRuleStore.dedupeForClash(rules)
        if (enabled.isEmpty()) return yaml

        val clashLines = enabled.map { rule -> "  ${DirectBypassRuleStore.toClashLine(rule)}" }
        val yamlLines = yaml.lines().toMutableList()
        var rulesSectionStart = -1
        var lastMatchIndex = -1

        yamlLines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed == "rules:" || trimmed.startsWith("rules:")) {
                rulesSectionStart = index
            }
            if (rulesSectionStart >= 0 && trimmed.startsWith("- MATCH,")) {
                lastMatchIndex = index
            }
        }

        val result =
            when {
                rulesSectionStart < 0 -> {
                    yaml.trimEnd() + "\n\nrules:\n" + clashLines.joinToString("\n")
                }
                lastMatchIndex >= 0 -> {
                    yamlLines.addAll(lastMatchIndex, clashLines)
                    yamlLines.joinToString("\n")
                }
                else -> {
                    yamlLines.addAll(clashLines)
                    yamlLines.joinToString("\n")
                }
            }

        AppDebugLogger.info(
            category = "mihomo",
            message = "已注入规则直连",
            context = mapOf("count" to enabled.size.toString()),
        )
        return result
    }
}
