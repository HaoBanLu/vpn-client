<template>
  <KyTabPage
    title="我的账户"
    subtitle="套餐权益、余额与账户安全"
    :on-refresh="refresh"
    :loading="account.loading"
  >
    <template #before>
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

    <KyCard v-if="account.user" highlight>
      <p class="email">{{ account.user.email }}</p>
      <p class="balance">{{ formatMoney(account.user.balance) }}</p>
      <div class="balance-actions">
        <KyButton type="primary" size="large" @click="router.push({ name: 'Recharge' })">充值</KyButton>
        <KyButton size="large" @click="router.push({ name: 'RechargeOrders' })">充值记录</KyButton>
      </div>
    </KyCard>

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

    <p class="ky-section-title">订单</p>
    <div class="entry-list">
      <KyListItem
        v-for="item in orderItems"
        :key="item.route"
        class="entry-card"
        :title="item.title"
        :subtitle="item.subtitle"
        arrow
        @click="router.push({ name: item.route })"
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
  </KyTabPage>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  FilterOutlined,
  BugOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  LockOutlined,
  ProfileOutlined,
  QuestionCircleOutlined,
  SettingOutlined,
  ShoppingOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'
import KyTabPage from '@/components/KyTabPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyListItem from '@/components/KyListItem.vue'
import { KyButton, KyEmpty } from '@/components/ky'
import { formatMoney, formatTraffic } from '@/lib/format'
import { useAuthStore } from '@/stores/auth'
import { useAccountStore } from '@/stores/account'

const auth = useAuthStore()
const account = useAccountStore()
const router = useRouter()

const orderItems: Array<{ title: string; subtitle: string; route: string; icon: Component }> = [
  { title: '购买记录', subtitle: '套餐订单', route: 'PurchaseOrders', icon: ShoppingOutlined },
]

const recentNotifications = computed(() => account.notifications.slice(-2).reverse())

const serviceItems = computed<Array<{ title: string; subtitle: string; route: string; icon: Component }>>(() => [
  ...(account.supportEnabled
    ? [{ title: '在线客服', subtitle: 'Telegram、群组与人工协助', route: 'Support', icon: CustomerServiceOutlined }]
    : []),
  { title: '我的工单', subtitle: '问题反馈与回复', route: 'Tickets', icon: FileTextOutlined },
  { title: '帮助中心', subtitle: '导出订阅链接与连接辅助', route: 'Help', icon: QuestionCircleOutlined },
])

const settingItems = computed<Array<{ title: string; subtitle: string; route: string; icon: Component }>>(() => {
  const items: Array<{ title: string; subtitle: string; route: string; icon: Component }> = [
    { title: '连接与隐私', subtitle: '自动重连、托盘与泄露自检', route: 'StabilitySettings', icon: SettingOutlined },
    { title: '规则直连', subtitle: '域名/IP 绕过 VPN', route: 'DirectBypassRules', icon: FilterOutlined },
  ]
  if (account.user?.app_debug_enabled) {
    items.push({
      title: '诊断日志',
      subtitle: '本地 VPN 事件与上传',
      route: 'DebugLog',
      icon: BugOutlined,
    })
  }
  items.push(
    { title: '我的设备', subtitle: '登录设备与会话管理', route: 'Devices', icon: ProfileOutlined },
    { title: '修改密码', subtitle: '账户安全', route: 'ChangePassword', icon: LockOutlined },
    { title: '关于跨云', subtitle: '版本与更新', route: 'About', icon: ProfileOutlined },
  )
  return items
})

async function refresh() {
  try {
    await account.refreshAccount()
  } catch {
    // 错误提示由请求层处理，避免挂载钩子未捕获异常
  }
}

async function logout() {
  await auth.logout()
  router.replace({ name: 'Login' })
}

onMounted(refresh)
</script>

<style scoped>
.email {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.balance {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-2xl);
  font-weight: 700;
  color: var(--ky-accent);
}

.balance-actions {
  display: flex;
  gap: var(--ky-space-sm);
  margin-top: var(--ky-space-md);
}

.package-name {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.muted {
  margin: var(--ky-space-xs) 0 0;
  color: var(--ky-text-muted);
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
</style>
