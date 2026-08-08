<template>
  <KyPage sub>
    <PageHeader :title="pageTitle" :subtitle="pageSubtitle" />

    <KyCard title="连接方式">
      <template v-if="isAndroid">
        <p class="mode-desc">
          当前使用 <strong>系统 VPN（TUN）</strong>，由跨云接管设备流量，对齐原生 Android 体验。
        </p>
        <p class="mode-desc muted">
          断网保护（Kill Switch）、开机自连、分应用直连等高级项将在后续版本开放。
        </p>
      </template>
      <template v-else>
        <p class="mode-desc">
          当前使用 <strong>系统代理</strong>（本地混合端口 + 系统 HTTP/HTTPS），无需管理员权限。
        </p>
        <p class="mode-desc muted">
          TUN 全隧道、Kill Switch、系统加固为 Android 能力，桌面端不提供。
        </p>
      </template>
    </KyCard>

    <KyCard title="连接设置">
      <div v-for="item in toggleItems" :key="item.key" class="setting-row">
        <div class="setting-copy">
          <p class="setting-title">{{ item.title }}</p>
          <p class="setting-desc">{{ item.desc }}</p>
        </div>
        <KySwitch
          :checked="settings[item.key]"
          @update:checked="(v) => setSetting(item.key, v)"
        />
      </div>
    </KyCard>

    <KyCard title="隐私自检">
      <p class="probe-desc">连接 VPN 后检测出口 IP 与 DNS 是否正常，帮助发现泄露风险。</p>
      <KyButton
        type="primary"
        block
        :loading="probeRunning"
        :disabled="!isConnected"
        @click="runPrivacyProbe"
      >
        {{
          probeRunning ? '正在检测…' : isConnected ? '立即隐私检测' : '请先连接 VPN 后再检测'
        }}
      </KyButton>
      <KyAlert
        v-if="probeMessage"
        :type="probePassed ? 'success' : 'warning'"
        :message="probeMessage"
        show-icon
        style="margin-top: var(--ky-space-md)"
      />
      <div v-if="latestProbe" class="probe-history">
        <div class="probe-history-head">
          <span class="probe-history-title">最近检测</span>
          <KyButton type="link" size="small" @click="clearHistory">清空</KyButton>
        </div>
        <ul class="probe-history-list">
          <li :key="`${latestProbe.atMillis}`">
            <span :class="['probe-dot', latestProbe.passed ? 'probe-dot--ok' : 'probe-dot--warn']" />
            <span class="probe-summary">{{ memberFacingProbeSummary(latestProbe) }}</span>
            <span class="probe-time">{{ formatProbeTime(latestProbe.atMillis) }}</span>
          </li>
        </ul>
      </div>
    </KyCard>

    <KyCard flat title="说明">
      <p class="hint">{{ footerHint }}</p>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted } from 'vue'
import dayjs from 'dayjs'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { KyAlert, KyButton, KySwitch } from '@/components/ky'
import {
  appendPrivacyProbeHistory,
  clearPrivacyProbeHistory,
  loadPrivacyProbeHistory,
  memberFacingProbeSummary,
  type PrivacyProbeHistoryEntry,
} from '@/lib/vpn/privacy-probe-history'
import {
  formatPrivacyProbeMessage,
  runPrivacyLeakProbe,
} from '@/lib/vpn/privacy-leak-probe'
import { loadDesktopSettings, saveDesktopSettings } from '@/lib/vpn/desktop-settings'
import { setTrayHideOnClose } from '@/lib/desktop/tray'
import { detectClientPlatform } from '@/lib/app-meta'
import { useConnectStore } from '@/stores/connect'
import { storeToRefs } from 'pinia'
import { appendDebugLog } from '@/lib/debug/app-debug-log'
import { message } from '@/lib/ui/message'

const connect = useConnectStore()
const { isConnected } = storeToRefs(connect)
const isAndroid = detectClientPlatform() === 'android'

const pageTitle = '连接与隐私'
const pageSubtitle = computed(() =>
  isAndroid ? '防泄露默认开启；可调整重连与隐私检测' : '系统代理下可调整重连、托盘与隐私自检',
)
const footerHint = computed(() =>
  isAndroid
    ? '断线自动重连开启后，切网/断网恢复将尝试完整重连。系统 VPN 授权与省电白名单请在系统设置中确认。'
    : '关闭窗口时若已开启「关闭时最小化到托盘」，VPN 连接会保持；可在托盘图标右键断开或退出。',
)

