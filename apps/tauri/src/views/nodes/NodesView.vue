<template>
  <KyTabPage
    page-class="nodes-page"
    :on-refresh="onRefresh"
    :loading="loading && nodes.length === 0 && !loadError"
  >
    <template v-if="regionFilterItems.length > 1" #sticky>
      <nav class="nodes-region-nav" aria-label="地区筛选">
        <button
          v-for="item in regionFilterItems"
          :key="item.key ?? 'all'"
          type="button"
          class="nodes-region-nav__chip"
          :class="{ active: filterRegion === item.key }"
          @click="selectRegionFilter(item.key)"
        >
          {{ item.title }}
        </button>
      </nav>
    </template>

    <div v-if="loadError" class="nodes-error">
      <KyAlert type="error" :message="loadError" />
      <KyButton type="primary" block @click="load">重试</KyButton>
    </div>

    <div
      v-else
      class="nodes-book"
      :class="{ 'nodes-book--empty': showEmptyBook }"
    >
      <KyEmpty
        v-if="!loading && connectableNodes.length === 0 && unsupportedNodes.length === 0"
        description="暂无在线节点"
      >
        <KyButton type="primary" @click="load">重新加载</KyButton>
      </KyEmpty>

      <KyEmpty
        v-else-if="!loading && filteredEmpty"
        description="该地区暂无节点"
      >
        <KyButton type="primary" @click="selectRegionFilter(null)">查看全部</KyButton>
      </KyEmpty>

      <template v-else>
        <div class="nodes-book__list">
          <section
            v-for="sec in visibleNodeSections"
            :id="sectionDomId(sec.key)"
            :key="sec.key"
            class="nodes-book__section"
          >
            <header class="nodes-book__head">{{ sec.title }}</header>
            <template v-for="(item, index) in sec.nodes" :key="item.id">
              <div v-if="index > 0" class="nodes-book__divider" aria-hidden="true" />
              <KyNodeCard
                :node="item"
                grouped
                :filter-region="filterRegion ?? sec.key"
                :is-active="isNodeActive(item)"
                :selected="isNodeSelected(item)"
                :latency-ms="latencyMap[item.id]"
                :latency-pending="isLatencyPending(item.id)"
                :latency-failed="latencyFailed[item.id] === true"
                :fastest="fastestNodeId === item.id"
                :action-label="connect.isConnected ? '切换' : '连接'"
                :action-loading="isNodeConnecting(item)"
                :action-disabled="connect.isSwitching"
                @action="selectNode(item)"
                @retry-latency="retryNodeLatency(item)"
              />
            </template>
          </section>

          <template v-if="visibleUnsupportedSections.length > 0">
            <section
              v-for="sec in visibleUnsupportedSections"
              :key="`u-${sec.key}`"
              class="nodes-book__section nodes-book__section--muted"
            >
              <header class="nodes-book__head">{{ sec.title }} · 需官方客户端</header>
              <template v-for="(item, index) in sec.nodes" :key="`u-${item.id}`">
                <div v-if="index > 0" class="nodes-book__divider" aria-hidden="true" />
                <KyNodeCard
                  :node="item"
                  grouped
                  :filter-region="filterRegion ?? sec.key"
                  variant="unsupported"
                  :unsupported-text="unsupportedReason(item)"
                />
              </template>
            </section>
          </template>
        </div>
      </template>
    </div>
  </KyTabPage>
</template>

