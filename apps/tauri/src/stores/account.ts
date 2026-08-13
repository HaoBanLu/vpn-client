import { defineStore } from 'pinia'
import { ref } from 'vue'
import { message } from '@/lib/ui/message'
import { clientApi, type OrderItem, type RechargeOrderItem, type SupportChannelItem, type SubscriptionActive, type SubscriptionUsage, type UserBrief } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { configureAppDebug } from '@/lib/debug/app-debug-log'
import { mapApiError } from '@/lib/api-error'
import { shareInflight } from '@/lib/account-view-state'

export interface AppNotification {
  id: number
  orderNo: string
  message: string
  type: string
}

export const useAccountStore = defineStore('account', () => {
  const loading = ref(false)
  const fetched = ref(false)
  const loadError = ref<string | null>(null)
  const user = ref<UserBrief | null>(null)
  const subscription = ref<SubscriptionActive | null>(null)
  const usage = ref<SubscriptionUsage | null>(null)
  const orders = ref<OrderItem[]>([])
  const supportEnabled = ref(false)
  const supportChannels = ref<SupportChannelItem[]>([])
  const notifications = ref<AppNotification[]>([])
  const unreadNotificationCount = ref(0)

  let knownRechargeStatuses: Record<number, string> = {}
  let notificationTimer: ReturnType<typeof setInterval> | null = null
  let pollingInitialized = false
  const refreshHolder: { current: Promise<void> | null } = { current: null }

  async function refreshAccount() {
    return shareInflight(refreshHolder, runRefreshAccount)
  }

  async function runRefreshAccount() {
    loading.value = true
    try {
      const [me, sub, orderRes, rechargeRes, supportRes] = await Promise.all([
        clientApi.getMe(),
        clientApi.getActiveSubscription(),
        clientApi.getOrders(),
        clientApi.getRechargeOrders(),
        clientApi.getSupportConfig().catch(() => ({ data: { enabled: false, channels: [] } })),
      ])
      user.value = me.data
      subscription.value = sub.data
      usage.value = subscription.value ? (await clientApi.getUsage()).data : null
      orders.value = orderRes.data.orders ?? []
      const rechargeOrders = rechargeRes.data.orders ?? []
      knownRechargeStatuses = rechargeOrders.reduce<Record<number, string>>((acc, order) => {
        acc[order.id] = order.status
        return acc
      }, {})
      supportEnabled.value = supportRes.data.enabled
      supportChannels.value = supportRes.data.channels ?? []

      const auth = useAuthStore()
      auth.user = me.data
      localStorage.setItem('tauri_user', JSON.stringify(me.data))
      configureAppDebug(Boolean(me.data?.app_debug_enabled))
      loadError.value = null
      fetched.value = true
    } catch (e: unknown) {
      loadError.value = mapApiError(e, '账户信息加载失败')
      throw e
    } finally {
      loading.value = false
    }
  }

  function reset() {
    refreshHolder.current = null
    stopNotificationPolling()
    loading.value = false
    fetched.value = false
    loadError.value = null
    user.value = null
    subscription.value = null
    usage.value = null
    orders.value = []
    supportEnabled.value = false
    supportChannels.value = []
    notifications.value = []
    unreadNotificationCount.value = 0
    knownRechargeStatuses = {}
    pollingInitialized = false
  }

  function notificationMessage(status: string) {
    if (status === 'paid') return 'USDT 充值已到账，余额已更新'
    if (status === 'rejected') return 'USDT 充值被驳回，请查看原因'
    return '充值订单状态已更新'
  }

  function detectRechargeChanges(orders: RechargeOrderItem[]) {
    if (!pollingInitialized) return []
    return orders
      .map((order) => {
        const previous = knownRechargeStatuses[order.id]
        if (previous === 'submitted' && (order.status === 'paid' || order.status === 'rejected')) {
          return {
            id: order.id,
            orderNo: order.order_no,
            type: order.status,
            message: notificationMessage(order.status),
          }
        }
        return null
      })
      .filter((item): item is AppNotification => !!item)
  }

  async function pollRechargeNotifications() {
    try {
      const rechargeRes = await clientApi.getRechargeOrders()
      const rechargeOrders = rechargeRes.data.orders ?? []
      const changes = detectRechargeChanges(rechargeOrders)
      knownRechargeStatuses = rechargeOrders.reduce<Record<number, string>>((acc, order) => {
        acc[order.id] = order.status
        return acc
      }, {})
      pollingInitialized = true

      if (changes.length === 0) {
        const me = await clientApi.getMe()
        user.value = me.data
        return
      }

      const existing = new Set(notifications.value.map((item) => `${item.id}:${item.type}`))
      const fresh = changes.filter((item) => !existing.has(`${item.id}:${item.type}`))
      if (fresh.length === 0) return

      notifications.value = [...notifications.value, ...fresh].slice(-10)
      unreadNotificationCount.value += fresh.length
      message.info(fresh[fresh.length - 1].message)

      const [me, orderRes] = await Promise.all([clientApi.getMe(), clientApi.getOrders()])
      user.value = me.data
      orders.value = orderRes.data.orders
    } catch {
      // Polling is best-effort; visible pages still refresh explicitly.
    }
  }

  function startNotificationPolling() {
    if (notificationTimer) return
    void pollRechargeNotifications()
    notificationTimer = setInterval(() => {
      void pollRechargeNotifications()
    }, 30_000)
  }

  function stopNotificationPolling() {
    if (notificationTimer) {
      clearInterval(notificationTimer)
      notificationTimer = null
    }
  }

  function clearUnreadNotifications() {
    unreadNotificationCount.value = 0
  }

  return {
    loading,
    fetched,
    loadError,
    user,
    subscription,
    usage,
    orders,
    supportEnabled,
    supportChannels,
    notifications,
    unreadNotificationCount,
    refreshAccount,
    reset,
    startNotificationPolling,
    stopNotificationPolling,
    clearUnreadNotifications,
  }
})
