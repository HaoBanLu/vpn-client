<template>
  <KyTabPage
    title="连接"
    page-class="connect-page"
    stack-gap="sm"
    :desktop-larger="false"
    :on-refresh="onRefresh"
    :loading="accountView === 'loading'"
    :spin-overlay="false"
    :refresh-disabled="store.isConnecting || store.isSwitching"
  >
    <template v-if="accountView === 'error'">
      <KyCard>
        <p class="empty-main">网络异常</p>
        <p class="empty-sub">{{ account.loadError || '请检查网络后重试' }}</p>
      </KyCard>
      <ConnectHero :copy="errorHeroCopy" @click="onRefresh" />
      <KyButton block class="relogin-btn" @click="relogin">重新登录</KyButton>
    </template>

    <template v-else-if="accountView === 'empty'">
      <KyCard>
        <p class="empty-main">暂无有效套餐</p>
        <p class="empty-sub">购买套餐后即可使用跨云加速服务</p>
      </KyCard>
      <ConnectHero :copy="noSubHeroCopy" @click="goPackages" />
    </template>

    <template v-else-if="accountView === 'ready'">
      <div v-if="renewalHint" class="renewal-hint">{{ renewalHint }}</div>

      <ConnectHero :copy="heroCopy" @click="onToggle" />

      <ConnectSessionCard
        v-if="store.isConnected"
        :download-bytes="store.stats.downloadBytes"
        :upload-bytes="store.stats.uploadBytes"
        :duration-ms="store.stats.durationMs"
        :download-bps="store.stats.downloadBps"
        :upload-bps="store.stats.uploadBps"
        :remaining-gb="store.usage?.remaining ?? null"
        :expires-at="store.subscription?.expires_at"
        :selected-node="store.selectedNode"
        @switch-node="goNodes"
      />

      <ConnectQuickStatus
        v-else
        :selected-node="store.selectedNode"
        :remaining-gb="store.usage?.remaining ?? null"
        :expires-at="store.subscription?.expires_at"
        :connecting="store.isConnecting || store.connectPending"
        @pick-node="goNodes"
      />

      <!-- 连接过程文案只走 Hero 按钮区，底部不重复「正在连接…」 -->
      <p v-if="displayHint" class="action-hint">{{ displayHint }}</p>
    </template>

    <template #after>
      <!-- 对齐 Android：失败区仅错误文案 + 重试连接 -->
      <template v-if="showConnectError">
        <KyCard flat class="error-card">
          <p class="error-text">{{ store.error || '网络异常，请检查节点或稍后重试' }}</p>
        </KyCard>
        <KyButton
          v-if="store.subscription && store.connectionState === 'failed'"
          type="primary"
          block
          size="large"
          @click="startConnect"
        >
          重试连接
        </KyButton>
      </template>
    </template>
  </KyTabPage>
</template>

<script setup lang="ts">
defineOptions({ name: 'ConnectView' })
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import KyTabPage from '@/components/KyTabPage.vue'
import KyCard from '@/components/KyCard.vue'
import ConnectHero from '@/components/ConnectHero.vue'
import ConnectQuickStatus from '@/components/ConnectQuickStatus.vue'
import ConnectSessionCard from '@/components/ConnectSessionCard.vue'
import { KyButton } from '@/components/ky'
import { resolveConnectHeroCopy } from '@/lib/connect-hero'
import { resolveAccountViewState } from '@/lib/account-view-state'
import { getEntryLatencyMs } from '@/lib/vpn/entry-latency-cache'
import { useConnectStore } from '@/stores/connect'
import { useAccountStore } from '@/stores/account'
import { useAuthStore } from '@/stores/auth'
import { probeHint } from '@/lib/vpn/probe'
import { buildRenewalHint } from '@/lib/subscription'

const router = useRouter()
const store = useConnectStore()
const account = useAccountStore()
const auth = useAuthStore()

const NODE_REQUIRED_HINT = '请先选择要连接的节点'

const accountView = computed(() =>
  resolveAccountViewState({
    loading: account.loading || store.loading,
    fetched: account.fetched,
    loadError: account.loadError,
    hasSubscription: !!account.subscription,
  }),
)

const renewalHint = computed(() =>
  store.subscription ? buildRenewalHint(store.subscription.expires_at) : null,
)

/** 仅展示真正的连接失败；拉取失败走三态，不挡「已保护」 */
const showConnectError = computed(() => {
  if (accountView.value !== 'ready') return false
  if (!store.error) return false
  return !store.error.includes(NODE_REQUIRED_HINT)
})

const entryLatencyMs = computed(() => getEntryLatencyMs(store.selectedNodeId))

const heroCopy = computed(() =>
  resolveConnectHeroCopy({
    connectionState: store.connectionState,
    connectPending: store.connectPending || (store.isConnecting && !store.isConnected),
    isSwitching: store.isSwitching,
    selectedNode: store.selectedNode,
    tunnelLatencyMs: store.probeLatencyMs,
    entryLatencyMs: entryLatencyMs.value,
    connectPhase: store.connectPhase,
  }),
)

const noSubHeroCopy = computed(() =>
  resolveConnectHeroCopy({
    connectionState: 'disconnected',
    emptyReason: 'no_subscription',
  }),
)

const errorHeroCopy = computed(() =>
  resolveConnectHeroCopy({
    connectionState: 'disconnected',
    emptyReason: 'load_error',
  }),
)

const displayHint = computed(() => {
  if (store.isConnected || store.isConnecting || store.connectPending || store.isSwitching) {
    return null
  }
  const hint = store.actionHint || probeHint(store.probeStatus)
  if (!hint) return null
  if (hint.includes(NODE_REQUIRED_HINT)) return null
  // 兜底：连接类文案一律不走底部
  if (/正在连接|正在建立|正在切换|正在恢复|连接中/.test(hint)) return null
  return hint
})

function goPackages() {
  router.push({ name: 'Packages' })
}

function goNodes() {
  router.push({ name: 'Nodes' })
}

async function onRefresh() {
  await store.refresh()
}

async function relogin() {
  await auth.logout({ silent: true })
  await router.replace({ name: 'Login' })
}

async function startConnect() {
  await store.connect()
}

async function onToggle() {
  // 对齐 Android ConnectScreen：connected || connecting → disconnect/interrupt；否则 connect
  if (store.isConnected) {
    await store.disconnect()
    return
  }
  if (store.isConnecting || store.isSwitching || store.connectPending) {
    await store.interruptInFlightConnect()
    return
  }
  await startConnect()
}

onMounted(async () => {
  store.clearNodeRequiredFailure()
  if (accountView.value === 'loading') {
    await store.refresh()
  }
})
</script>

<style scoped>
/* 对齐 Android：顶对齐可滚动，不做垂直居中 */
:deep(.connect-page) {
  gap: 8px;
  justify-content: flex-start;
}

.renewal-hint {
  padding: var(--ky-space-md);
  border-radius: var(--ky-radius-md);
  background: var(--ky-danger-bg);
  border: 1px solid rgba(248, 113, 113, 0.25);
  color: var(--ky-danger);
  font-size: var(--ky-font-sm);
}

.action-hint {
  margin: 0;
  text-align: center;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.empty-main {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.empty-sub {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.relogin-btn {
  margin-top: 4px;
}

.error-card {
  margin-bottom: 8px;
}

.error-text {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-danger);
}
</style>
