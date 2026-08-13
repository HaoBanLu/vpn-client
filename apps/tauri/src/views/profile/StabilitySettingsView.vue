<template>
  <KyPage sub>
    <PageHeader :title="pageTitle" :subtitle="pageSubtitle" />

    <KyCard title="连接方式">
      <template v-if="isAndroid">
        <p class="mode-desc">
          当前使用系统 VPN。连接后流量默认走隧道。
        </p>
        <p class="mode-desc muted">
          指定应用不走加速请到「应用直连」。断网保护请用下方系统 Always-on。
        </p>
      </template>
      <template v-else>
        <p class="mode-desc">
          当前使用 <strong>系统代理</strong>（本地混合端口 + 系统 HTTP/HTTPS），无需管理员权限。
        </p>
        <p class="mode-desc muted">
          TUN 全隧道、Always-on、分应用直连为 Android 能力，桌面端不提供。
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

    <KyCard v-if="isAndroid" title="系统级加固">
      <p class="probe-desc">
        已完成 {{ stability?.hardeningDoneCount ?? 0 }} /
        {{ stability?.hardeningTotal ?? 3 }} 项。开启后由系统保持 VPN，减少意外掉线。
      </p>
      <button type="button" class="todo-row" @click="onOpenVpnSettings">
        <span class="todo-dot" :class="stability?.alwaysOnConfigured ? 'todo-dot--ok' : ''" />
        <span class="todo-copy">
          <span class="todo-title">始终开启 VPN</span>
          <span class="todo-desc">断网后由系统自动重新拉起 VPN</span>
        </span>
      </button>
      <button type="button" class="todo-row" @click="onOpenVpnSettings">
        <span class="todo-dot" :class="stability?.lockdownConfigured ? 'todo-dot--ok' : ''" />
        <span class="todo-copy">
          <span class="todo-title">禁止绕过 VPN</span>
          <span class="todo-desc">未走 VPN 时禁止上网，降低 IP 泄露</span>
        </span>
      </button>
      <button type="button" class="todo-row" @click="onOpenBatterySettings">
        <span
          class="todo-dot"
          :class="stability?.batteryOptimizationIgnored ? 'todo-dot--ok' : ''"
        />
        <span class="todo-copy">
          <span class="todo-title">关闭电池优化</span>
          <span class="todo-desc">避免后台被系统杀掉导致掉线</span>
        </span>
      </button>
      <KyButton class="hardening-btn" block @click="onOpenVpnSettings">打开系统 VPN 设置</KyButton>
      <KyButton block type="default" @click="refreshStability">刷新状态</KyButton>
    </KyCard>

    <KyCard title="隐私自检">
      <p class="probe-desc">
        连接 VPN 后做基础检测（出口 IP / DNS / IPv6），帮助发现明显泄露风险；通过不等于绝对无泄露。
      </p>
      <KyButton
        type="primary"
        block
        :loading="probeRunning"
        :disabled="!isConnected"
        @click="runPrivacyProbe"
      >
        {{
          probeRunning ? '正在检测…' : isConnected ? '立即基础检测' : '请先连接 VPN 后再检测'
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
import { computed, reactive, ref, watch, onMounted, onActivated } from 'vue'
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
import {
  getAndroidStabilityStatus,
  openBatteryOptimizationSettings,
  openVpnSettings,
  setBootAutoConnect,
  type AndroidStabilityStatus,
} from '@/lib/vpn/android-stability'
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
  isAndroid
    ? '连接后流量默认走隧道；可调整重连与系统 Always-on'
    : '系统代理下可调整重连、托盘与隐私自检',
)
const footerHint = computed(() =>
  isAndroid
    ? '开启断线自动重连后，切网或断网恢复将尝试重新连接。Always-on 与省电白名单请在系统设置中确认。'
    : '关闭窗口时若已开启「关闭时最小化到托盘」，VPN 连接会保持；可在托盘图标右键断开或退出。',
)

const settings = reactive(loadDesktopSettings())
const probeRunning = ref(false)
const probeMessage = ref<string | null>(null)
const probePassed = ref(false)
const probeHistory = ref<PrivacyProbeHistoryEntry[]>(loadPrivacyProbeHistory())
const latestProbe = computed(() => probeHistory.value[0] ?? null)
const stability = ref<AndroidStabilityStatus | null>(null)

function formatProbeTime(atMillis: number) {
  return dayjs(atMillis).format('YYYY-MM-DD HH:mm')
}

function clearHistory() {
  clearPrivacyProbeHistory()
  probeHistory.value = []
  message.success('已清空自检记录')
}

async function refreshStability() {
  if (!isAndroid) return
  try {
    const status = await getAndroidStabilityStatus()
    stability.value = status
    settings.bootAutoConnect = status.bootAutoConnectEnabled
    saveDesktopSettings({ bootAutoConnect: status.bootAutoConnectEnabled })
  } catch {
    // 非 Android 插件环境忽略
  }
}

async function onOpenVpnSettings() {
  try {
    await openVpnSettings()
    message.info('请在系统设置中开启 Always-on / 禁止绕过')
    window.setTimeout(() => {
      void refreshStability()
    }, 1200)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '无法打开系统设置')
  }
}

async function onOpenBatterySettings() {
  try {
    await openBatteryOptimizationSettings()
    message.info('请允许忽略电池优化')
    window.setTimeout(() => {
      void refreshStability()
    }, 1200)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '无法打开电池设置')
  }
}

onMounted(() => {
  probeHistory.value = loadPrivacyProbeHistory()
  void refreshStability()
})

onActivated(() => {
  void refreshStability()
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

type ToggleKey = 'autoReconnect' | 'hideOnClose' | 'restoreSession' | 'bootAutoConnect'

const allToggleItems: Array<{
  key: ToggleKey
  title: string
  desc: string
  desktopOnly?: boolean
  androidOnly?: boolean
}> = [
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
  {
    key: 'bootAutoConnect',
    title: '开机自动恢复连接',
    desc: '设备重启后尝试恢复上次连接（需已授权 VPN）',
    androidOnly: true,
  },
]

const toggleItems = computed(() =>
  allToggleItems.filter((item) => {
    if (item.desktopOnly && isAndroid) return false
    if (item.androidOnly && !isAndroid) return false
    return true
  }),
)

async function setSetting(key: ToggleKey, value: boolean) {
  settings[key] = value
  saveDesktopSettings({ [key]: value })
  if (key === 'hideOnClose' && !isAndroid) {
    await setTrayHideOnClose(value)
  }
  if (key === 'bootAutoConnect' && isAndroid) {
    try {
      const enabled = await setBootAutoConnect(value)
      settings.bootAutoConnect = enabled
      saveDesktopSettings({ bootAutoConnect: enabled })
    } catch (e) {
      settings.bootAutoConnect = !value
      saveDesktopSettings({ bootAutoConnect: !value })
      message.error(e instanceof Error ? e.message : '保存开机自连失败')
      return
    }
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

.todo-row {
  display: flex;
  align-items: flex-start;
  gap: var(--ky-space-md);
  width: 100%;
  padding: var(--ky-space-md) 0;
  border: 0;
  border-bottom: 1px solid var(--ky-border-soft);
  background: transparent;
  text-align: left;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.todo-row:last-of-type {
  border-bottom: 0;
}

.todo-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--ky-border, #d9d9d9);
}

.todo-dot--ok {
  background: var(--ky-success, #52c41a);
}

.todo-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.todo-title {
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.todo-desc {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  line-height: 1.45;
}

.hardening-btn {
  margin: var(--ky-space-md) 0 var(--ky-space-sm);
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
