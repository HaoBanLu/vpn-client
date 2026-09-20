<template>
  <KyTabPage
    title="节点选择"
    page-class="nodes-page"
    stack-gap="xs"
    :on-refresh="onRefresh"
    :loading="loading && nodes.length === 0 && !loadError"
  >
    <div v-if="loadError" class="nodes-error">
      <KyAlert type="error" :message="loadError" />
      <KyButton type="primary" block @click="load">重试</KyButton>
    </div>

    <div
      v-else
      class="nodes-book"
      :class="{ 'nodes-book--empty': !loading && connectableNodes.length === 0 && unsupportedNodes.length === 0 }"
    >
      <KyEmpty
        v-if="!loading && connectableNodes.length === 0 && unsupportedNodes.length === 0"
        description="暂无在线节点"
      >
        <KyButton type="primary" @click="load">重新加载</KyButton>
      </KyEmpty>

      <template v-else>
        <div class="nodes-book__list">
          <section
            v-for="sec in nodeSections"
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
                :filter-region="sec.key"
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

          <template v-if="unsupportedSections.length > 0">
            <section
              v-for="sec in unsupportedSections"
              :key="`u-${sec.key}`"
              class="nodes-book__section nodes-book__section--muted"
            >
              <header class="nodes-book__head">{{ sec.title }} · 需官方客户端</header>
              <template v-for="(item, index) in sec.nodes" :key="`u-${item.id}`">
                <div v-if="index > 0" class="nodes-book__divider" aria-hidden="true" />
                <KyNodeCard
                  :node="item"
                  grouped
                  :filter-region="sec.key"
                  variant="unsupported"
                  :unsupported-text="unsupportedReason(item)"
                />
              </template>
            </section>
          </template>
        </div>

        <nav v-if="indexItems.length > 1" class="nodes-book__index" aria-label="地区索引">
          <button
            v-for="item in indexItems"
            :key="item.key"
            type="button"
            class="nodes-book__index-item"
            :class="{ active: activeIndexKey === item.key }"
            :aria-label="item.title"
            @click="jumpToSection(item.key)"
          >
            {{ item.glyph }}
          </button>
        </nav>
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
import { mapApiError } from '@/lib/api-error'
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
  regionIndexGlyph,
  sortNodesByLatency,
} from '@/lib/vpn/node-list-display'
import { getEntryLatencyMs, saveEntryLatenciesByNodeId } from '@/lib/vpn/entry-latency-cache'
import { useConnectStore } from '@/stores/connect'
import { useAccountStore } from '@/stores/account'

const router = useRouter()
const connect = useConnectStore()
const account = useAccountStore()
const loading = ref(false)
const loadError = ref<string | null>(null)
const nodes = ref<NodeItem[]>([])
const latencyPending = ref(false)
const latencyMap = reactive<Record<number, number>>({})
const activeIndexKey = ref<string | null>(null)
let latencyRunId = 0

const connectableNodes = computed(() => nodes.value.filter((node) => isAppConnectable(node)))
const unsupportedNodes = computed(() => nodes.value.filter((node) => !isAppConnectable(node)))
const fastestNodeId = computed(() => findFastestNodeId(connectableNodes.value, latencyMap))

const nodeSections = computed(() =>
  groupNodesByRegionOrder(connectableNodes.value, connect.regions).map((sec) => ({
    ...sec,
    nodes: sortNodesByLatency(sec.nodes, latencyMap),
  })),
)

const unsupportedSections = computed(() =>
  groupNodesByRegionOrder(unsupportedNodes.value, connect.regions),
)

const indexItems = computed(() =>
  nodeSections.value.map((sec) => ({
    key: sec.key,
    title: sec.title,
    glyph: regionIndexGlyph(sec.title),
  })),
)

function sectionDomId(key: string) {
  return `nodes-sec-${key}`
}

function jumpToSection(key: string) {
  activeIndexKey.value = key
  connect.saveRegion(key)
  void nextTick(() => {
    const el = document.getElementById(sectionDomId(key))
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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

async function load() {
  loading.value = true
  loadError.value = null
  try {
    connect.invalidateConnectNodesCache()
    if (connect.regions.length === 0 && !account.fetched) {
      await connect.refresh()
    }
    nodes.value = await connect.fetchConnectNodesWithRecovery(true)
    await connect.syncSavedNodeWithNodes(nodes.value)
    hydrateLatencyFromCache(nodes.value)
    activeIndexKey.value = connect.selectedRegion
    void autoProbeLatency()
  } catch (error) {
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

/* 通讯录式：通栏白底铺满剩余视口，右侧索引 */
.nodes-book {
  position: relative;
  display: flex;
  align-items: stretch;
  flex: 1;
  min-height: calc(100dvh - 152px);
  margin: 0;
  background: var(--ky-bg-card);
  border-radius: 0;
  border: 0;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: none;
  overflow: visible;
}

.nodes-book--empty {
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

.nodes-book__list {
  flex: 1;
  min-width: 0;
  padding-bottom: 48px;
}

.nodes-book__head {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 7px 14px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--ky-text-muted);
  background: #f1f5f9;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.nodes-book__divider {
  height: 0;
  margin: 0 14px;
  border-top: 1px solid rgba(226, 232, 240, 0.85);
}

.nodes-book__section--muted {
  opacity: 0.9;
}

.nodes-book__index {
  position: sticky;
  top: 72px;
  align-self: flex-start;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 10px 6px 10px 2px;
  margin-right: 2px;
}

.nodes-book__index-item {
  appearance: none;
  border: 0;
  background: transparent;
  min-width: 22px;
  min-height: 22px;
  padding: 0;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  color: var(--ky-accent);
  cursor: pointer;
  line-height: 1;
}

.nodes-book__index-item.active {
  background: var(--ky-nav-active-pill);
  color: var(--ky-on-primary-container);
}
</style>
