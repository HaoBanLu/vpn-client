<template>
  <div class="app-shell" :class="{ 'app-shell--desktop': isDesktop }">
    <aside v-if="isDesktop" class="side-nav">
      <div class="side-nav__brand">
        <div class="side-nav__logo">
          <KuayunCloudIcon size="md" />
        </div>
        <div class="side-nav__brand-copy">
          <span class="side-nav__brand-name">跨云</span>
          <span class="side-nav__brand-version">v{{ APP_VERSION_NAME }}</span>
        </div>
      </div>
      <nav class="side-nav__menu">
        <button
          v-for="item in tabs"
          :key="item.name"
          type="button"
          class="side-nav__item"
          :class="{ active: isActive(item.name) }"
          @click="go(item.name)"
        >
          <component :is="item.icon" class="side-nav__icon" />
          <span>{{ item.label }}</span>
          <span v-if="item.name === 'Profile' && account.unreadNotificationCount > 0" class="nav-badge">
            {{ account.unreadNotificationCount > 99 ? '99+' : account.unreadNotificationCount }}
          </span>
        </button>
      </nav>
    </aside>

    <div class="app-main">
      <main class="app-content">
        <div class="app-route-view">
          <!-- keep-alive：Tab 切换保留页面，避免反复整页加载闪烁 -->
          <RouterView v-slot="{ Component, route: r }">
            <KeepAlive :include="tabKeepAliveNames">
              <component :is="Component" :key="r.name" />
            </KeepAlive>
          </RouterView>
        </div>
      </main>
    </div>

    <nav v-if="!isDesktop" class="bottom-nav">
      <button
        v-for="item in tabs"
        :key="item.name"
        type="button"
        :class="{ active: isActive(item.name) }"
        @click="go(item.name)"
      >
        <KyBadge
          v-if="item.name === 'Profile' && account.unreadNotificationCount > 0"
          :count="account.unreadNotificationCount"
        >
          <component :is="item.icon" class="tab-icon" />
        </KyBadge>
        <component v-else :is="item.icon" class="tab-icon" />
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CloudOutlined,
  HomeOutlined,
  ShoppingCartOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import KuayunCloudIcon from '@/components/KuayunCloudIcon.vue'
import { KyBadge } from '@/components/ky'
import { APP_VERSION_NAME } from '@/lib/app-meta'
import { clientApi } from '@/api/client'
import { useConnectStore } from '@/stores/connect'
import { useAccountStore } from '@/stores/account'
import { initDesktopTray, setTrayHideOnClose } from '@/lib/desktop/tray'
import { loadDesktopSettings } from '@/lib/vpn/desktop-settings'
import { shouldUseDesktopLayout } from '@/lib/layout'
import { isProfileRoute } from '@/lib/route-groups'
import { sessionHeartbeatIntervalMs } from '@/lib/session-auth'
import { useAppUpdate } from '@/lib/app-update/use-app-update'
import { message } from '@/lib/ui/message'

const route = useRoute()
const router = useRouter()
const connect = useConnectStore()
const account = useAccountStore()

const tabs: Array<{ name: string; label: string; icon: Component }> = [
  { name: 'Connect', label: '连接', icon: HomeOutlined },
  { name: 'Nodes', label: '节点', icon: CloudOutlined },
  { name: 'Packages', label: '套餐', icon: ShoppingCartOutlined },
  { name: 'Profile', label: '我的', icon: UserOutlined },
]

/** 与路由 name / 组件 name 对齐，供 KeepAlive include */
const tabKeepAliveNames = ['ConnectView', 'NodesView', 'PackagesView', 'ProfileView']

const isDesktop = ref(typeof window !== 'undefined' && shouldUseDesktopLayout(window.innerWidth))
const appUpdate = useAppUpdate()

function updateLayout() {
  isDesktop.value = shouldUseDesktopLayout(window.innerWidth)
}

let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let unlistenTray: (() => void) | null = null

function isDocumentVisible() {
  return typeof document === 'undefined' || document.visibilityState !== 'hidden'
}

function startHeartbeatTimer() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  heartbeatTimer = setInterval(sendHeartbeat, sessionHeartbeatIntervalMs(isDocumentVisible()))
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void sendHeartbeat()
    void appUpdate.reconcileAfterResume()
  }
  startHeartbeatTimer()
}

function isActive(name: string) {
  if (name === 'Profile') return isProfileRoute(route.name)
  return route.name === name
}

function go(name: string) {
  router.push({ name })
}

async function sendHeartbeat() {
  try {
    await clientApi.sendHeartbeat(connect.heartbeatPayload())
  } catch {
    // ignore heartbeat errors
  }
}

onMounted(async () => {
  updateLayout()
  window.addEventListener('resize', updateLayout)
  const desktopSettings = loadDesktopSettings()
  try {
    unlistenTray = await initDesktopTray({
      hideOnClose: desktopSettings.hideOnClose,
      syncHideOnClose: setTrayHideOnClose,
      onDisconnect: () => connect.disconnect(),
    })
  } catch {
    unlistenTray = null
  }
  await connect.initVpnBridge()
  await connect.recoverAfterAppUpdate()
  await connect.startWatchers()
  try {
    await connect.refresh()
  } catch {
    // 账户/连接数据加载失败不阻断主壳初始化
  }
  account.startNotificationPolling()
  connect.syncTrayTooltip()
  void connect.restoreSessionIfNeeded()
  void appUpdate.checkSilently()
  sendHeartbeat()
  startHeartbeatTimer()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateLayout)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  unlistenTray?.()
  connect.stopWatchers()
  account.stopNotificationPolling()
  if (heartbeatTimer) clearInterval(heartbeatTimer)
})

watch(
  () => connect.requestNavigateToNodes,
  (requested) => {
    if (!requested) return
    router.push({ name: 'Nodes' })
    message.info('请选择要连接的节点')
    connect.consumeNavigateToNodesRequest()
  },
)

watch(
  () => connect.requestNavigateToPackages,
  (requested) => {
    if (!requested) return
    router.push({ name: 'Packages' })
    message.info('请先购买或续费套餐')
    connect.consumeNavigateToPackagesRequest()
  },
)

watch(
  () => route.name,
  (name) => {
    if (isProfileRoute(name)) {
      account.clearUnreadNotifications()
    }
  },
  { immediate: true },
)
</script>

<style scoped>
.nav-badge {
  margin-left: auto;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--ky-radius-full);
  background: var(--ky-danger);
  color: white;
  font-size: 11px;
  line-height: 20px;
  text-align: center;
}

.side-nav__brand-copy {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.side-nav__logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--ky-accent-deep) 0%, var(--ky-accent-cyan) 100%);
  box-shadow: var(--ky-shadow-sm);
}

.side-nav__brand-name {
  font-size: var(--ky-font-lg);
  font-weight: 700;
  color: var(--ky-text);
}

.side-nav__brand-version {
  font-size: 11px;
  font-weight: 500;
  color: var(--ky-text-muted);
  letter-spacing: 0.02em;
}
</style>
