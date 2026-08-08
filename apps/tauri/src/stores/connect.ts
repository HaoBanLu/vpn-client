import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { message } from '@/lib/ui/message'
import { storeToRefs } from 'pinia'
import { clientApi, type ConnectDashboardData, type NodeItem } from '@/api/client'
import { useAccountStore } from '@/stores/account'
import {
  connectVpn,
  disconnectVpn,
  getVpnPlatformInfo,
  getVpnStats,
  getVpnStatus,
  healVpn,
  prepareVpn,
  probeVpn,
  reconnectVpn,
  watchVpnStats,
  watchVpnStatus,
} from '@/lib/vpn/bridge'
import { probeConnectivity, probeHint, probeResultToStatus, normalizeProbeResult } from '@/lib/vpn/probe'
import { probeExitIp } from '@/lib/vpn/exit-ip-probe'
import { isAcquirableNodeName } from '@/lib/vpn/line-acquire-node'
import { shouldNavigateToNodes, shouldNavigateToPackages } from '@/lib/vpn/connect-navigation'
import type { VpnConnectionState, VpnPlatformInfo, VpnProbeStatus, VpnSessionStats } from '@/lib/vpn/types'
import { sanitizeVpnUserMessage } from '@/lib/vpn/user-message'
import { isAppConnectable, unsupportedReason } from '@/lib/vpn/app-protocol-support'
import {
  loadSavedRouteMode,
  saveRouteMode,
  type AppRouteMode,
  type ClientProfile,
} from '@/lib/vpn/app-route-mode'
import {
  CONNECTION_SCENARIO,
  connectionScenarioLabel,
  normalizeConnectionScenario,
  resolveConnectionConfig,
  type ConnectionScenarioValue,
} from '@/lib/vpn/connection-scenario'
import { injectDirectBypassRules } from '@/lib/vpn/direct-bypass-rule'
import { waitForVpnReady } from '@/lib/vpn/wait-for-vpn-ready'
import { recordProbeFailure, recordProbeSuccess } from '@/lib/vpn/node-failover'
import { savePrivacyBaselineIp } from '@/lib/vpn/privacy-leak-probe'
import {
  AUTO_RECONNECT_POLICY,
  autoReconnectBackoffMs,
} from '@/lib/vpn/auto-reconnect-policy'
import {
  decideDesktopNetworkRestore,
  DESKTOP_NETWORK_RESTORE,
  shouldProceedDesktopAutoReconnect,
} from '@/lib/vpn/network-restore-policy'
import {
  effectiveConnectionMode,
  effectiveKillSwitchEnabled,
  hadVpnSession,
  loadDesktopSettings,
  markVpnSession,
} from '@/lib/vpn/desktop-settings'
import { updateTrayTooltip } from '@/lib/desktop/tray'
import { appendDebugLog } from '@/lib/debug/app-debug-log'
import { shouldIgnoreDisconnectedWhileConnecting } from '@/lib/vpn/connect-inflight'

const REGION_KEY = 'tauri_region'
const NODE_KEY = 'tauri_node'

function sanitizeStoredNode(): string | null {
  const raw = localStorage.getItem(NODE_KEY)
  if (!raw) return null
  if (!isAcquirableNodeName(raw)) {
    localStorage.removeItem(NODE_KEY)
    return null
  }
  return raw
}

loadSavedRouteMode()

