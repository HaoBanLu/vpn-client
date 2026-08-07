<template>
  <KyTabPage title="节点选择" page-class="nodes-page" :on-refresh="load" :loading="loading">
    <KyAlert v-if="loadError" type="error" :message="loadError" />

    <KySelectedBanner
      v-if="connect.selectedNode"
      label="当前节点"
      :value="connect.selectedNode"
      :clear-disabled="connect.isSwitching"
      @clear="clearNode"
    />

    <KyStack gap="md">
      <KyChipGroup :model-value="region" :items="regionItems" @update:model-value="setRegion" />

      <KyButton
        block
        class="ky-btn-block nodes-batch-btn"
        size="large"
        :loading="batchTesting"
        :disabled="connectableNodes.length === 0"
        @click="batchTest"
      >
        {{ batchTesting ? '测速中…' : '批量测速' }}
      </KyButton>
    </KyStack>

    <KyEmpty
      v-if="!loading && connectableNodes.length === 0 && unsupportedNodes.length === 0"
      description="当前地区暂无节点"
    />

    <KyGrid2 v-else class="nodes-grid">
      <KyNodeCard
        v-for="item in connectableNodes"
        :key="item.id"
        :node="item"
        :filter-region="region"
        :highlight="connect.selectedNode === item.name"
        :selected="connect.selectedNode === item.name"
        :selected-status-text="selectedStatusText"
        :latency-ms="latencyMap[item.id]"
        :action-label="connect.isConnected ? '切换' : '连接'"
        :action-loading="connect.isSwitching"
        :action-disabled="connect.isSwitching"
        @action="selectNode(item)"
      />

      <template v-if="unsupportedNodes.length > 0">
        <p class="unsupported-title">以下节点需使用官方客户端，App 内不可选</p>
        <KyNodeCard
          v-for="item in unsupportedNodes"
          :key="`unsupported-${item.id}`"
          :node="item"
          :filter-region="region"
          variant="unsupported"
          :unsupported-text="unsupportedReason(item)"
        />
      </template>
    </KyGrid2>
    <!-- 显式占位：避免 flex/滚动裁切导致末卡贴底 -->
    <div class="nodes-bottom-spacer" aria-hidden="true" />
  </KyTabPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyTabPage from '@/components/KyTabPage.vue'
import KyGrid2 from '@/components/KyGrid2.vue'
import KyStack from '@/components/KyStack.vue'
import KyNodeCard from '@/components/KyNodeCard.vue'
import KySelectedBanner from '@/components/KySelectedBanner.vue'
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

const regionItems = computed(() => [
  { label: '全部', value: null },
  ...connect.regions.map((r: RegionItem) => ({
    label: regionDisplayLabel(r),
    value: r.code,
  })),
])

const selectedStatusText = computed(() => {
  if (connect.isSwitching) return '正在切换到此节点…'
  if (connect.isConnecting) return '连接中，可点其他节点切换'
  if (connect.isConnected) return '✓ 当前使用中'
  return '✓ 已选中'
})

function setRegion(value: string | null) {
  region.value = value
  connect.saveRegion(value)
}

async function clearNode() {
  await connect.clearNodeSelection()
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
  // 对齐 Android：未连接时点「连接此节点」即连；套餐未就绪先 refresh，仍无则 connect() 返回 need_package
  if (!wasConnected && !connect.subscription) {
    try {
      await connect.refresh()
    } catch {
      /* connect() / need_package 再处理 */
    }
  }
  const willConnect = shouldConnectAfterNodeSelect(wasConnected)
  // 跳转前先进入「连接中」UI，避免连接页仍显示「一键连接」而底部已是「正在连接」
  if (willConnect && connect.subscription) {
    connect.beginConnectPending(node.name)
  }
  // 对齐 Android MainShell：先切到连接 Tab，再发起连接/切换
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
    message.success(`已测试 ${Object.keys(results).length} 个节点`)
  } finally {
    batchTesting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.nodes-batch-btn {
  width: 100%;
}

.unsupported-title {
  grid-column: 1 / -1;
  margin: var(--ky-space-md) 0 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-text-muted);
}

.nodes-grid {
  margin-bottom: 8px;
}

.nodes-bottom-spacer {
  height: 40px;
  width: 100%;
  flex-shrink: 0;
  pointer-events: none;
}
</style>
