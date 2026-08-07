package com.vpn.member.vpn

/**
 * 连接失败原因（与占线、节点可达性、本机网络等分离，供 UI 与诊断日志统一引用）。
 *
 * [logCode] 写入诊断日志 `category=connect_fail` 的 `reason` 字段，便于后台检索。
 */
enum class ConnectFailureReason(val logCode: String) {
    /** 占线：线路被其他设备/会话占用 */
    LINE_HELD_CONFLICT("line_held_conflict"),
    /** 占线：账号线路配额已满 */
    LINE_QUOTA_FULL("line_quota_full"),
    /** 节点/代理入口不可达（Reality 握手超时、入口 EOF 等） */
    NODE_UNREACHABLE("node_unreachable"),
    /** 验证时 VPN 隧道未建立 */
    PROBE_NO_VPN("probe_no_vpn"),
    /** 验证时本机物理网络不可用（split 模式） */
    PROBE_NETWORK_OFFLINE("probe_network_offline"),
    /** TUN/Mihomo 启动失败 */
    VPN_TUNNEL_FAILED("vpn_tunnel_failed"),
    /** 订阅配置解析或字段异常 */
    CONFIG_INVALID("config_invalid"),
    /** 本机无网络 */
    NETWORK_OFFLINE("network_offline"),
    /** 套餐/流量 */
    SUBSCRIPTION("subscription"),
    UNKNOWN("unknown"),
}

fun ConnectFailureReason.isLineIssue(): Boolean =
    this == ConnectFailureReason.LINE_HELD_CONFLICT ||
        this == ConnectFailureReason.LINE_QUOTA_FULL

fun ConnectFailureReason.isNodeIssue(): Boolean =
    this == ConnectFailureReason.NODE_UNREACHABLE ||
        this == ConnectFailureReason.VPN_TUNNEL_FAILED ||
        this == ConnectFailureReason.PROBE_NO_VPN

/** 用户可见文案：一类问题只对应一种提示，不与占线混写。 */
fun ConnectFailureReason.userMessage(
    nodeName: String? = null,
    domesticReturn: Boolean = false,
): String {
    val nodeSuffix =
        nodeName?.trim()?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
    return when (this) {
        ConnectFailureReason.LINE_HELD_CONFLICT ->
            "该节点暂时不可用，请更换其他节点后重试"
        ConnectFailureReason.LINE_QUOTA_FULL ->
            "当前连接数已达上限，请稍后再试"
        ConnectFailureReason.NODE_UNREACHABLE ->
            if (domesticReturn) {
                "回国节点不可达$nodeSuffix，请更换芜湖/上海/杭州等节点或稍后重试"
            } else {
                "节点不可达：代理入口无响应$nodeSuffix。若在缅甸/海外，请改选新加坡、香港等「海外直连」节点"
            }
        ConnectFailureReason.PROBE_NO_VPN ->
            "连接验证失败：VPN 隧道未就绪，请重新点击连接"
        ConnectFailureReason.PROBE_NETWORK_OFFLINE ->
            "本机网络不可用，请检查 Wi-Fi 或移动数据后重试"
        ConnectFailureReason.VPN_TUNNEL_FAILED ->
            "VPN 隧道建立失败$nodeSuffix，请重试或更换节点"
        ConnectFailureReason.CONFIG_INVALID ->
            "节点配置异常，请刷新节点列表后重试"
        ConnectFailureReason.NETWORK_OFFLINE ->
            "网络不可用，请检查网络后重试"
        ConnectFailureReason.SUBSCRIPTION ->
            "套餐已失效，请续费后重试"
        ConnectFailureReason.UNKNOWN ->
            "连接失败，请稍后重试"
    }
}
