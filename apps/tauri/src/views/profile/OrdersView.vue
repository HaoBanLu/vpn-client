<template>
  <KySubPage title="订单">
    <KyChipGroup :model-value="tab" :items="tabItems" @update:model-value="setTab" />

    <KyPullRefresh :on-refresh="load">
      <KySpin :spinning="loading" overlay>
        <template v-if="tab === 'recharge'">
          <KyEmpty v-if="!loading && rechargeOrders.length === 0" description="暂无充值记录">
            <KyButton type="primary" size="large" @click="router.push({ name: 'Recharge' })">去充值</KyButton>
          </KyEmpty>
          <div v-else class="order-list">
            <KyOrderCard
              v-for="item in rechargeOrders"
              :key="item.id"
              :order-no="item.order_no"
              :amount="formatUsdt(item.requested_usdt)"
              :sub="item.credited_cny ? `到账约 ${formatMoney(item.credited_cny)}` : undefined"
              :time="formatDateTime(item.created_at)"
              :error="item.status === 'rejected' && item.reject_reason ? `驳回：${item.reject_reason}` : undefined"
              @click="openRechargeDetail(item)"
            >
              <template #status>
                <KyTag :color="rechargeStatusColor(item.status)">
                  {{ rechargeStatusLabel(item.status, item.chain_auto_confirmed) }}
                </KyTag>
              </template>
            </KyOrderCard>
          </div>
        </template>

        <template v-else>
          <KyEmpty v-if="!loading && purchaseOrders.length === 0" description="暂无套餐订单">
            <KyButton type="primary" size="large" @click="router.push({ name: 'Packages' })">去购买套餐</KyButton>
          </KyEmpty>
          <div v-else class="order-list">
            <KyOrderCard
              v-for="item in purchaseOrders"
              :key="item.id"
              :order-no="`订单 #${item.id}`"
              :amount="formatMoney(item.amount)"
              :sub="`支付方式：${item.payment_method || '-'}`"
              :time="formatDateTime(item.created_at)"
              @click="openPurchaseDetail(item)"
            >
              <template #status>
                <KyTag :color="orderStatusColor(item.status)">{{ orderStatusLabel(item.status) }}</KyTag>
              </template>
            </KyOrderCard>
          </div>
        </template>
      </KySpin>
    </KyPullRefresh>

    <KyModal
      v-model:open="rechargeDetailOpen"
      title="充值订单详情"
      :show-footer="false"
      @cancel="closeRechargeDetail"
    >
      <template v-if="selectedRecharge">
        <p class="detail-subtitle">{{ selectedRecharge.order_no }}</p>
        <div class="detail-grid">
          <KyDetailRow
            label="状态"
            :value="rechargeStatusLabel(selectedRecharge.status, selectedRecharge.chain_auto_confirmed)"
          />
          <KyDetailRow label="申请金额" :value="formatUsdt(selectedRecharge.requested_usdt)" />
          <KyDetailRow
            v-if="selectedRecharge.received_usdt"
            label="实收金额"
            :value="formatUsdt(selectedRecharge.received_usdt)"
          />
          <KyDetailRow label="汇率" :value="`1 USDT ≈ ${formatMoney(selectedRecharge.exchange_rate)}`" />
          <KyDetailRow
            v-if="selectedRecharge.credited_cny"
            label="到账金额"
            :value="formatMoney(selectedRecharge.credited_cny)"
          />
          <KyDetailRow label="收款地址" :value="selectedRecharge.receive_address" copyable />
          <KyDetailRow
            v-if="selectedRecharge.from_address"
            label="付款地址"
            :value="selectedRecharge.from_address"
            copyable
          />
          <KyDetailRow v-if="selectedRecharge.txid" label="交易哈希" :value="selectedRecharge.txid" copyable />
          <KyDetailRow label="创建时间" :value="formatDateTime(selectedRecharge.created_at)" />
          <KyDetailRow
            v-if="selectedRecharge.paid_at"
            label="到账时间"
            :value="formatDateTime(selectedRecharge.paid_at)"
          />
          <KyDetailRow
            v-if="selectedRecharge.expired_at"
            label="过期时间"
            :value="formatDateTime(selectedRecharge.expired_at)"
          />
        </div>
        <p v-if="selectedRecharge.status === 'rejected' && selectedRecharge.reject_reason" class="detail-error">
          驳回原因：{{ selectedRecharge.reject_reason }}
        </p>
        <div v-if="selectedRecharge.proof_image_url" class="proof-block">
          <p class="detail-label">转账截图</p>
          <KyImage :src="resolveAssetUrl(selectedRecharge.proof_image_url)" alt="转账截图" />
        </div>
        <div class="detail-actions">
          <KyButton @click="closeRechargeDetail">关闭</KyButton>
          <KyButton
            v-if="['rejected', 'expired', 'cancelled'].includes(selectedRecharge.status)"
            type="primary"
            @click="retryRecharge"
          >
            重新发起充值
          </KyButton>
        </div>
      </template>
    </KyModal>

    <KyModal
      v-model:open="purchaseDetailOpen"
      title="套餐订单详情"
      :show-footer="false"
      @cancel="closePurchaseDetail"
    >
      <template v-if="selectedPurchase">
        <div class="detail-grid">
          <KyDetailRow label="订单号" :value="`#${selectedPurchase.id}`" />
          <KyDetailRow label="状态" :value="orderStatusLabel(selectedPurchase.status)" />
          <KyDetailRow label="支付金额" :value="formatMoney(selectedPurchase.amount)" />
          <KyDetailRow label="支付方式" :value="selectedPurchase.payment_method || '-'" />
          <KyDetailRow label="创建时间" :value="formatDateTime(selectedPurchase.created_at)" />
          <KyDetailRow label="支付时间" :value="formatDateTime(selectedPurchase.paid_at)" />
        </div>
        <div class="detail-actions">
          <KyButton @click="closePurchaseDetail">关闭</KyButton>
        </div>
      </template>
    </KyModal>
  </KySubPage>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import KySubPage from '@/components/KySubPage.vue'