<script setup lang="ts">
defineOptions({ name: 'NodesView' })
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import KyTabPage from '@/components/KyTabPage.vue'
import KyNodeCard from '@/components/KyNodeCard.vue'
import { KyAlert, KyButton, KyEmpty } from '@/components/ky'
import { isNetworkConnectivityError, mapApiError } from '@/lib/api-error'
import { withTimeout } from '@/lib/account-view-state'
import { BOOTSTRAP_FETCH_TIMEOUT_MS } from '@/lib/boot-session'
import { clientApi, type NodeItem } from '@/api/client'
import { isAppConnectable, unsupportedReason } from '@/lib/vpn/app-protocol-support'
import { shouldConnectAfterNodeSelect, shouldNavigateToConnectAfterNodeSelect } from '@/lib/vpn/connect-navigation'
import {
  CLIENT_LATENCY_CONCURRENCY,
  displayLatencyMs,
  mapPool,
  mergeLatencyResults,
  parseLatencyEndpoint,
  probeTcpLatencyWithRetry,
} from '@/lib/vpn/client-latency-probe'
import {
  findFastestNodeId,
  groupNodesByRegionOrder,
  sortNodesByLatency,
} from '@/lib/vpn/node-list-display'
import { getEntryLatencyMs, saveEntryLatenciesByNodeId } from '@/lib/vpn/entry-latency-cache'
import { useConnectStore } from '@/stores/connect'
import { useAccountStore } from '@/stores/account'
/** null = 全部 */
const ALL_REGIONS = null as string | null

const router = useRouter()
const connect = useConnectStore()
const account = useAccountStore()
const loading = ref(false)
const loadError = ref<string | null>(null)
const nodes = ref<NodeItem[]>([])
const latencyPending = ref(false)
const latencyMap = reactive<Record<number, number>>({})
/** 测速失败（可点「超时」重试）；与「尚未测」区分开 */
const latencyFailed = reactive<Record<number, boolean>>({})
const latencyProbing = reactive<Record<number, boolean>>({})
/** 地区筛选：默认「全部」 */
const filterRegion = ref<string | null>(ALL_REGIONS)
let latencyRunId = 0

function isLatencyPending(nodeId: number) {
  if (latencyProbing[nodeId]) return true
  return latencyPending.value && latencyMap[nodeId] === undefined && !latencyFailed[nodeId]
}

const connectableNodes = computed(() => nodes.value.filter((node) => isAppConnectable(node)))
const unsupportedNodes = computed(() => nodes.value.filter((node) => !isAppConnectable(node)))

const allNodeSections = computed(() =>
  groupNodesByRegionOrder(connectableNodes.value, connect.regions).map((sec) => ({
    ...sec,
    nodes: sortNodesByLatency(sec.nodes, latencyMap),
  })),
)

const allUnsupportedSections = computed(() =>
  groupNodesByRegionOrder(unsupportedNodes.value, connect.regions),
)

const regionFilterItems = computed(() => [
  { key: ALL_REGIONS, title: '全部' },
  ...allNodeSections.value.map((sec) => ({ key: sec.key, title: sec.title })),
])

const visibleNodeSections = computed(() => {
  if (!filterRegion.value) return allNodeSections.value
  return allNodeSections.value.filter((sec) => sec.key === filterRegion.value)
})

const visibleUnsupportedSections = computed(() => {
  if (!filterRegion.value) return allUnsupportedSections.value
  return allUnsupportedSections.value.filter((sec) => sec.key === filterRegion.value)
})

const visibleConnectableNodes = computed(() =>
  visibleNodeSections.value.flatMap((sec) => sec.nodes),
)

const fastestNodeId = computed(() => findFastestNodeId(visibleConnectableNodes.value, latencyMap))

const filteredEmpty = computed(
  () =>
    !!filterRegion.value &&
    visibleNodeSections.value.length === 0 &&
    visibleUnsupportedSections.value.length === 0 &&
    (connectableNodes.value.length > 0 || unsupportedNodes.value.length > 0),
)

const showEmptyBook = computed(
  () =>
    (!loading.value && connectableNodes.value.length === 0 && unsupportedNodes.value.length === 0) ||
    filteredEmpty.value,
)

function sectionDomId(key: string) {
  return `nodes-sec-${key}`
}

function selectRegionFilter(key: string | null) {
  filterRegion.value = key
  connect.saveRegion(key)
  void nextTick(() => {
    const scroller = document.querySelector('.nodes-page .ky-pull-refresh') as HTMLElement | null
    scroller?.scrollTo?.({ top: 0, behavior: 'smooth' })
  })
}

function isNodeActive(item: NodeItem) {
  return connect.isConnected && connect.selectedNodeId === item.id
}

