<template>
  <KyTabPage :on-refresh="refresh" :loading="account.loading && !account.user">
    <template #before>
      <div v-if="account.user" class="account-bar">
        <div class="account-bar__identity">
          <UserOutlined class="account-bar__avatar" />
          <div class="account-bar__copy">
            <div class="account-bar__name-row">
              <p class="account-bar__email">{{ account.user.email }}</p>
              <span v-if="account.subscription" class="account-bar__vip">VIP会员</span>
            </div>
            <p class="account-bar__meta">
              <button type="button" class="account-bar__scenario" @click="openScenario">
                {{ connect.connectionScenarioLabel }}
              </button>
              <span v-if="expiresLabel">到期: {{ expiresLabel }}</span>
            </p>
          </div>
        </div>
        <div class="account-bar__menu-wrap">
          <button
            type="button"
            class="account-bar__menu-btn"
            aria-label="账户菜单"
            @click="accountMenuOpen = !accountMenuOpen"
          >
            <MoreOutlined />
          </button>
          <div v-if="accountMenuOpen" class="account-bar__menu" role="menu">
            <button type="button" role="menuitem" @click="goMenu('Devices')">查看设备</button>
            <button type="button" role="menuitem" @click="goMenu('Recharge')">充值</button>
            <button type="button" role="menuitem" @click="goMenu('ChangePassword')">修改密码</button>
            <button type="button" role="menuitem" @click="openScenario">
              使用场景: {{ connect.connectionScenarioLabel }}
            </button>
            <button type="button" role="menuitem" class="account-bar__menu-danger" @click="logoutFromMenu">
              退出登录
            </button>
          </div>
        </div>
      </div>

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

    <div v-if="account.subscription" class="sub-hero">
      <div class="sub-hero__head">
        <div>
          <p class="sub-hero__label">我使用的套餐</p>
          <p class="sub-hero__name">{{ account.subscription.package?.name || '有效套餐' }}</p>
        </div>
        <span
          class="sub-hero__badge"
          :class="{
            'sub-hero__badge--warn': statusLabel === '即将到期',
            'sub-hero__badge--danger': statusLabel === '流量不足',
          }"
        >{{ statusLabel || '使用中' }}</span>
      </div>
      <p class="sub-hero__summary">{{ subscriptionSummary }}</p>
      <div class="sub-hero__bar">
        <div class="sub-hero__fill" :style="{ width: `${trafficPct}%` }" />
      </div>
      <div class="sub-hero__actions">
        <KyButton type="primary" class="sub-hero__btn" @click="router.push({ name: 'Packages' })">
          续费 / 升级
        </KyButton>
        <KyButton class="sub-hero__btn" @click="router.push({ name: 'Traffic' })">流量统计</KyButton>
      </div>
    </div>
    <div v-else class="sub-hero">
      <p class="sub-hero__label">我使用的套餐</p>
      <p class="sub-hero__name">暂无有效套餐</p>
      <p class="sub-hero__summary">购买套餐后即可使用跨云加速服务</p>
      <KyButton type="primary" block class="sub-hero__buy" @click="router.push({ name: 'Packages' })">
        去购买套餐
      </KyButton>
    </div>

    <section class="menu-panel">
      <div class="menu-panel__head">
        <span class="menu-panel__title">账户</span>
        <span v-if="account.user" class="menu-panel__meta">{{ balanceMeta }}</span>
      </div>
      <div class="menu-panel__body">
        <template v-for="(item, index) in accountItems" :key="item.route">
          <div v-if="index > 0" class="menu-panel__divider" aria-hidden="true" />
          <KyListItem
            :title="item.title"
            :subtitle="item.subtitle"
            :icon="item.icon"
            arrow
            compact
            bare-icon
            @click="router.push({ name: item.route })"
          />
        </template>
      </div>
    </section>

    <section class="menu-panel">
      <div class="menu-panel__head">
        <span class="menu-panel__title">连接设置</span>
      </div>
      <div class="menu-panel__body">
        <template v-for="(item, index) in connectItems" :key="`${item.title}-${item.route}`">
          <div v-if="index > 0" class="menu-panel__divider" aria-hidden="true" />
          <KyListItem
            :title="item.title"
            :subtitle="item.subtitle"
            :icon="item.icon"
            arrow
            compact
            bare-icon
            @click="onConnectItem(item)"
          />
        </template>
      </div>
    </section>

    <section class="menu-panel">
      <div class="menu-panel__head">
        <span class="menu-panel__title">帮助与支持</span>
      </div>
      <div class="menu-panel__body">
        <template v-for="(item, index) in helpItems" :key="`${item.title}-${item.route}`">
          <div v-if="index > 0" class="menu-panel__divider" aria-hidden="true" />
          <KyListItem
            :title="item.title"
            :subtitle="item.subtitle"
            :icon="item.icon"
            arrow
            compact
            bare-icon
            @click="router.push({ name: item.route })"
          />
        </template>
      </div>
    </section>

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
defineOptions({ name: 'ProfileView' })
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  BugOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  MoreOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  ShoppingOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import KyTabPage from '@/components/KyTabPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyListItem from '@/components/KyListItem.vue'
import { KyButton } from '@/components/ky'
import { APP_VERSION_CODE, APP_VERSION_NAME, detectClientPlatform } from '@/lib/app-meta'
import { CONNECTION_SCENARIO, type ConnectionScenarioValue } from '@/lib/vpn/connection-scenario'
import { subscriptionStatusLabel } from '@/lib/subscription'
import { useAuthStore } from '@/stores/auth'
import { useAccountStore } from '@/stores/account'
import { useConnectStore } from '@/stores/connect'

