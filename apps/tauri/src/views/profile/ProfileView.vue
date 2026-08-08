<template>
  <KyTabPage
    title="我的"
    :on-refresh="refresh"
    :loading="account.loading"
  >
    <template #before>
      <KyCard v-if="account.user" class="account-bar" highlight>
        <div class="account-bar__row">
          <div class="account-bar__copy">
            <div class="account-bar__name-row">
              <p class="account-bar__email">{{ account.user.email }}</p>
              <span v-if="account.subscription" class="account-bar__vip">VIP</span>
            </div>
            <p class="account-bar__meta">
              <button type="button" class="account-bar__scenario" @click="openScenario">
                场景：{{ connect.connectionScenarioLabel }}
              </button>
              <span v-if="expiresLabel">到期 {{ expiresLabel }}</span>
            </p>
          </div>
          <KyButton size="small" @click="router.push({ name: 'Devices' })">设备</KyButton>
        </div>
      </KyCard>

      <KyCard
        v-for="item in recentNotifications"
        :key="`${item.id}-${item.type}`"
        :class="['notification-card', `notification-card--${item.type}`]"
        @click="router.push({ name: 'RechargeOrders' })"
      >
        <p class="notification-message">{{ item.message }}</p>
        <p class="notification-order">{{ item.orderNo }}</p>
      </KyCard>
    </template>

    <KyCard v-if="account.subscription" title="当前套餐">
      <p class="package-name">{{ account.subscription.package?.name || '有效套餐' }}</p>
      <p class="muted">到期：{{ account.subscription.expires_at?.slice(0, 10) }}</p>
      <p v-if="account.usage" class="muted">
        剩余流量：{{ formatTraffic(account.usage.remaining) }} / {{ formatTraffic(account.usage.total) }}
      </p>
      <div class="sub-actions">
        <KyButton @click="router.push({ name: 'Packages' })">购买套餐</KyButton>
        <KyButton @click="router.push({ name: 'Traffic' })">流量统计</KyButton>
      </div>
    </KyCard>
    <KyCard v-else flat>
      <KyEmpty description="暂无套餐">
        <KyButton type="primary" @click="router.push({ name: 'Packages' })">去购买</KyButton>
      </KyEmpty>
    </KyCard>

    <p class="ky-section-title">账户</p>
    <div class="entry-list">
      <KyListItem
        v-for="item in accountItems"
        :key="item.route"
        class="entry-card"
        :title="item.title"
        :subtitle="item.subtitle"
        arrow
        @click="router.push({ name: item.route })"
      />
    </div>

    <p class="ky-section-title">连接设置</p>
    <div class="entry-list">
      <KyListItem
        v-for="item in connectItems"
        :key="item.route"
        class="entry-card"
        :title="item.title"
        :subtitle="item.subtitle"
        arrow
        @click="onConnectItem(item)"
      />
    </div>

    <p class="ky-section-title">服务</p>
    <div class="entry-list">
      <KyListItem
        v-for="item in serviceItems"
        :key="item.route"
        class="entry-card"
        :title="item.title"
        :subtitle="item.subtitle"
        arrow
        @click="router.push({ name: item.route })"
      />
    </div>

    <p class="ky-section-title">设置</p>
    <div class="entry-list">
      <KyListItem
        v-for="item in settingItems"
        :key="item.route"
        class="entry-card"
        :title="item.title"
        :subtitle="item.subtitle"
        arrow
        @click="router.push({ name: item.route })"
      />
    </div>

    <KyButton danger block size="large" @click="logout">退出登录</KyButton>

    <div v-if="scenarioOpen" class="scenario-mask" @click.self="scenarioOpen = false">
      <div class="scenario-sheet" role="dialog" aria-label="使用场景">
        <p class="scenario-sheet__title">使用场景</p>
        <p class="scenario-sheet__desc">影响默认线路画像；切换后若已连接将自动重连</p>
        <button
          v-for="opt in scenarioOptions"
          :key="opt.value"
          type="button"
          class="scenario-option"
          :class="{ 'scenario-option--active': connect.connectionScenario === opt.value }"
          @click="pickScenario(opt.value)"
        >
          <span class="scenario-option__label">{{ opt.label }}</span>
          <span class="scenario-option__hint">{{ opt.hint }}</span>
        </button>
        <KyButton block @click="scenarioOpen = false">取消</KyButton>
      </div>
    </div>
  </KyTabPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BugOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  FilterOutlined,
  LockOutlined,
  ProfileOutlined,
  QuestionCircleOutlined,
  SettingOutlined,
  ShoppingOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import KyTabPage from '@/components/KyTabPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyListItem from '@/components/KyListItem.vue'
