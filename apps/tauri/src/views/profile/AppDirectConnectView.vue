<template>
  <KyPage sub>
    <PageHeader title="应用直连" subtitle="指定应用不走 VPN，其余流量默认加速" />

    <KyAlert
      type="warning"
      message="开启应用直连后，所选应用将暴露真实 IP。请仅对确需直连的应用开启。"
      show-icon
    />

    <template v-if="!isAndroid">
      <KyCard flat class="info-card">
        <p class="info-title">分应用代理</p>
        <p class="info-body">
          分应用直连依赖系统应用列表，目前仅 Android 客户端支持。桌面端请使用「规则直连」按域名/IP
          绕过。
        </p>
      </KyCard>
      <KyCard flat class="empty-card">
        <KyEmpty description="桌面端请改用规则直连" />
        <KyButton block size="large" type="primary" @click="goBypass">去配置规则直连</KyButton>
      </KyCard>
    </template>

    <template v-else>
      <KyCard flat class="info-card">
        <p class="info-title">分应用代理</p>
        <p class="info-body">
          默认全部走 VPN；已选 {{ selectedCount }} 个应用直连。修改后需重连 VPN 才能完全生效。
        </p>
        <ul class="info-list">
          <li>未勾选的应用仍走 VPN 加速</li>
          <li>系统应用与本应用默认保持代理，避免误伤</li>
          <li>修改后需重连 VPN 才能完全生效</li>
        </ul>
        <KyButton
          v-if="needsPermission"
          class="perm-btn"
          block
          type="primary"
          @click="onRequestPermission"
        >
          授予权限并刷新列表
        </KyButton>
      </KyCard>

      <KyCard v-if="loading" flat class="empty-card">
        <KyEmpty description="正在加载应用列表…" />
      </KyCard>

      <KyCard v-else-if="loadError" flat class="empty-card">
        <KyEmpty :description="loadError" />
        <KyButton block type="primary" @click="loadApps">重试</KyButton>
      </KyCard>

      <template v-else>
        <KyCard flat class="search-card">
          <KyInput v-model="query" placeholder="搜索应用名或包名" />
        </KyCard>

        <KyCard v-if="filteredApps.length === 0" flat class="empty-card">
          <KyEmpty description="未找到匹配的应用" />
        </KyCard>

        <KyCard v-else flat class="list-card">
          <div v-for="app in filteredApps" :key="app.packageName" class="app-row">
            <div class="app-copy">
              <span class="app-label">{{ app.label }}</span>
              <span class="app-pkg">{{ app.packageName }}</span>
            </div>
            <KySwitch
              :checked="selectedSet.has(app.packageName)"
              @update:checked="(v) => onToggle(app.packageName, v)"
            />
          </div>
        </KyCard>
      </template>
    </template>
  </KyPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { KyAlert, KyButton, KyEmpty, KyInput, KySwitch } from '@/components/ky'
import { detectClientPlatform } from '@/lib/app-meta'
import {
  filterInstalledApps,
  listInstalledApps,
  requestInstalledAppsPermission,
  setDirectConnectPackages,
  type InstalledAppInfo,
} from '@/lib/vpn/app-direct-connect'
import { Modal } from '@/lib/ui/confirm'
import { message } from '@/lib/ui/message'
import { useConnectStore } from '@/stores/connect'

const router = useRouter()
const connect = useConnectStore()
const isAndroid = detectClientPlatform() === 'android'

const loading = ref(false)
const loadError = ref<string | null>(null)
const query = ref('')
const apps = ref<InstalledAppInfo[]>([])
const selected = ref<string[]>([])
const needsPermission = ref(false)

const selectedSet = computed(() => new Set(selected.value))
const selectedCount = computed(() => selected.value.length)
const filteredApps = computed(() => filterInstalledApps(apps.value, query.value))

function goBypass() {
  router.push({ name: 'DirectBypassRules' })
}

async function loadApps() {
  if (!isAndroid) return
  loading.value = true
  loadError.value = null
  try {
    const result = await listInstalledApps()
    apps.value = result.apps
    selected.value = [...result.selectedPackages]
    needsPermission.value = result.needsPermission
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载应用列表失败'
  } finally {
    loading.value = false
  }
}

async function onRequestPermission() {
  try {
    await requestInstalledAppsPermission()
    message.info('请在系统弹窗中允许后返回')
    // 给用户一点时间点授权
    window.setTimeout(() => {
      void loadApps()
    }, 800)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '无法请求权限')
  }
}

async function persist(next: string[], toast: string) {
  try {
    const result = await setDirectConnectPackages(next)
    selected.value = [...result.packages]
    if (connect.isConnected) {
      void connect.reconnect('正在应用直连设置…')
      message.success('已保存，正在重连以生效')
      return
    }
    message.success(toast)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
    await loadApps()
  }
}

function onToggle(packageName: string, enabled: boolean) {
  if (enabled) {
    Modal.confirm({
      title: '确认开启应用直连？',
      content: '该应用将不经过 VPN，会暴露真实 IP。仅建议对确需直连的应用开启。',
      okText: '确认开启',
      cancelText: '取消',
      onOk: () => {
        const next = Array.from(new Set([...selected.value, packageName]))
        return persist(next, '已保存应用直连')
      },
    })
    return
  }
  const next = selected.value.filter((p) => p !== packageName)
  void persist(next, '已关闭应用直连')
}

onMounted(() => {
  void loadApps()
})
</script>

<style scoped>
.info-card {
  margin-top: 12px;
}

.info-title {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 700;
  color: var(--ky-text);
}

.info-body {
  margin: 8px 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.5;
}

.info-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
  line-height: 1.6;
}

.perm-btn {
  margin-top: 12px;
}

.search-card {
  margin-top: 12px;
}

.list-card {
  margin-top: 12px;
  padding: 4px 0;
}

.app-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--ky-border, rgba(0, 0, 0, 0.06));
}

.app-row:last-child {
  border-bottom: 0;
}

.app-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.app-label {
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-pkg {
  font-size: var(--ky-font-xs, 12px);
  color: var(--ky-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-card {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
