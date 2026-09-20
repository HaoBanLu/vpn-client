<template>
  <KyTabPage
    title="节点选择"
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
                :latency-pending="latencyPending && latencyMap[item.id] === undefined"
                :fastest="fastestNodeId === item.id"
                :action-label="connect.isConnected ? '切换' : '连接'"
                :action-loading="isNodeConnecting(item)"
                :action-disabled="connect.isSwitching"
                @action="selectNode(item)"
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
import { clientApi, type NodeItem } from '@/api/client'
import { isAppConnectable, unsupportedReason } from '@/lib/vpn/app-protocol-support'
import { shouldConnectAfterNodeSelect, shouldNavigateToConnectAfterNodeSelect } from '@/lib/vpn/connect-navigation'
import {
  CLIENT_LATENCY_CONCURRENCY,
  mapPool,
  mergeLatencyResults,
  parseLatencyEndpoint,
  probeTcpLatency,
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
/** 地区筛选：默认「全部」 */
const filterRegion = ref<string | null>(ALL_REGIONS)
let latencyRunId = 0

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
    if (latencyMap[node.id] > 0) continue
    const cached = getEntryLatencyMs(node.id)
    if (cached != null && cached > 0) latencyMap[node.id] = cached
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
      await connect.refresh()
    }
    // force 即可跳过 TTL；勿先 invalidate，否则已连 VPN 时 API 超时无法回退缓存
    nodes.value = await connect.fetchConnectNodesWithRecovery(true)
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
    targets.map((node) => ({
      id: node.id,
      latencyMs: latencyMap[node.id] ?? 0,
    })),
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
      if (merged > 0) latencyMap[node.id] = merged
    }
  } catch {
    // 控制面补洞失败不影响已测出的本机结果
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
      if (latencyMap[node.id] > 0) return
      const endpoint = parseLatencyEndpoint(node.latency_endpoint)
      if (!endpoint) return
      const latency = await probeTcpLatency(endpoint.host, endpoint.port)
      if (runId !== latencyRunId) return
      if (latency != null && latency > 0) latencyMap[node.id] = latency
    })
    if (runId !== latencyRunId) return
    const missing = targets.filter((node) => !(latencyMap[node.id] > 0))
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

/* 标题下横向地区筛选：含「全部」；地区多时可横滑 */
.nodes-region-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  overflow-x: auto;
  padding: 2px 0 4px;
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
  padding: 7px 14px;
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

/* 与套餐等主 Tab 一致：卡片容器 + 页内留白 */
.nodes-book {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: calc(100dvh - 210px);
  margin: 0;
  background: var(--ky-bg-card);
  border-radius: var(--ky-radius-lg);
  border: 1px solid var(--ky-border);
  box-shadow: var(--ky-shadow-sm);
  overflow: hidden;
}

.nodes-book--empty {
  align-items: center;
  justify-content: center;
  padding: 32px var(--ky-space-md);
}

.nodes-book__list {
  flex: 1;
  min-width: 0;
  padding-bottom: 48px;
}

.nodes-book__section + .nodes-book__section {
  margin-top: 2px;
}

.nodes-book__head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 10px 14px 10px 12px;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.04em;
  color: var(--ky-on-primary-container);
  background: linear-gradient(180deg, #e8f0fe 0%, #eef4ff 100%);
  border-bottom: 1px solid rgba(59, 130, 246, 0.18);
  border-left: 3px solid var(--ky-accent);
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.03);
}

.nodes-book__divider {
  height: 0;
  margin: 0 14px;
  border-top: 1px solid rgba(226, 232, 240, 0.85);
}

.nodes-book__section--muted {
  opacity: 0.92;
}

.nodes-book__section--muted .nodes-book__head {
  color: var(--ky-text-secondary);
  background: var(--ky-surface-variant);
  border-left-color: var(--ky-border-strong);
}
</style>