type MenuItem = { title: string; subtitle: string; route: string; icon: Component; action?: 'scenario' }

const auth = useAuthStore()
const account = useAccountStore()
const connect = useConnectStore()
const router = useRouter()
const scenarioOpen = ref(false)
const accountMenuOpen = ref(false)
const isAndroid = detectClientPlatform() === 'android'

const recentNotifications = computed(() => account.notifications.slice(-2).reverse())
const expiresLabel = computed(() => formatExpiry(account.subscription?.expires_at))
const balanceMeta = computed(() =>
  account.user ? `余额 ${formatMoneySafe(account.user.balance)}` : undefined,
)
const statusLabel = computed(() => subscriptionStatusLabel(account.subscription, account.usage))

const subscriptionSummary = computed(() => {
  const sub = account.subscription
  if (!sub) return ''
  const remaining = account.usage?.remaining
  const total = account.usage?.total ?? sub.traffic_total_gb
  const remText = remaining != null ? remaining.toFixed(1) : '-'
  const totalText = total != null ? Number(total).toFixed(0) : '-'
  const expiry = formatExpiryDateOnly(sub.expires_at) || '-'
  return `剩余 ${remText}/${totalText} GB · ${expiry} 到期`
})

const trafficPct = computed(() => {
  const used = account.usage?.used
  const total = account.usage?.total
  if (used == null || !total || total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((used / total) * 100)))
})

function formatMoneySafe(v: number) {
  return `¥${Number(v || 0).toFixed(2)}`
}

