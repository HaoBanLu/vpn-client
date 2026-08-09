<template>
  <KyTabPage
    title="节点选择"
    subtitle="点「连接」上网；需要延迟时再批量测速"
    page-class="nodes-page"
    :on-refresh="load"
    :loading="loading && nodes.length === 0"
  >
    <KyAlert v-if="loadError" type="error" :message="loadError" />

    <div class="nodes-toolbar">
      <KyChipGroup :model-value="region" :items="regionItems" @update:model-value="setRegion" />

      <KyButton
        type="primary"
        block
        class="nodes-batch-btn"
        :loading="batchTesting"
        :disabled="connectableNodes.length === 0"
        @click="batchTest"
      >
        {{ batchTesting ? '测速中…' : '批量测速' }}
      </KyButton>
    </div>

    <KyEmpty
      v-if="!loading && connectableNodes.length === 0 && unsupportedNodes.length === 0"
      description="当前地区暂无在线节点"
    />

    <div v-else-if="sortedConnectableNodes.length > 0" class="nodes-list-card">
      <template v-for="(item, index) in sortedConnectableNodes" :key="item.id">
        <div v-if="index > 0" class="nodes-list-divider" aria-hidden="true" />
        <KyNodeCard
          :node="item"
          :filter-region="region"
          :is-active="isNodeActive(item)"
          :selected="isNodeSelected(item)"
          :latency-ms="latencyMap[item.id]"
          :latency-pending="batchTesting && latencyMap[item.id] === undefined"
          :fastest="fastestNodeId === item.id"
          :action-label="connect.isConnected ? '切换' : '连接'"
          :action-loading="isNodeConnecting(item)"
          :action-disabled="connect.isSwitching"
          @action="selectNode(item)"
        />
      </template>
    </div>

    <template v-if="unsupportedNodes.length > 0">
      <p class="unsupported-title">以下节点需使用官方客户端，App 内不可选</p>
      <div class="nodes-list-card nodes-list-card--muted">
        <template v-for="(item, index) in unsupportedNodes" :key="`unsupported-${item.id}`">
          <div v-if="index > 0" class="nodes-list-divider" aria-hidden="true" />
          <KyNodeCard
            :node="item"
            :filter-region="region"
            variant="unsupported"
            :unsupported-text="unsupportedReason(item)"
          />
        </template>
      </div>
    </template>

    <div class="nodes-bottom-spacer" aria-hidden="true" />
  </KyTabPage>
</template>

<script setup lang="ts">
defineOptions({ name: 'NodesView' })
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyTabPage from '@/components/KyTabPage.vue'
import KyNodeCard from '@/components/KyNodeCard.vue'
import KyChipGroup from '@/components/KyChipGroup.vue'
import { KyAlert, KyButton, KyEmpty } from '@/components/ky'
import { mapApiError } from '@/lib/api-error'
import { clientApi, type NodeItem, type RegionItem } from '@/api/client'
import { regionDisplayLabel } from '@/lib/subscription'
import { isAppConnectable, unsupportedReason } from '@/lib/vpn/app-protocol-support'
import { shouldConnectAfterNodeSelect, shouldNavigateToConnectAfterNodeSelect } from '@/lib/vpn/connect-navigation'
import {
  mergeLatencyResults,
  parseLatencyEndpoint,
  probeTcpLatency,
} from '@/lib/vpn/client-latency-probe'
import { findFastestNodeId, sortNodesByLatency } from '@/lib/vpn/node-list-display'
import { saveEntryLatenciesByNodeName } from '@/lib/vpn/entry-latency-cache'
import { useConnectStore } from '@/stores/connect'

const router = useRouter()
const connect = useConnectStore()
const loading = ref(false)
const loadError = ref<string | null>(null)
const nodes = ref<NodeItem[]>([])
const region = ref<string | null>(connect.selectedRegion)
const batchTesting = ref(false)
const latencyMap = reactive<Record<number, number>>({})

const filteredNodes = computed(() => {
  if (!region.value) return nodes.value
  return nodes.value.filter((n) => n.region === region.value)
})

