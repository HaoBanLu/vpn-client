<template>
  <KyTabPage
    page-class="packages-page"
    :on-refresh="load"
    :loading="loading && packages.length === 0 && !loadError"
    stack-gap="md"
  >
    <div v-if="loadError" class="packages-error">
      <KyAlert type="error" :message="loadError" />
      <KyButton type="primary" block @click="load">重试</KyButton>
    </div>

    <KyEmpty v-if="!loading && !loadError && packages.length === 0" description="暂无可用套餐，请稍后重试">
      <KyButton type="primary" @click="load">重新加载</KyButton>
    </KyEmpty>

    <div v-else-if="displayPackages.length > 0" class="packages-list">
      <KyPackageCard
        v-for="(item, index) in displayPackages"
        :key="item.id"
        :name="item.name"
        :price="formatMoney(item.price)"
        :duration-days="item.duration_days"
        :traffic-gb="Math.round(item.traffic_gb)"
        :description="item.description"
        :badge-text="packageBadgeText(item, index)"
        :badge-variant="packageBadgeVariant(item, index)"
        :highlight="isCurrentPackage(account.subscription, item)"
        :loading="buyingId === item.id"
        :disabled="!buttonState(item).enabled && buttonState(item).label !== '余额不足，去充值'"
        :action-label="buyingId === item.id ? '处理中…' : buttonState(item).label"
        @action="buy(item)"
      />
    </div>
  </KyTabPage>
</template>

<script setup lang="ts">
defineOptions({ name: 'PackagesView' })
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal } from '@/lib/ui/confirm'
import { message } from '@/lib/ui/message'
import KyTabPage from '@/components/KyTabPage.vue'
import KyPackageCard from '@/components/KyPackageCard.vue'
import { KyAlert, KyButton, KyEmpty } from '@/components/ky'
import type { StatusBadgeVariant } from '@/components/StatusBadge.vue'
import { clientApi, type PackageItem } from '@/api/client'
import { ApiBusinessError } from '@/api/request'
import { formatMoney } from '@/lib/format'
import { mapApiError, isNetworkConnectivityError } from '@/lib/api-error'
import {
  isCurrentPackage,
  purchaseButtonState,
  purchaseSuccessMessage,
} from '@/lib/subscription'
import { useConnectStore } from '@/stores/connect'
import { useAccountStore } from '@/stores/account'

const router = useRouter()
const connect = useConnectStore()
const account = useAccountStore()
const loading = ref(false)
const loadError = ref<string | null>(null)
const packages = ref<PackageItem[]>([])
const buyingId = ref<number | null>(null)

/** 当前套餐置顶，其余保持接口顺序 */
const displayPackages = computed(() => {
  const list = packages.value
  const sub = account.subscription
  if (!sub?.package_id || list.length < 2) return list
  const current: PackageItem[] = []
  const rest: PackageItem[] = []
  for (const item of list) {
    if (isCurrentPackage(sub, item)) current.push(item)
    else rest.push(item)
  }
  return current.length ? [...current, ...rest] : list
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
  loadError.value = null
  void account.refreshAccount().catch(() => {
    /* 账户失败走 store.loadError，不挡套餐列表 */
  })
  try {
    packages.value = (await clientApi.getPackages()).data.packages
  } catch (error) {
    if (isNetworkConnectivityError(error)) {
      try {
        if (!connect.isConnected && !connect.isConnecting && !connect.connectPending) {
          await connect.dropLeftoverTunnel('packages_fetch_blocked')
        }
        packages.value = (await clientApi.getPackages()).data.packages
        loadError.value = null
        return
      } catch (retryError) {
        loadError.value = mapApiError(retryError, '套餐加载失败')
        return
      }
    }
    loadError.value = mapApiError(error, '套餐加载失败')
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
      message.warning('支付处理中，请稍后在订单查看')
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

<style scoped>
.packages-error {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.packages-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  padding-top: 2px;
}

@media (min-width: 900px) {
  .packages-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
}
</style>