function formatExpiry(raw?: string | null) {
  if (!raw) return null
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw.slice(0, 16).replace('T', ' ')
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatExpiryDateOnly(raw?: string | null) {
  if (!raw) return null
  return raw.slice(0, 10)
}

const accountItems = computed<MenuItem[]>(() => [
  { title: 'USDT 充值', subtitle: '余额充值与到账', route: 'Recharge', icon: WalletOutlined },
  { title: '充值订单', subtitle: 'USDT 充值记录与状态', route: 'RechargeOrders', icon: FileTextOutlined },
  { title: '购买记录', subtitle: '套餐订单与支付状态', route: 'PurchaseOrders', icon: ShoppingOutlined },
])

const connectItems = computed<MenuItem[]>(() => [
  {
    title: '应用直连',
    subtitle: '指定应用不走 VPN，其余默认加速',
    route: 'AppDirectConnect',
    icon: AppstoreOutlined,
  },
  {
    title: '规则直连',
    subtitle: '指定域名或 IP 不经代理节点',
    route: 'DirectBypassRules',
    icon: GlobalOutlined,
  },
  {
    title: '连接与隐私',
    subtitle: '防泄露保护、稳定性与隐私检测',
    route: 'StabilitySettings',
    icon: SafetyCertificateOutlined,
  },
])

const helpItems = computed<MenuItem[]>(() => {
  const items: MenuItem[] = []
  if (account.supportEnabled) {
    items.push({
      title: '在线客服',
      subtitle: 'Telegram、群组与人工协助',
      route: 'Support',
      icon: CustomerServiceOutlined,
    })
  }
  items.push({ title: '我的工单', subtitle: '问题反馈与客服回复', route: 'Tickets', icon: FileTextOutlined })
  if (isAndroid || account.user?.app_debug_enabled) {
    items.push({
      title: isAndroid ? '帮助中心' : '订阅导出',
      subtitle: isAndroid ? '导出订阅链接与连接辅助' : 'Clash 订阅链接（高级）',
      route: 'Help',
      icon: QuestionCircleOutlined,
    })
  }
  if (account.user?.app_debug_enabled) {
    items.push({
      title: '诊断日志',
      subtitle: '连接问题排查与上报',
      route: 'DebugLog',
      icon: BugOutlined,
    })
  }
  items.push({
    title: '关于跨云',
    subtitle: `v${APP_VERSION_NAME} · code ${APP_VERSION_CODE}`,
    route: 'About',
    icon: InfoCircleOutlined,
  })
  return items
})

const scenarioOptions: Array<{ value: ConnectionScenarioValue; label: string; hint: string }> = [
  { value: CONNECTION_SCENARIO.AUTO, label: '自动', hint: '按所选节点地区/专线类型智能选择画像' },
  {
    value: CONNECTION_SCENARIO.RETURN_HOME,
    label: '回国加速',
    hint: '访问国内站；请选武汉/贵州等「回国专线」节点',
  },
  {
    value: CONNECTION_SCENARIO.OVERSEAS,
    label: '海外访问',
    hint: '访问外网；请选新加坡/香港等「海外直连」节点',
  },
]

function openScenario() {
  accountMenuOpen.value = false
  scenarioOpen.value = true
}

function goMenu(routeName: string) {
  accountMenuOpen.value = false
  router.push({ name: routeName })
}

async function logoutFromMenu() {
  accountMenuOpen.value = false
  await logout()
}

function onConnectItem(item: MenuItem) {
  if (item.action === 'scenario') {
    scenarioOpen.value = true
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
.account-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-sm);
  padding: 8px 2px 2px;
}

.account-bar__identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.account-bar__avatar {
  flex-shrink: 0;
  font-size: 28px;
  color: var(--ky-accent);
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
  border-radius: 10px;
  background: #ffc107;
  color: #1a1a1a;
  font-size: 12px;
  font-weight: 700;
}

.account-bar__meta {
  margin: 4px 0 0;
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
  color: var(--ky-accent);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.account-bar__menu-wrap {
  position: relative;
  flex-shrink: 0;
}

.account-bar__menu-btn {
  appearance: none;
  border: 0;
  width: 36px;
  height: 36px;
  border-radius: var(--ky-radius-full);
  background: transparent;
  color: var(--ky-text-secondary);
  font-size: 20px;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.account-bar__menu {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 20;
  min-width: 168px;
  padding: 6px;
  border-radius: var(--ky-radius-md);
  background: var(--ky-bg-elevated);
  border: 1px solid var(--ky-border);
  box-shadow: var(--ky-shadow-md);
  display: flex;
  flex-direction: column;
}

.account-bar__menu button {
  appearance: none;
  border: 0;
  background: transparent;
  color: var(--ky-text);
  text-align: left;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: var(--ky-font-sm);
  cursor: pointer;
}

.account-bar__menu button:hover {
  background: var(--ky-bg-card-hover);
}

.account-bar__menu-danger {
  color: var(--ky-danger) !important;
}

.sub-hero {
  border-radius: 20px;
  padding: 16px;
  background: linear-gradient(
    135deg,
    rgba(214, 228, 255, 0.95) 0%,
    rgba(232, 238, 248, 0.92) 55%,
    rgba(0, 212, 255, 0.12) 100%
  );
  border: 1px solid rgba(27, 77, 255, 0.08);
}

.sub-hero__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.sub-hero__label {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.sub-hero__name {
  margin: 4px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--ky-text);
}

.sub-hero__badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--ky-radius-full);
  background: rgba(255, 255, 255, 0.85);
  color: #2e7d32;
  font-size: 12px;
  font-weight: 650;
}

.sub-hero__badge::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.sub-hero__badge--warn {
  color: #b78103;
}

.sub-hero__badge--danger {
  color: #c62828;
}

.sub-hero__summary {
  margin: 10px 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.sub-hero__bar {
  margin-top: 8px;
  height: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.65);
  overflow: hidden;
}

.sub-hero__fill {
  height: 100%;
  background: var(--ky-accent);
}

.sub-hero__actions {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.sub-hero__btn {
  height: 36px !important;
  border-radius: 12px !important;
}

.sub-hero__buy {
  margin-top: 12px;
  height: 36px !important;
  border-radius: 12px !important;
}

.menu-panel {
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--ky-border-soft);
  box-shadow: var(--ky-shadow-sm);
  overflow: hidden;
  padding: 14px 16px;
}

.menu-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 0 0;
}

.menu-panel__title {
  font-size: var(--ky-font-sm);
  font-weight: 650;
  color: var(--ky-text);
}

.menu-panel__meta {
  font-size: 12px;
  color: var(--ky-text-muted);
}

.menu-panel__body {
  padding: 0;
  margin-top: 8px;
  border-top: 1px solid rgba(15, 23, 41, 0.06);
  padding-top: 4px;
}

.menu-panel__divider {
  height: 0;
  margin: 2px 0;
  border-top: 1px solid rgba(15, 23, 41, 0.05);
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