const connectableNodes = computed(() => filteredNodes.value.filter((node) => isAppConnectable(node)))
const unsupportedNodes = computed(() => filteredNodes.value.filter((node) => !isAppConnectable(node)))
const sortedConnectableNodes = computed(() => sortNodesByLatency(connectableNodes.value, latencyMap))
const fastestNodeId = computed(() => findFastestNodeId(connectableNodes.value, latencyMap))

const regionItems = computed(() => [
  { label: '全部', value: null },
  ...connect.regions.map((r: RegionItem) => ({
    label: regionDisplayLabel(r),
    value: r.code,
  })),
])

function isNodeActive(item: NodeItem) {
  return connect.isConnected && connect.selectedNode === item.name
}

function isNodeSelected(item: NodeItem) {
  if (isNodeActive(item)) return false
  return connect.selectedNode === item.name
}

function isNodeConnecting(item: NodeItem) {
  return (
    (connect.isConnecting || connect.isSwitching) &&
    connect.selectedNode === item.name
  )
}

function setRegion(value: string | null) {
  region.value = value
  connect.saveRegion(value)
}

async function load() {
  loading.value = true
  loadError.value = null
  try {
    if (connect.regions.length === 0 || !connect.subscription) {
      await connect.refresh()
    }
    nodes.value = (await clientApi.getNodes()).data.nodes
    await connect.syncSavedNodeWithNodes(nodes.value)
  } catch (error) {
    loadError.value = mapApiError(error, '节点加载失败')
  } finally {
    loading.value = false
  }
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
    connect.beginConnectPending(node.name)
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

async function batchTest() {
  const targets = connectableNodes.value
  const ids = targets.map((n) => n.id)
  if (ids.length === 0) return
  batchTesting.value = true
  try {
    const payload = (await clientApi.batchTestLatency(ids)).data
    const results = payload.results ?? {}
    const details = payload.details ?? {}
    const clientLatencies = await Promise.all(
      targets.map(async (node) => {
        const endpoint = parseLatencyEndpoint(node.latency_endpoint)
        if (!endpoint) return [node.id, null] as const
        const latency = await probeTcpLatency(endpoint.host, endpoint.port)
        return [node.id, latency] as const
      }),
    )
    const clientMap = Object.fromEntries(clientLatencies)
    Object.entries(results).forEach(([id, latency]) => {
      const nodeId = Number(id)
      const serverMs = details[id]?.entry_latency_ms ?? latency
      latencyMap[nodeId] = mergeLatencyResults(serverMs, clientMap[nodeId] ?? null)
    })
    saveEntryLatenciesByNodeName(
      targets.map((node) => ({
        name: node.name,
        latencyMs: latencyMap[node.id] ?? 0,
      })),
    )
    message.success(`已测试 ${Object.keys(results).length} 个节点`)
  } finally {
    batchTesting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* 对齐 Android：chip 下 10dp 间距、按钮高 40、M3 ExtraLarge 近胶囊 */
.nodes-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nodes-batch-btn {
  width: 100%;
  height: 40px !important;
  min-height: 40px !important;
  border-radius: 999px !important;
  border: 0 !important;
  background: var(--ky-accent) !important;
  color: #fff !important;
  font-weight: 700 !important;
  font-size: 15px !important;
  letter-spacing: 0.2px;
  box-shadow: none !important;
}

.nodes-batch-btn:not(:disabled):hover {
  background: var(--ky-accent-soft) !important;
  color: #fff !important;
}

.nodes-list-card {
  margin-top: 2px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  border: 0;
  box-shadow: none;
  overflow: hidden;
}

.nodes-list-card--muted {
  background: rgba(255, 255, 255, 0.72);
}

.nodes-list-divider {
  height: 0;
  margin: 2px 14px;
  border-top: 1px dashed rgba(197, 208, 224, 0.7);
}

.unsupported-title {
  margin: var(--ky-space-md) 0 var(--ky-space-sm);
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-text-muted);
}

.nodes-bottom-spacer {
  height: 40px;
  width: 100%;
  flex-shrink: 0;
  pointer-events: none;
}
</style>