import { KyButton, KyEmpty } from '@/components/ky'
import { formatTraffic } from '@/lib/format'
import { detectClientPlatform } from '@/lib/app-meta'
import { CONNECTION_SCENARIO, type ConnectionScenarioValue } from '@/lib/vpn/connection-scenario'
import { useAuthStore } from '@/stores/auth'
import { useAccountStore } from '@/stores/account'
import { useConnectStore } from '@/stores/connect'
import { message } from '@/lib/ui/message'

type MenuItem = { title: string; subtitle: string; route: string; icon: Component; action?: 'scenario' | 'app-direct-soon' }

const auth = useAuthStore()
const account = useAccountStore()
const connect = useConnectStore()
const router = useRouter()
const scenarioOpen = ref(false)
const isAndroid = detectClientPlatform() === 'android'

const recentNotifications = computed(() => account.notifications.slice(-2).reverse())
const expiresLabel = computed(() => account.subscription?.expires_at?.slice(0, 10) ?? null)
const balanceSubtitle = computed(() =>
  account.user ? `余额 ${formatMoneySafe(account.user.balance)}` : '余额充值与到账',
)

function formatMoneySafe(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

const accountItems = computed<MenuItem[]>(() => [
  { title: 'USDT 充值', subtitle: balanceSubtitle.value, route: 'Recharge', icon: WalletOutlined },
  { title: '充值订单', subtitle: 'USDT 充值记录与状态', route: 'RechargeOrders', icon: FileTextOutlined },
  { title: '购买记录', subtitle: '套餐订单与支付状态', route: 'PurchaseOrders', icon: ShoppingOutlined },
])

const connectItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = []
  if (isAndroid) {
    items.push({
      title: '应用直连',
      subtitle: '指定应用不走 VPN（即将开放）',
      route: 'Profile',
      icon: FilterOutlined,
      action: 'app-direct-soon',
    })
  }
  // Android 对齐原生：规则直连对全员开放；桌面仍仅调试账号
  if (isAndroid || account.user?.app_debug_enabled) {
    items.push({
      title: '规则直连',
      subtitle: isAndroid ? '指定域名或 IP 不经代理节点' : '域名/IP 绕过 VPN（高级）',
      route: 'DirectBypassRules',
      icon: FilterOutlined,
    })
  }
  items.push({
    title: '连接与隐私',
    subtitle: isAndroid ? '防泄露保护、稳定性与隐私检测' : '自动重连、托盘与泄露自检',
    route: 'StabilitySettings',
    icon: SettingOutlined,
  })
  items.push({
    title: '使用场景',
    subtitle: `当前：${connect.connectionScenarioLabel}`,
    route: 'Profile',
    icon: SettingOutlined,
    action: 'scenario',
  })
  return items
})

const serviceItems = computed<MenuItem[]>(() => [
  ...(account.supportEnabled
    ? [{ title: '在线客服', subtitle: 'Telegram、群组与人工协助', route: 'Support', icon: CustomerServiceOutlined }]
    : []),
  { title: '我的工单', subtitle: '问题反馈与客服回复', route: 'Tickets', icon: FileTextOutlined },
  // 帮助中心：Android 对齐原生全员可见；桌面保留「订阅导出」仅调试
  ...(isAndroid || account.user?.app_debug_enabled
    ? [
        {
          title: isAndroid ? '帮助中心' : '订阅导出',
          subtitle: isAndroid ? '导出订阅链接与连接辅助' : 'Clash 订阅链接（高级）',
          route: 'Help',
          icon: QuestionCircleOutlined,
        },
      ]
    : []),
])

const settingItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    { title: '我的设备', subtitle: '登录设备与会话管理', route: 'Devices', icon: ProfileOutlined },
    { title: '修改密码', subtitle: '账户安全', route: 'ChangePassword', icon: LockOutlined },
    { title: '关于跨云', subtitle: '版本与更新', route: 'About', icon: ProfileOutlined },
  ]
  if (account.user?.app_debug_enabled) {
    items.splice(2, 0, {
      title: '诊断日志',
      subtitle: '本地 VPN 事件与上传',
      route: 'DebugLog',
      icon: BugOutlined,
    })
  }
  return items
})

const scenarioOptions: Array<{ value: ConnectionScenarioValue; label: string; hint: string }> = [
  { value: CONNECTION_SCENARIO.AUTO, label: '自动', hint: '按节点地区智能选择画像' },
  { value: CONNECTION_SCENARIO.RETURN_HOME, label: '回国加速', hint: '优先国内可达线路' },
  { value: CONNECTION_SCENARIO.OVERSEAS, label: '海外访问', hint: '优先出海访问线路' },
]

function openScenario() {
  scenarioOpen.value = true
}

function onConnectItem(item: MenuItem) {
  if (item.action === 'scenario') {
    scenarioOpen.value = true
    return
  }
  if (item.action === 'app-direct-soon') {
    message.info('应用直连正在接入，下个版本开放')
    return
  }
  router.push({ name: item.route })
}

async function pickScenario(value: ConnectionScenarioValue) {
  scenarioOpen.value = false
  await connect.updateConnectionScenario(value)
}

async function refresh() {
  try {
    await account.refreshAccount()
  } catch {
    // 错误提示由请求层处理
  }
}

async function logout() {
  await auth.logout()
  router.replace({ name: 'Login' })
}

onMounted(refresh)
</script>

<style scoped>
.account-bar__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-md);
}

.account-bar__copy {
  min-width: 0;
  flex: 1;
}

.account-bar__name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.account-bar__email {
  margin: 0;
  font-size: var(--ky-font-lg);
  font-weight: 650;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-bar__vip {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: var(--ky-radius-full);
  background: var(--ky-accent-bg);
  color: var(--ky-accent);
  font-size: 11px;
  font-weight: 700;
}

.account-bar__meta {
  margin: 6px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.account-bar__scenario {
  appearance: none;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ky-accent-soft);
  font: inherit;
  cursor: pointer;
}

.package-name {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.muted {
  margin: var(--ky-space-xs) 0 0;
  color: var(--ky-text-secondary);
  font-size: var(--ky-font-sm);
}

.sub-actions {
  display: flex;
  gap: var(--ky-space-sm);
  margin-top: var(--ky-space-md);
}

.entry-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.entry-card {
  padding: 15px 16px;
  border: 1px solid var(--ky-border);
  border-radius: var(--ky-radius-lg);
  background: rgba(26, 35, 56, 0.32);
}

.notification-card {
  cursor: pointer;
}

.notification-card--paid {
  border-color: rgba(74, 222, 128, 0.25);
  background: var(--ky-success-bg);
}

.notification-card--rejected {
  border-color: rgba(248, 113, 113, 0.25);
  background: var(--ky-danger-bg);
}

.notification-message {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.notification-order {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.scenario-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: var(--ky-space-md);
  padding-bottom: calc(var(--ky-space-md) + env(safe-area-inset-bottom, 0px));
}

.scenario-sheet {
  width: min(460px, 100%);
  border-radius: var(--ky-radius-xl);
  background: var(--ky-bg-elevated);
  border: 1px solid var(--ky-border);
  padding: var(--ky-space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-sm);
}

.scenario-sheet__title {
  margin: 0;
  font-size: var(--ky-font-lg);
  font-weight: 700;
}

.scenario-sheet__desc {
  margin: 0 0 var(--ky-space-sm);
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.scenario-option {
  appearance: none;
  border: 1px solid var(--ky-border);
  border-radius: var(--ky-radius-md);
  background: var(--ky-bg-card);
  color: var(--ky-text);
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.scenario-option--active {
  border-color: var(--ky-border-strong);
  background: var(--ky-accent-bg);
}

.scenario-option__label {
  font-weight: 650;
}

.scenario-option__hint {
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}
</style>