function isNodeSelected(item: NodeItem) {
  if (isNodeActive(item)) return false
  return connect.selectedNodeId === item.id
}

function isNodeConnecting(item: NodeItem) {
  return (
    (connect.isConnecting || connect.isSwitching) &&
    connect.selectedNodeId === item.id
  )
}

function hydrateLatencyFromCache(list: NodeItem[]) {
  for (const node of list) {
    if (displayLatencyMs(latencyMap[node.id]) != null) continue
    const cached = displayLatencyMs(getEntryLatencyMs(node.id))
    if (cached != null) {
      latencyMap[node.id] = cached
      delete latencyFailed[node.id]
    }
  }
}

function syncFilterWithAvailableRegions() {
  // 每次进入/刷新节点页默认「全部」；点 Chip 后再筛选并写入 selectedRegion
  filterRegion.value = ALL_REGIONS
}

async function load() {
  loading.value = true
  const hadNodes = nodes.value.length > 0
  try {
    if (connect.regions.length === 0 && !account.fetched) {
      await withTimeout(
        connect.refresh(),
        BOOTSTRAP_FETCH_TIMEOUT_MS + 8_000,
        '节点加载超时，请下拉刷新重试',
      )
    }
    // force 即可跳过 TTL；勿先 invalidate，否则已连 VPN 时 API 超时无法回退缓存
    nodes.value = await withTimeout(
      connect.fetchConnectNodesWithRecovery(true),
      BOOTSTRAP_FETCH_TIMEOUT_MS,
      '节点加载超时，请下拉刷新重试',
    )
    loadError.value = null
    await connect.syncSavedNodeWithNodes(nodes.value)
    hydrateLatencyFromCache(nodes.value)
    syncFilterWithAvailableRegions()
    void autoProbeLatency()
  } catch (error) {
    // 已有列表时软失败：保留节点，避免下拉刷新整页变成「连接超时」
    if (hadNodes && isNetworkConnectivityError(error)) {
      loadError.value = null
      return
    }
    loadError.value = mapApiError(error, '节点加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  await load()
}

async function selectNode(node: NodeItem) {
  const wasConnected = connect.isConnected
  if (!wasConnected && !connect.subscription) {
    try {
      await connect.refresh()
    } catch {
      /* connect() / need_package 再处理 */
    }
  }
  const willConnect = shouldConnectAfterNodeSelect(wasConnected)
  if (willConnect && connect.subscription) {
    connect.beginConnectPending(node)
  }
  if (shouldNavigateToConnectAfterNodeSelect()) {
    await router.push({ name: 'Connect' })
  }
  await connect.applyNodeSelection(node, {
    connectAfterSelect: willConnect,
  })
  if (!connect.subscription && !connect.isConnecting && !connect.isConnected) {
    connect.clearConnectPending()
    await router.push({ name: 'Packages' })
  }
}

async function persistLatencyCache(targets: NodeItem[]) {
  saveEntryLatenciesByNodeId(
    targets
      .map((node) => ({
        id: node.id,
        latencyMs: displayLatencyMs(latencyMap[node.id]) ?? 0,
      }))
      .filter((item) => item.latencyMs > 0),
  )
}

async function fillMissingFromServer(missing: NodeItem[]) {
  if (missing.length === 0) return
  try {
    const payload = (await clientApi.batchTestLatency(missing.map((node) => node.id))).data
    const results = payload.results ?? {}
    const details = payload.details ?? {}
    for (const node of missing) {
      const key = String(node.id)
      const serverMs = details[key]?.entry_latency_ms ?? results[key] ?? -1
      const merged = mergeLatencyResults(serverMs, latencyMap[node.id] ?? null)
      if (merged > 0) {
        latencyMap[node.id] = merged
        delete latencyFailed[node.id]
      } else if (displayLatencyMs(latencyMap[node.id]) == null) {
        delete latencyMap[node.id]
        latencyFailed[node.id] = true
      }
    }
  } catch {
    // 控制面补洞失败不影响已测出的本机结果
    for (const node of missing) {
      if (displayLatencyMs(latencyMap[node.id]) == null) latencyFailed[node.id] = true
    }
  }
}

async function probeOneNode(node: NodeItem): Promise<boolean> {
  const endpoint = parseLatencyEndpoint(node.latency_endpoint)
  if (!endpoint) {
    latencyFailed[node.id] = true
    return false
  }
  latencyProbing[node.id] = true
  delete latencyFailed[node.id]
  try {
    const latency = await probeTcpLatencyWithRetry(endpoint.host, endpoint.port)
    if (latency != null) {
      latencyMap[node.id] = latency
      delete latencyFailed[node.id]
      return true
    }
    latencyFailed[node.id] = true
    return false
  } finally {
    delete latencyProbing[node.id]
  }
}

async function retryNodeLatency(node: NodeItem) {
  delete latencyMap[node.id]
  delete latencyFailed[node.id]
  const ok = await probeOneNode(node)
  if (!ok) {
    await fillMissingFromServer([node])
  }
  if (displayLatencyMs(latencyMap[node.id]) != null) {
    persistLatencyCache([node])
  }
}

/** 进入页面自动测速，不再放「批量测速」按钮 */
async function autoProbeLatency() {
  const targets = [...connectableNodes.value]
  if (targets.length === 0) return
  const runId = ++latencyRunId
  latencyPending.value = true
  try {
    await mapPool(targets, CLIENT_LATENCY_CONCURRENCY, async (node) => {
      if (runId !== latencyRunId) return
      if (displayLatencyMs(latencyMap[node.id]) != null) return
      await probeOneNode(node)
    })
    if (runId !== latencyRunId) return
    const missing = targets.filter((node) => displayLatencyMs(latencyMap[node.id]) == null)
    await fillMissingFromServer(missing)
    if (runId !== latencyRunId) return
    persistLatencyCache(targets)
  } finally {
    if (runId === latencyRunId) latencyPending.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.nodes-error {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 页顶地区筛选：无品牌头，芯片条即顶栏；地区多时可横滑 */
.nodes-region-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  overflow-x: auto;
  padding: 0 0 6px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.nodes-region-nav::-webkit-scrollbar {
  display: none;
}

.nodes-region-nav__chip {
  flex-shrink: 0;
  appearance: none;
  border: 1px solid var(--ky-border);
  border-radius: var(--ky-radius-full);
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  background: var(--ky-bg-card);
  color: var(--ky-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.nodes-region-nav__chip.active {
  border-color: transparent;
  background: var(--ky-accent);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.28);
}

.nodes-region-nav__chip:active:not(.active) {
  background: var(--ky-nav-active-pill);
  color: var(--ky-on-primary-container);
}

/* 通栏紧凑列表：无大卡片圆角包裹，行内留白与分区标题对齐 */
.nodes-book {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: calc(100dvh - 210px);
  margin: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  overflow: visible;
}

.nodes-book--empty {
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 40px var(--ky-space-md);
  background: transparent;
  border: 0;
  border-radius: 0;
}

.nodes-book__list {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding-bottom: 56px;
}

.nodes-book__section {
  margin: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  overflow: visible;
}

.nodes-book__section + .nodes-book__section {
  margin-top: 6px;
}

.nodes-book__head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 10px 0 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ky-text-hint);
  background: color-mix(in srgb, var(--ky-bg) 92%, transparent);
  backdrop-filter: blur(8px);
  border: 0;
  border-bottom: 1px solid rgba(226, 232, 240, 0.75);
  box-shadow: none;
}

.nodes-book__head::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(226, 232, 240, 0.9);
}

.nodes-book__divider {
  height: 0;
  margin: 0;
  border-top: 1px solid rgba(226, 232, 240, 0.85);
}

.nodes-book__section--muted {
  opacity: 0.88;
}

.nodes-book__section--muted .nodes-book__head {
  color: var(--ky-text-hint);
  background: color-mix(in srgb, var(--ky-bg) 92%, transparent);
}
</style>