import KyPullRefresh from '@/components/KyPullRefresh.vue'
import KyChipGroup from '@/components/KyChipGroup.vue'
import KyDetailRow from '@/components/KyDetailRow.vue'
import KyOrderCard from '@/components/KyOrderCard.vue'
import { KyButton, KyEmpty, KyImage, KyModal, KySpin, KyTag } from '@/components/ky'
import { clientApi, type OrderItem, type RechargeOrderItem } from '@/api/client'
import {
  formatDateTime,
  formatMoney,
  formatUsdt,
  orderStatusColor,
  orderStatusLabel,
  rechargeStatusColor,
  rechargeStatusLabel,
  resolveAssetUrl,
} from '@/lib/format'
import { parseOrdersTab, type OrdersTab } from '@/lib/support-channels'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const rechargeOrders = ref<RechargeOrderItem[]>([])
const purchaseOrders = ref<OrderItem[]>([])
const selectedRecharge = ref<RechargeOrderItem | null>(null)
const selectedPurchase = ref<OrderItem | null>(null)
const rechargeDetailOpen = ref(false)
const purchaseDetailOpen = ref(false)
const loaded = ref<{ recharge: boolean; purchase: boolean }>({ recharge: false, purchase: false })

const tabItems = [
  { label: '充值', value: 'recharge' },
  { label: '套餐', value: 'purchase' },
]

const tab = computed(() => parseOrdersTab(route.query.tab))

function setTab(value: string | null) {
  const next: OrdersTab = parseOrdersTab(value)
  if (next === tab.value) return
  void router.replace({ name: 'Orders', query: { tab: next } })
}

function openRechargeDetail(item: RechargeOrderItem) {
  selectedRecharge.value = item
  rechargeDetailOpen.value = true
}

function closeRechargeDetail() {
  rechargeDetailOpen.value = false
  selectedRecharge.value = null
}

function retryRecharge() {
  closeRechargeDetail()
  router.push({ name: 'Recharge' })
}

function openPurchaseDetail(item: OrderItem) {
  selectedPurchase.value = item
  purchaseDetailOpen.value = true
}

function closePurchaseDetail() {
  purchaseDetailOpen.value = false
  selectedPurchase.value = null
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'recharge') {
      rechargeOrders.value = (await clientApi.getRechargeOrders()).data.orders
      loaded.value.recharge = true
    } else {
      purchaseOrders.value = (await clientApi.getOrders()).data.orders
      loaded.value.purchase = true
    }
  } finally {
    loading.value = false
  }
}

watch(
  tab,
  async (current) => {
    if (loaded.value[current]) return
    await load()
  },
  { immediate: true },
)
</script>

<style scoped>
.order-list {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-md);
}

.detail-subtitle {
  margin: 0 0 var(--ky-space-md);
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.detail-grid {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-md);
}

.detail-error {
  margin: var(--ky-space-md) 0 0;
  color: var(--ky-danger);
  font-size: var(--ky-font-sm);
}

.proof-block {
  margin-top: var(--ky-space-md);
}

.detail-label {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  margin-bottom: var(--ky-space-sm);
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ky-space-sm);
  margin-top: var(--ky-space-lg);
}
</style>