const settings = reactive(loadDesktopSettings())
const probeRunning = ref(false)
const probeMessage = ref<string | null>(null)
const probePassed = ref(false)
const probeHistory = ref<PrivacyProbeHistoryEntry[]>(loadPrivacyProbeHistory())
const latestProbe = computed(() => probeHistory.value[0] ?? null)

function formatProbeTime(atMillis: number) {
  return dayjs(atMillis).format('YYYY-MM-DD HH:mm')
}

function clearHistory() {
  clearPrivacyProbeHistory()
  probeHistory.value = []
  message.success('已清空自检记录')
}

onMounted(() => {
  probeHistory.value = loadPrivacyProbeHistory()
})

async function runPrivacyProbe() {
  if (!isConnected.value || probeRunning.value) return
  probeRunning.value = true
  probeMessage.value = null
  try {
    const result = await runPrivacyLeakProbe()
    probePassed.value = result.passed
    probeMessage.value = formatPrivacyProbeMessage(result)
    probeHistory.value = appendPrivacyProbeHistory(result)
    appendDebugLog('privacy', probeMessage.value, result.passed ? 'info' : 'warn')
  } catch (e: unknown) {
    probeMessage.value = e instanceof Error ? e.message : '自检失败'
  } finally {
    probeRunning.value = false
  }
}

type ToggleKey = 'autoReconnect' | 'hideOnClose' | 'restoreSession'

const allToggleItems: Array<{ key: ToggleKey; title: string; desc: string; desktopOnly?: boolean }> = [
  {
    key: 'autoReconnect',
    title: '意外断线自动重连',
    desc: '隧道异常退出时自动尝试重连（最多 3 次，退避 3s/6s/10s）',
  },
  {
    key: 'hideOnClose',
    title: '关闭窗口时最小化到托盘',
    desc: '点击窗口关闭按钮时隐藏到系统托盘，不断开 VPN',
    desktopOnly: true,
  },
  {
    key: 'restoreSession',
    title: '启动时恢复上次连接',
    desc: isAndroid
      ? '应用启动后若上次仍登录且有会话，将尝试自动连接'
      : '应用启动后若上次异常退出且仍登录，将尝试自动连接',
  },
]

const toggleItems = computed(() =>
  allToggleItems.filter((item) => !(item.desktopOnly && isAndroid)),
)

async function setSetting(key: ToggleKey, value: boolean) {
  settings[key] = value
  saveDesktopSettings({ [key]: value })
  if (key === 'hideOnClose' && !isAndroid) {
    await setTrayHideOnClose(value)
  }
  message.success('设置已保存')
}

watch(
  () => settings.hideOnClose,
  (enabled) => {
    if (!isAndroid) void setTrayHideOnClose(enabled)
  },
  { immediate: true },
)
</script>

<style scoped>
.mode-desc {
  margin: 0 0 var(--ky-space-sm);
  font-size: var(--ky-font-sm);
  color: var(--ky-text);
  line-height: 1.6;
}

.mode-desc.muted {
  color: var(--ky-text-muted);
  font-size: var(--ky-font-xs);
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-md);
  padding: var(--ky-space-md) 0;
  border-bottom: 1px solid var(--ky-border-soft);
}

.setting-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.setting-copy {
  flex: 1;
  min-width: 0;
}

.setting-title {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.setting-desc {
  margin: 4px 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  line-height: 1.5;
}

.hint {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.6;
}

.probe-desc {
  margin: 0 0 var(--ky-space-md);
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.6;
}

.probe-history {
  margin-top: var(--ky-space-md);
}

.probe-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ky-space-sm);
}

.probe-history-title {
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-text);
}

.probe-history-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.probe-history-list li {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  padding: var(--ky-space-xs) 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.probe-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.probe-dot--ok {
  background: var(--ky-success, #52c41a);
}

.probe-dot--warn {
  background: var(--ky-warning, #faad14);
}

.probe-summary {
  flex: 1;
  min-width: 0;
}

.probe-time {
  flex-shrink: 0;
  color: var(--ky-text-muted);
}
</style>