export const useConnectStore = defineStore('connect', () => {
  const account = useAccountStore()
  const { subscription, usage } = storeToRefs(account)
  const loading = ref(false)
  const regions = ref<import('@/api/client').RegionItem[]>([])
  const selectedRegion = ref<string | null>(localStorage.getItem(REGION_KEY))
  const selectedNode = ref<string | null>(sanitizeStoredNode())
  const routeMode = ref<AppRouteMode>(loadSavedRouteMode())
  const connectionScenario = ref<ConnectionScenarioValue>(CONNECTION_SCENARIO.AUTO)
  const connectionScenarioLabelText = ref('自动')
  const activeProfile = ref<ClientProfile>('overseas_weak')
  const connectionState = ref<VpnConnectionState>('disconnected')
  const probeStatus = ref<VpnProbeStatus>('idle')
  const probeLatencyMs = ref<number | null>(null)
  const actionHint = ref<string | null>(null)
  /** 对齐 Android connectPending：点连接/选节点后立刻为 true，先于 connectionState=connecting */
  const connectPending = ref(false)
  const error = ref<string | null>(null)
  const platformInfo = ref<VpnPlatformInfo | null>(null)
  const stats = ref({
    uploadBytes: 0,
    downloadBytes: 0,
    durationMs: 0,
    uploadBps: 0,
    downloadBps: 0,
  })

  function normalizeSessionStats(payload: VpnSessionStats) {
    return {
      uploadBytes: payload.uploadBytes,
      downloadBytes: payload.downloadBytes,
      durationMs: payload.durationMs,
      uploadBps: payload.uploadBps ?? 0,
      downloadBps: payload.downloadBps ?? 0,
    }
  }
  const requestNavigateToNodes = ref(false)
  const requestNavigateToPackages = ref(false)

  const isConnected = computed(() => connectionState.value === 'connected')
  const isConnecting = computed(
    () => connectionState.value === 'connecting' || connectPending.value,
  )
  const isSwitching = ref(false)
  const userInitiatedDisconnect = ref(false)
  const autoReconnectAttempts = ref(0)
  const dashboard = ref<ConnectDashboardData | null>(null)
  const exitIp = ref<string | null>(null)
  const exitCountry = ref<string | null>(null)
  const exitCity = ref<string | null>(null)
  let autoReconnectInProgress = false
  let networkRestoreInProgress = false
  let networkRestoreDebounceTimer: ReturnType<typeof setTimeout> | null = null
  let healthProbeTimer: ReturnType<typeof setInterval> | null = null
  /** 连接世代号：中断/新连接时递增，丢弃过期的 in-flight connect。 */
  let connectGeneration = 0
  let onOnlineHandler: (() => void) | null = null
  let onOfflineHandler: (() => void) | null = null

  const MAX_AUTO_RECONNECT = AUTO_RECONNECT_POLICY.maxAttempts

  function bumpConnectGeneration(): number {
    connectGeneration += 1
    return connectGeneration
  }

  function isConnectGenerationCurrent(token: number): boolean {
    return token === connectGeneration
  }

  function syncTrayTooltip() {
    const node = selectedNode.value ?? '智能选路'
    if (connectionState.value === 'connected') {
      const label =
        probeStatus.value === 'ok'
          ? '已保护'
          : probeStatus.value === 'degraded'
            ? '网络异常'
            : probeStatus.value === 'probing'
              ? '验证中'
              : '已连接'
      void updateTrayTooltip(`跨云 · ${label} · ${node}`)
      return
    }
    if (connectionState.value === 'connecting') {
      void updateTrayTooltip('跨云 · 连接中…')
      return
    }
    void updateTrayTooltip('跨云 · 未连接')
  }

  function resetSessionStats() {
    stats.value = {
      uploadBytes: 0,
      downloadBytes: 0,
      durationMs: 0,
      uploadBps: 0,
      downloadBps: 0,
    }
  }

  async function syncVpnStats() {
    if (connectionState.value !== 'connected') return
    if (!platformInfo.value?.vpnSupported) return
    try {
      stats.value = normalizeSessionStats(await getVpnStats())
    } catch {
      // 统计失败不阻断连接
    }
  }

  function startHealthProbeLoop() {
    stopHealthProbeLoop()
    const tick = () => {
      if (connectionState.value === 'connected' && probeStatus.value !== 'probing') {
        void startProbe()
      }
    }
    const interval =
      probeStatus.value === 'degraded'
        ? AUTO_RECONNECT_POLICY.degradedHealthProbeMs
        : AUTO_RECONNECT_POLICY.periodicHealthProbeMs
    healthProbeTimer = setInterval(tick, interval)
  }

  function stopHealthProbeLoop() {
    if (healthProbeTimer) {
      clearInterval(healthProbeTimer)
      healthProbeTimer = null
    }
  }
  function shouldAutoReconnect(): boolean {
    return connectionState.value === 'connected' || connectionState.value === 'connecting'
  }

  /** 指定节点时不再传 region，避免地区筛选与节点不一致导致配置生成失败 */
  function getConfigParams(): { region: string | null; node: string | null } {
    if (selectedNode.value) {
      return { region: null, node: selectedNode.value }
    }
    return { region: selectedRegion.value, node: null }
  }

  function setVpnError(value: string | null) {
    error.value = value ? sanitizeVpnUserMessage(value) : null
  }

  async function initVpnBridge() {
    try {
      platformInfo.value = await getVpnPlatformInfo()
      const status = await getVpnStatus()
      connectionState.value = status.state
      setVpnError(status.error ?? null)
    } catch {
      platformInfo.value = {
        platform: 'web',
        vpnSupported: false,
        implementation: 'none',
        notes: '浏览器开发模式，VPN 命令不可用',
      }
    }
  }

  async function refresh() {
    loading.value = true
    error.value = null
    try {
      await account.refreshAccount()
      const [regionsRes, dashRes, prefRes] = await Promise.all([
        clientApi.getRegions(),
        clientApi.getConnectDashboard(selectedNode.value),
        clientApi.getUserPreferences().catch(() => null),
      ])
      regions.value = regionsRes.data.regions
      dashboard.value = dashRes.data
      if (prefRes?.data) {
        const scenario = normalizeConnectionScenario(prefRes.data.connection_scenario)
        connectionScenario.value = scenario
        connectionScenarioLabelText.value =
          prefRes.data.connection_scenario_label ?? connectionScenarioLabel(scenario)
        applyResolvedConnectionConfig()
      }
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '加载失败'
    } finally {
      loading.value = false
    }
  }

  function applyResolvedConnectionConfig(nodeAccessMode?: string | null) {
    const resolved = resolveConnectionConfig(
      connectionScenario.value,
      selectedRegion.value,
      nodeAccessMode,
    )
    activeProfile.value = resolved.profile
    routeMode.value = resolved.routeMode
    saveRouteMode(resolved.routeMode)
  }

  async function updateConnectionScenario(scenario: ConnectionScenarioValue) {
    try {
      const pref = (
        await clientApi.updateUserPreferences({ connection_scenario: scenario })
      ).data
      const normalized = normalizeConnectionScenario(pref.connection_scenario ?? scenario)
      connectionScenario.value = normalized
      connectionScenarioLabelText.value =
        pref.connection_scenario_label ?? connectionScenarioLabel(normalized)
      applyResolvedConnectionConfig()
      message.success('使用场景已更新')
      if (shouldAutoReconnect()) {
        await reconnect('正在应用使用场景…')
      }
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '更新失败')
    }
  }

  async function updateIpBindingMode(mode: string) {
    try {
      const pref = (await clientApi.updateUserPreferences({ ip_binding_mode: mode })).data
      if (dashboard.value) {
        dashboard.value.ip_binding_mode = pref.ip_binding_mode
        dashboard.value.ip_binding_mode_label = pref.ip_binding_mode_label
      }
      message.success('IP 模式已更新')
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '更新失败')
    }
  }

  function saveRegion(region: string | null) {
    selectedRegion.value = region
    if (region) localStorage.setItem(REGION_KEY, region)
    else localStorage.removeItem(REGION_KEY)
    applyResolvedConnectionConfig()
  }

  function setRouteMode(mode: AppRouteMode) {
    routeMode.value = mode
    saveRouteMode(mode)
  }

  function heartbeatPayload() {
    return {
      vpn_connected: isConnected.value,
      probe_status: isConnected.value && probeStatus.value !== 'idle' && probeStatus.value !== 'probing'
        ? probeStatus.value
        : undefined,
      connected_node: isConnected.value ? (selectedNode.value ?? '智能选路') : undefined,
      probe_latency_ms: isConnected.value ? probeLatencyMs.value ?? undefined : undefined,
      exit_ip: isConnected.value ? exitIp.value ?? undefined : undefined,
      exit_country: isConnected.value ? exitCountry.value ?? undefined : undefined,
      exit_city: isConnected.value ? exitCity.value ?? undefined : undefined,
    }
  }

  async function validateSelectedNode(nodes?: NodeItem[]) {
    const nodeName = selectedNode.value
    if (!nodeName) return
    const list = nodes ?? (await clientApi.getNodes()).data.nodes
    const node = list.find((item) => item.name === nodeName)
    if (node && !isAppConnectable(node)) {
      saveNode(null)
      throw new Error(unsupportedReason(node) ?? '所选节点不支持 App 连接')
    }
  }

  function saveNode(node: string | null) {
    selectedNode.value = node
    if (node) localStorage.setItem(NODE_KEY, node)
    else localStorage.removeItem(NODE_KEY)
  }

  async function syncSavedNodeWithNodes(nodes: NodeItem[]) {
    const nodeName = selectedNode.value
    if (!nodeName) return
    const node = nodes.find((item) => item.name === nodeName)
    if (node && !isAppConnectable(node)) {
      saveNode(null)
    }
  }

  let probeToken = 0

  async function runConnectivityProbe() {
    const platform = platformInfo.value?.platform
    if (platform === 'android' || platform === 'windows' || platform === 'macos' || platform === 'linux') {
      try {
        const native = await probeVpn()
        return normalizeProbeResult(native)
      } catch {
        // 原生端优先走平台探测，失败时再退回 WebView fetch
      }
    }
    return probeConnectivity()
  }

  async function startProbe() {
    if (probeStatus.value === 'probing') return
    const token = ++probeToken
    probeStatus.value = 'probing'
    // 与 Android TUN_SETTLE_MS 对齐，避免隧道刚建立时误判
    await new Promise((resolve) => setTimeout(resolve, 500))
    if (token !== probeToken || connectionState.value !== 'connected') return
    const result = await runConnectivityProbe()
    if (token !== probeToken || connectionState.value !== 'connected') return
    probeLatencyMs.value = result.latencyMs ?? null
    probeStatus.value = probeResultToStatus(result, true)
    const probeFailed = probeStatus.value === 'failed' || probeStatus.value === 'degraded'

    // 全端对齐 Clash Verge：探针只做软诊断（出口 IP / 心跳），不拆隧道、不自动切节点、不刷失败文案
    if (probeFailed) {
      recordProbeFailure()
      appendDebugLog('probe', '连接后质量探测未通过，保持隧道', 'warn')
      if (connectionState.value === 'connected') {
        actionHint.value = null
        error.value = null
      }
      syncTrayTooltip()
      return
    }

    recordProbeSuccess()
    syncTrayTooltip()

    const info = await probeExitIp()
    if (info && connectionState.value === 'connected' && token === probeToken) {
      exitIp.value = info.ip
      exitCountry.value = info.country ?? null
      exitCity.value = info.city ?? null
      if (dashboard.value) {
        dashboard.value = {
          ...dashboard.value,
          exit_ip: info.ip,
          exit_country: info.country,
          exit_city: info.city,
          probe_latency_ms: probeLatencyMs.value ?? dashboard.value.probe_latency_ms,
        }
      }
      const nodeName = selectedNode.value ?? undefined
      if (nodeName && isAcquirableNodeName(nodeName)) {
        try {
          dashboard.value = (await clientApi.getConnectDashboard(selectedNode.value)).data
        } catch {
          // dashboard 刷新失败不阻断已建立的 VPN
        }
      }
    }
  }

  function cancelProbe() {
    probeToken += 1
    probeStatus.value = 'idle'
    probeLatencyMs.value = null
    exitIp.value = null
    exitCountry.value = null
    exitCity.value = null
  }

  async function syncStatusAndProbe() {
    try {
      const status = await getVpnStatus()
      applyExternalVpnStatus(status)
    } catch (e) {
      if (platformInfo.value?.vpnSupported) {
        throw e
      }
    }
  }

  /**
   * 应用后端 VPN 状态。连接进行中时忽略仍为 disconnected 的回报，
   * 否则会冲掉 connectPending / connecting，出现「底部正在连接、按钮仍一键连接」。
   */
  function applyExternalVpnStatus(status: { state: VpnConnectionState; error?: string | null }) {
    const prev = connectionState.value
    if (
      shouldIgnoreDisconnectedWhileConnecting({
        connectPending: connectPending.value,
        connectionState: prev,
        isSwitching: isSwitching.value,
        userInitiatedDisconnect: userInitiatedDisconnect.value,
        nextState: status.state,
      })
    ) {
      return
    }
    connectionState.value = status.state
    setVpnError(status.error ?? null)
    handleConnectionTransition(prev, status.state)
  }

  function handleConnectionTransition(prev: VpnConnectionState, next: VpnConnectionState) {
    syncTrayTooltip()
    if (next === 'connected' && prev !== 'connected') {
      connectPending.value = false
      autoReconnectAttempts.value = 0
      userInitiatedDisconnect.value = false
      markVpnSession(true)
      resetSessionStats()
      startHealthProbeLoop()
      void syncVpnStats()
      void startProbe()
      return
    }
    if (next === 'disconnected' || next === 'failed') {
      // 在途连接中的 disconnected 已在 applyExternalVpnStatus 过滤；此处仅处理真实结束
      connectPending.value = false
      cancelProbe()
      stopHealthProbeLoop()
      resetSessionStats()
      if (next === 'disconnected') markVpnSession(false)
      if (next === 'failed') actionHint.value = null
    }
    if (
      loadDesktopSettings().autoReconnect &&
      prev === 'connected' &&
      next === 'failed' &&
      !userInitiatedDisconnect.value &&
      !isSwitching.value
    ) {
      appendDebugLog('connect', '隧道意外中断，触发自动重连', 'warn')
      void handleUnexpectedTunnelStop()
    }
  }

  /**
   * 断网再连 / 网卡恢复（对齐 Android 3.15.7）：
   * 自动重连开 → 防抖后直接完整重连；关且仍已连接 → 仅重刷系统代理。
   */
  async function recoverAfterNetworkOnline() {
    if (isSwitching.value) return
    const action = decideDesktopNetworkRestore({
      connectionState: connectionState.value,
      userInitiatedDisconnect: userInitiatedDisconnect.value,
      autoReconnectEnabled: loadDesktopSettings().autoReconnect,
    })
    if (action === 'none') return

    if (action === 'schedule_reconnect') {
      if (!shouldProceedDesktopAutoReconnect(typeof navigator !== 'undefined' ? navigator.onLine : true)) {
        actionHint.value = '网络已断开，恢复后将自动重连'
        return
      }
      appendDebugLog('network', '网络恢复，准备完整重连', 'warn')
      actionHint.value = '网络已恢复，正在自动重连…'
      void handleUnexpectedTunnelStop()
      return
    }

    // heal：仅关闭自动重连时的轻量兜底
    if (networkRestoreInProgress) return
    networkRestoreInProgress = true
    try {
      appendDebugLog('network', '网络恢复，重刷系统代理（自动重连已关）', 'info')
      try {
        await healVpn()
      } catch (e: unknown) {
        appendDebugLog(
          'network',
          `重刷系统代理失败: ${e instanceof Error ? e.message : String(e)}`,
          'warn',
        )
      }
      await new Promise((resolve) =>
        setTimeout(resolve, DESKTOP_NETWORK_RESTORE.settleAfterHealMs),
      )
      syncTrayTooltip()
    } finally {
      networkRestoreInProgress = false
    }
  }

  function onBrowserOnline() {
    if (networkRestoreDebounceTimer) {
      clearTimeout(networkRestoreDebounceTimer)
      networkRestoreDebounceTimer = null
    }
    networkRestoreDebounceTimer = setTimeout(() => {
      networkRestoreDebounceTimer = null
      void recoverAfterNetworkOnline()
    }, DESKTOP_NETWORK_RESTORE.reconnectDebounceMs)
  }

  function onBrowserOffline() {
    if (networkRestoreDebounceTimer) {
      clearTimeout(networkRestoreDebounceTimer)
      networkRestoreDebounceTimer = null
    }
    if (connectionState.value === 'connected' && !userInitiatedDisconnect.value) {
      actionHint.value = '网络已断开，恢复后将自动重连'
      appendDebugLog('network', '物理网断开，保持会话等待恢复', 'info')
    }
  }

  async function handleUnexpectedTunnelStop() {
    if (
      !loadDesktopSettings().autoReconnect ||
      autoReconnectInProgress ||
      userInitiatedDisconnect.value ||
      isSwitching.value ||
      !subscription.value
    ) {
      return
    }
    if (!shouldProceedDesktopAutoReconnect(typeof navigator !== 'undefined' ? navigator.onLine : true)) {
      actionHint.value = '网络已断开，恢复后将自动重连'
      appendDebugLog('network', '无物理网，暂缓自动重连', 'info')
      return
    }
    if (autoReconnectAttempts.value >= MAX_AUTO_RECONNECT) {
      actionHint.value = '连接已中断，请手动重试'
      return
    }
    autoReconnectInProgress = true
    autoReconnectAttempts.value += 1
    const attempt = autoReconnectAttempts.value
    const token = bumpConnectGeneration()
    actionHint.value = `连接中断，正在自动重连（${attempt}/${MAX_AUTO_RECONNECT}）…`
    connectionState.value = 'connecting'
    error.value = null
    resetSessionStats()
    syncTrayTooltip()
    await new Promise((resolve) => setTimeout(resolve, autoReconnectBackoffMs(attempt - 1)))
    if (!isConnectGenerationCurrent(token)) {
      autoReconnectInProgress = false
      return
    }
    try {
      await performConnect(undefined, token)
      if (!isConnectGenerationCurrent(token)) return
      autoReconnectAttempts.value = 0
    } catch (e: unknown) {
      if (!isConnectGenerationCurrent(token)) return
      connectionState.value = 'failed'
      setVpnError(e instanceof Error ? e.message : '自动重连失败')
      if (attempt < MAX_AUTO_RECONNECT) {
        void handleUnexpectedTunnelStop()
      } else {
        actionHint.value = '自动重连失败，请手动重试'
      }
    } finally {
      autoReconnectInProgress = false
    }
  }

  async function resolveConnectConfig() {
    let accessMode: string | null = null
    if (selectedNode.value) {
      const nodes = (await clientApi.getNodes()).data.nodes
      accessMode = nodes.find((n) => n.name === selectedNode.value)?.access_mode ?? null
    }
    return resolveConnectionConfig(connectionScenario.value, selectedRegion.value, accessMode)
  }

  async function performConnect(hint?: string | null, generation?: number) {
    const token = generation ?? connectGeneration
    if (platformInfo.value && !platformInfo.value.vpnSupported) {
      throw new Error(platformInfo.value.notes || '当前平台暂不支持 VPN 连接')
    }
    const params = getConfigParams()
    const resolved = await resolveConnectConfig()
    if (!isConnectGenerationCurrent(token)) return
    activeProfile.value = resolved.profile
    routeMode.value = resolved.routeMode
    saveRouteMode(resolved.routeMode)
    const configResp = (
      await clientApi.getClientConfig(
        params.region,
        params.node,
        resolved.routeMode,
        resolved.profile,
      )
    ).data
    if (!isConnectGenerationCurrent(token)) return
    const patchedConfig = injectDirectBypassRules(configResp.config)
    const prepared = await prepareVpn()
    if (!isConnectGenerationCurrent(token)) return
    if (!prepared) {
      throw new Error('未获得 VPN 授权，请允许系统弹窗后重试')
    }
    await validateSelectedNode()
    if (!isConnectGenerationCurrent(token)) return
    const mode = effectiveConnectionMode()
    appendDebugLog('connect', `开始连接 · ${selectedNode.value ?? '智能选路'} · ${mode}`, 'info')
    await connectVpn({
      configJson: patchedConfig,
      nodeName: selectedNode.value ?? '智能选路',
      connectionMode: mode,
    })
    if (!isConnectGenerationCurrent(token)) return
    // Android 原生 connect 异步返回 CONNECTING，需轮询到 connected/failed，避免秒报「VPN 未就绪」
    const ready = await waitForVpnReady({
      getStatus: getVpnStatus,
      isCurrent: () => isConnectGenerationCurrent(token),
    })
    if (!isConnectGenerationCurrent(token) || ready.kind === 'cancelled') return
    if (ready.kind === 'failed') {
      throw new Error(ready.error)
    }
    if (ready.kind === 'timeout') {
      throw new Error('VPN 启动超时，请重试')
    }
    await syncStatusAndProbe()
    if (!isConnectGenerationCurrent(token)) return
    if (connectionState.value !== 'connected') {
      const status = await getVpnStatus()
      if (!isConnectGenerationCurrent(token)) return
      if (status.state !== 'connected') {
        throw new Error(status.error ?? 'VPN 未就绪，请重试')
      }
      const prev = connectionState.value
      connectionState.value = status.state
      setVpnError(status.error ?? null)
      handleConnectionTransition(prev, status.state)
    }
    appendDebugLog('connect', '连接成功', 'info')
    try {
      dashboard.value = (await clientApi.getConnectDashboard(selectedNode.value)).data
    } catch {
      // 占线/出口信息刷新失败不阻断连接
    }
    if (!isConnectGenerationCurrent(token)) return
    if (hint) actionHint.value = hint
  }

  /** 对齐 Android disconnect()：连接中再点 Hero 取消在途连接并回到未连接。 */
  async function interruptInFlightConnect() {
    if (connectionState.value !== 'connecting' && !isSwitching.value && !connectPending.value) {
      return
    }
    bumpConnectGeneration()
    userInitiatedDisconnect.value = true
    autoReconnectAttempts.value = 0
    cancelProbe()
    stopHealthProbeLoop()
    isSwitching.value = false
    connectPending.value = false
    markVpnSession(false)
    appendDebugLog('connect', '用户中断连接中的隧道', 'info')
    try {
      await disconnectVpn({ userInitiated: true })
    } catch {
      // ignore
    }
    connectionState.value = 'disconnected'
    actionHint.value = null
    error.value = null
    resetSessionStats()
    syncTrayTooltip()
  }

  /**
   * 选节点后、跳转连接页之前调用：立刻进入「连接中」UI，避免底部 hint 与 Hero「一键连接」打架。
   */
  function beginConnectPending(nodeName: string) {
    const name = nodeName.trim()
    if (!name) return
    saveNode(name)
    connectPending.value = true
    error.value = null
    actionHint.value = `正在连接 ${name}…`
  }

  function clearConnectPending() {
    connectPending.value = false
  }

  /** @returns need_node / need_package 时由 MainShell 跳转，不进入失败态 */
  async function connect(): Promise<'need_node' | 'need_package' | 'done'> {
    if (shouldNavigateToPackages(!!subscription.value)) {
      connectPending.value = false
      error.value = null
      requestNavigateToPackages.value = true
      return 'need_package'
    }
    if (shouldNavigateToNodes(selectedNode.value)) {
      connectPending.value = false
      error.value = null
      if (connectionState.value === 'failed') {
        connectionState.value = 'disconnected'
      }
      requestNavigateToNodes.value = true
      return 'need_node'
    }
    if (platformInfo.value && !platformInfo.value.vpnSupported) {
      connectPending.value = false
      error.value = platformInfo.value.notes || '当前平台暂不支持 VPN 连接'
      return 'done'
    }

    // 立刻进入连接中（对齐 Android connectPending），再做基线探测等耗时步骤
    const needBaseline =
      connectionState.value === 'disconnected' ||
      connectionState.value === 'failed' ||
      connectPending.value
    const token = bumpConnectGeneration()
    error.value = null
    cancelProbe()
    userInitiatedDisconnect.value = false
    connectPending.value = true
    connectionState.value = 'connecting'
    actionHint.value = selectedNode.value?.trim()
      ? `正在连接 ${selectedNode.value.trim()}…`
      : '正在建立 VPN 隧道…'
    resetSessionStats()
    isSwitching.value = false
    syncTrayTooltip()

    if (needBaseline) {
      const baseline = await probeExitIp().catch(() => null)
      if (!isConnectGenerationCurrent(token)) return 'done'
      if (baseline?.ip) savePrivacyBaselineIp(baseline.ip)
    }

    try {
      await performConnect(undefined, token)
      if (!isConnectGenerationCurrent(token)) return 'done'
      // performConnect 会更新 connectionState；用 computed 避免赋值后的字面量收窄误报
      if (isConnected.value) {
        connectPending.value = false
        actionHint.value = probeHint(probeStatus.value) ?? '已建立 VPN 隧道'
      }
    } catch (e: unknown) {
      if (!isConnectGenerationCurrent(token)) return 'done'
      connectPending.value = false
      connectionState.value = 'failed'
      const msg = e instanceof Error ? e.message : '连接失败'
      setVpnError(msg)
      appendDebugLog('connect', `连接失败：${msg}`, 'error')
      actionHint.value = null
      cancelProbe()
      message.error(msg)
    }
    return 'done'
  }

  async function reconnect(switchingHint?: string) {
    const token = bumpConnectGeneration()
    cancelProbe()
    isSwitching.value = true
    connectPending.value = true
    actionHint.value = switchingHint ?? '正在切换节点…'
    connectionState.value = 'connecting'
    error.value = null
    resetSessionStats()
    syncTrayTooltip()
    try {
      await validateSelectedNode()
      if (!isConnectGenerationCurrent(token)) return
      const params = getConfigParams()
      const resolved = await resolveConnectConfig()
      if (!isConnectGenerationCurrent(token)) return
      activeProfile.value = resolved.profile
      routeMode.value = resolved.routeMode
      saveRouteMode(resolved.routeMode)
      const config = (
        await clientApi.getClientConfig(
          params.region,
          params.node,
          resolved.routeMode,
          resolved.profile,
        )
      ).data
      if (!isConnectGenerationCurrent(token)) return
      const patchedConfig = injectDirectBypassRules(config.config)
      await reconnectVpn({
        configJson: patchedConfig,
        nodeName: selectedNode.value ?? '智能选路',
        connectionMode: effectiveConnectionMode(),
      })
      if (!isConnectGenerationCurrent(token)) return
      const ready = await waitForVpnReady({
        getStatus: getVpnStatus,
        isCurrent: () => isConnectGenerationCurrent(token),
      })
      if (!isConnectGenerationCurrent(token) || ready.kind === 'cancelled') return
      if (ready.kind === 'failed') {
        throw new Error(ready.error)
      }
      if (ready.kind === 'timeout') {
        throw new Error('切换节点超时，请重试')
      }
      await syncStatusAndProbe()
      connectPending.value = false
    } catch (e: unknown) {
      if (!isConnectGenerationCurrent(token)) return
      connectPending.value = false
      connectionState.value = 'failed'
      const msg = e instanceof Error ? e.message : '切换失败'
      setVpnError(msg)
      actionHint.value = null
      message.error(msg)
    } finally {
      if (isConnectGenerationCurrent(token)) {
        isSwitching.value = false
      }
    }
  }

  async function applyNodeSelection(node: NodeItem, options?: { connectAfterSelect?: boolean }) {
    if (!isAppConnectable(node)) {
      const reason = unsupportedReason(node) ?? '所选节点不支持 App 连接'
      error.value = reason
      message.error(reason)
      return false
    }
    // 仅中断真正在途的隧道连接；connectPending 单独不算（选节点跳转前的乐观 UI）
    const wasConnecting = connectionState.value === 'connecting' || isSwitching.value
    if (wasConnecting) {
      await interruptInFlightConnect()
    }
    const wasConnected = connectionState.value === 'connected'
    saveNode(node.name)
    saveRegion(node.region)
    applyResolvedConnectionConfig(node.access_mode)
    error.value = null

    if (wasConnected) {
      await reconnect(`正在切换到 ${node.name}…`)
      if (connectionState.value === 'connected') {
        message.success(`已切换到 ${node.name}`)
      }
      return connectionState.value === 'connected'
    }

    const willConnect = Boolean(options?.connectAfterSelect || wasConnecting)
    if (willConnect) {
      // 无套餐也走 connect()，由 need_package 引导购买；勿静默「只选中」
      if (!subscription.value) {
        clearConnectPending()
        actionHint.value = `已选择 ${node.name}，购买套餐后可连接`
        message.success(`已选择 ${node.name}`)
      } else {
        beginConnectPending(node.name)
        message.success(`正在连接 ${node.name}`)
      }
      const result = await connect()
      return result === 'done' && isConnected.value
    }

    clearConnectPending()
    actionHint.value = subscription.value
      ? `已选择 ${node.name}`
      : `已选择 ${node.name}，购买套餐后可连接`
    message.success(`已选择 ${node.name}`)
    return true
  }

  async function clearNodeSelection() {
    if (connectionState.value === 'connecting') {
      await interruptInFlightConnect()
    }
    saveNode(null)
    error.value = null
    if (connectionState.value === 'connected') {
      await reconnect('正在切回智能选路…')
      if (connectionState.value === 'connected') {
        message.success('已切回智能选路')
      }
      return
    }
    actionHint.value = '已切回智能选路，下次连接将自动选路'
    message.info('已切换为智能选路')
  }

  async function disconnect() {
    bumpConnectGeneration()
    userInitiatedDisconnect.value = true
    autoReconnectAttempts.value = 0
    cancelProbe()
    stopHealthProbeLoop()
    isSwitching.value = false
    connectPending.value = false
    markVpnSession(false)
    syncTrayTooltip()
    appendDebugLog('connect', '用户手动断开连接', 'info')
    try {
      await disconnectVpn({ userInitiated: true })
    } catch {
      connectionState.value = 'disconnected'
    }
    actionHint.value = null
    error.value = null
    resetSessionStats()
  }

  async function forceDisconnectForAuth(reason: 'subscription_expired' | 'session_revoked') {
    if (
      connectionState.value !== 'connected' &&
      connectionState.value !== 'connecting' &&
      !connectPending.value
    ) {
      return
    }
    userInitiatedDisconnect.value = false
    autoReconnectAttempts.value = 0
    cancelProbe()
    stopHealthProbeLoop()
    connectPending.value = false
    markVpnSession(false)
    syncTrayTooltip()
    appendDebugLog('connect', `鉴权断开：${reason}`, 'warn')
    try {
      await disconnectVpn({ userInitiated: false, killSwitchEnabled: effectiveKillSwitchEnabled() })
    } catch {
      connectionState.value = 'disconnected'
    }
    actionHint.value = reason === 'subscription_expired' ? '套餐已到期，VPN 已断开' : '会话已失效，VPN 已断开'
    error.value = null
  }

  watch(subscription, (next, prev) => {
    if (prev && !next && (connectionState.value === 'connected' || connectionState.value === 'connecting')) {
      void forceDisconnectForAuth('subscription_expired')
    }
  })

  let unlistenStatus: (() => void) | null = null
  let unlistenStats: (() => void) | null = null

  let pollTimer: ReturnType<typeof setInterval> | null = null

  async function startWatchers() {
    try {
      unlistenStatus = await watchVpnStatus((status) => {
        applyExternalVpnStatus(status)
      })
      unlistenStats = await watchVpnStats((payload) => {
        stats.value = normalizeSessionStats(payload)
      })
      pollTimer = setInterval(() => {
        void syncStatusAndProbe()
        void syncVpnStats()
      }, 1000)
    } catch {
      // web dev
    }
    if (typeof window !== 'undefined') {
      onOnlineHandler = onBrowserOnline
      onOfflineHandler = onBrowserOffline
      window.addEventListener('online', onOnlineHandler)
      window.addEventListener('offline', onOfflineHandler)
    }
  }

  function stopWatchers() {
    unlistenStatus?.()
    unlistenStats?.()
    stopHealthProbeLoop()
    if (networkRestoreDebounceTimer) {
      clearTimeout(networkRestoreDebounceTimer)
      networkRestoreDebounceTimer = null
    }
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    if (typeof window !== 'undefined') {
      if (onOnlineHandler) window.removeEventListener('online', onOnlineHandler)
      if (onOfflineHandler) window.removeEventListener('offline', onOfflineHandler)
      onOnlineHandler = null
      onOfflineHandler = null
    }
  }

  async function restoreSessionIfNeeded() {
    const settings = loadDesktopSettings()
    if (!settings.restoreSession || !hadVpnSession() || !subscription.value) return
    if (connectionState.value === 'connected') return
    actionHint.value = '正在恢复上次连接…'
    appendDebugLog('connect', '启动时恢复上次连接', 'info')
    await connect()
  }

  function consumeNavigateToNodesRequest() {
    requestNavigateToNodes.value = false
  }

  function consumeNavigateToPackagesRequest() {
    requestNavigateToPackages.value = false
  }

  /** 清掉「未选节点」导致的失败态，避免连接页残留红字提示 */
  function clearNodeRequiredFailure() {
    if (!error.value?.includes('请先选择要连接的节点')) return
    error.value = null
    actionHint.value = null
    if (connectionState.value === 'failed' && !isAcquirableNodeName(selectedNode.value)) {
      connectionState.value = 'disconnected'
    }
  }

  return {
    loading,
    subscription,
    usage,
    regions,
    selectedRegion,
    selectedNode,
    routeMode,
    connectionScenario,
    connectionScenarioLabel: connectionScenarioLabelText,
    activeProfile,
    connectionState,
    probeStatus,
    probeLatencyMs,
    actionHint,
    connectPending,
    error,
    platformInfo,
    stats,
    dashboard,
    requestNavigateToNodes,
    requestNavigateToPackages,
    isConnected,
    isConnecting,
    isSwitching,
    initVpnBridge,
    refresh,
    saveRegion,
    setRouteMode,
    saveNode,
    heartbeatPayload,
    syncSavedNodeWithNodes,
    connect,
    reconnect,
    beginConnectPending,
    clearConnectPending,
    interruptInFlightConnect,
    applyNodeSelection,
    clearNodeSelection,
    disconnect,
    forceDisconnectForAuth,
    updateConnectionScenario,
    updateIpBindingMode,
    startWatchers,
    stopWatchers,
    restoreSessionIfNeeded,
    syncTrayTooltip,
    clearNodeRequiredFailure,
    consumeNavigateToNodesRequest,
    consumeNavigateToPackagesRequest,
  }
})
