package com.vpn.member.vpn

/** 节点接入类型与连接场景的提示文案。 */
object NodeAccessHint {
    fun poolLabel(accessMode: String?): String? =
        when {
            accessMode.equals("relay", ignoreCase = true) -> "回国专线"
            accessMode.equals("direct", ignoreCase = true) -> "海外直连"
            else -> null
        }

    /** 场景与节点类型不匹配时返回提示，否则 null。 */
    fun scenarioMismatchHint(
        scenario: String?,
        accessMode: String?,
    ): String? {
        val normalized = ConnectionScenario.normalize(scenario)
        val isRelay = accessMode.equals("relay", ignoreCase = true)
        val isDirect = accessMode.equals("direct", ignoreCase = true)
        return when {
            normalized == ConnectionScenario.OVERSEAS && isRelay ->
                "芜湖/武汉等为「回国专线」，缅甸/海外访问外网请选新加坡、香港等「海外直连」节点。"
            normalized == ConnectionScenario.RETURN_HOME && isDirect ->
                "当前为「回国加速」，海外直连节点不适合访问国内站；建议选择武汉或贵州。"
            else -> null
        }
    }
}
