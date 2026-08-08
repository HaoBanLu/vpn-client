<template>
  <KyTabPage
    title="套餐"
    :on-refresh="load"
    :loading="loading"
  >
    <KySubscriptionSummary
      v-if="account.subscription"
      :package-name="account.subscription.package?.name || '当前套餐'"
      :status-text="statusLabel"
      :status-variant="statusBadgeVariant"
      :progress-percent="trafficPct"
      :usage-text="usageText"
    />

    <KyEmpty v-if="!loading && packages.length === 0" description="暂无可用套餐，请稍后重试">
      <KyButton type="primary" @click="load">重新加载</KyButton>
    </KyEmpty>

    <KyGrid2 v-else>
      <KyPackageCard
        v-for="(item, index) in packages"
        :key="item.id"
        :name="item.name"
        :price="formatMoney(item.price)"
        :duration-days="item.duration_days"
        :traffic-gb="Math.round(item.traffic_gb)"
        :description="item.description"
        :badge-text="packageBadgeText(item, index)"
        :badge-variant="packageBadgeVariant(item, index)"
        :highlight="isCurrentPackage(account.subscription, item) || (index === 0 && !account.subscription)"
        :loading="buyingId === item.id"
        :disabled="!buttonState(item).enabled && buttonState(item).label !== '余额不足，去充值'"
        :action-label="buyingId === item.id ? '处理中…' : buttonState(item).label"
        @action="buy(item)"
      />
    </KyGrid2>
  </KyTabPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal } from '@/lib/ui/confirm'
import { message } from '@/lib/ui/message'
import KyTabPage from '@/components/KyTabPage.vue'
import KyGrid2 from '@/components/KyGrid2.vue'
import KyPackageCard from '@/components/KyPackageCard.vue'
import KySubscriptionSummary from '@/components/KySubscriptionSummary.vue'
import { KyButton, KyEmpty } from '@/components/ky'
import type { StatusBadgeVariant } from '@/components/StatusBadge.vue'
import { clientApi, type PackageItem } from '@/api/client'
import { ApiBusinessError } from '@/api/request'
import { formatMoney } from '@/lib/format'
import { mapApiError } from '@/lib/api-error'
import {
  isCurrentPackage,
  purchaseButtonState,
  purchaseSuccessMessage,
  subscriptionStatusLabel,
  trafficProgress,
} from '@/lib/subscription'
import { useConnectStore } from '@/stores/connect'
import { useAccountStore } from '@/stores/account'

const router = useRouter()
const connect = useConnectStore()
const account = useAccountStore()
const loading = ref(false)
const packages = ref<PackageItem[]>([])
const buyingId = ref<number | null>(null)

const statusLabel = computed(() => subscriptionStatusLabel(account.subscription, account.usage))
const statusBadgeVariant = computed((): StatusBadgeVariant => {
  if (statusLabel.value === '流量不足') return 'error'
  if (statusLabel.value === '即将到期') return 'warning'
  return 'success'
})
const trafficPct = computed(() => Math.round(trafficProgress(account.usage) * 100))
const usageText = computed(() => {
  if (!account.usage) return ''
  const expiry = account.subscription?.expires_at?.slice(0, 10) || '-'
  return `剩余 ${account.usage.remaining.toFixed(1)} GB / ${account.usage.total.toFixed(0)} GB · ${expiry} 到期`
})

function packageBadgeText(item: PackageItem, index: number) {
  if (isCurrentPackage(account.subscription, item)) return '当前套餐'
  if (index === 0 && !account.subscription) return '推荐'
  return null
}

function packageBadgeVariant(item: PackageItem, index: number): StatusBadgeVariant {
  if (isCurrentPackage(account.subscription, item)) return 'success'
  if (index === 0 && !account.subscription) return 'recommend'
  return 'success'
}

function buttonState(item: PackageItem) {
  return purchaseButtonState(
    account.subscription,
    item,
    account.user?.balance ?? 0,
    buyingId.value === item.id,
  )
}

async function load() {
  loading.value = true
  try {
    const [pkgRes] = await Promise.all([clientApi.getPackages(), account.refreshAccount()])
    packages.value = pkgRes.data.packages
  } catch (error) {
    message.error(mapApiError(error, '套餐加载失败'))
  } finally {
    loading.value = false
  }
}

async function pollOrderStatus(orderId: number) {
  for (let i = 0; i < 10; i += 1) {
    await new Promise((r) => setTimeout(r, 1000))
    const status = (await clientApi.getOrderStatus(orderId)).data
    if (status.status === 'paid') return true
    if (['cancelled', 'expired', 'failed'].includes(status.status)) return false
  }
  return false
}

async function buy(item: PackageItem) {
  const state = buttonState(item)
  if (state.insufficientBalance) {
    Modal.confirm({
      title: '余额不足',
      content: '当前余额不足以购买该套餐，是否前往充值？',
      okText: '去充值',
      onOk: () => {
        void router.push({ name: 'Recharge' })
      },
    })
    return
  }

  const subBefore = account.subscription
  buyingId.value = item.id
  try {
    const order = (await clientApi.createOrder(item.id)).data
    await clientApi.payOrder(order.id)
    const paid = await pollOrderStatus(order.id)
    if (paid) {
      message.success(purchaseSuccessMessage(subBefore, item))
      await connect.refresh()
      await load()
    } else {
      message.warning('支付处理中，请稍后在购买记录查看')
    }
  } catch (e: unknown) {
    if (e instanceof ApiBusinessError && e.appCode === 'INSUFFICIENT_BALANCE') {
      Modal.confirm({
        title: '余额不足',
        content: e.message || '请先充值后再购买套餐',
        okText: '去充值',
        onOk: () => {
        void router.push({ name: 'Recharge' })
      },
      })
      return
    }
    message.error(e instanceof Error ? e.message : '购买失败')
  } finally {
    buyingId.value = null
  }
}

onMounted(load)
</script>
