<template>
  <KySubPage title="充值记录">
    <KyPullRefresh :on-refresh="load">
      <KySpin :spinning="loading" overlay>
        <KyEmpty v-if="!loading && orders.length === 0" description="暂无充值记录">
          <KyButton type="primary" size="large" @click="router.push({ name: 'Recharge' })">去充值</KyButton>
        </KyEmpty>
        <div v-else class="order-list">
          <KyOrderCard
            v-for="item in orders"
            :key="item.id"
            :order-no="item.order_no"
            :amount="formatUsdt(item.requested_usdt)"
            :sub="item.credited_cny ? `到账约 ${formatMoney(item.credited_cny)}` : undefined"
            :time="formatDateTime(item.created_at)"
            :error="item.status === 'rejected' && item.reject_reason ? `驳回：${item.reject_reason}` : undefined"
            @click="openDetail(item)"
          >
            <template #status>
              <KyTag :color="rechargeStatusColor(item.status)">{{ rechargeStatusLabel(item.status, item.chain_auto_confirmed) }}</KyTag>
            </template>
          </KyOrderCard>
        </div>
      </KySpin>
    </KyPullRefresh>

    <KyModal
      v-model:open="detailOpen"
      title="充值订单详情"
      :show-footer="false"
      @cancel="closeDetail"
    >
      <template v-if="selected">
        <p class="detail-subtitle">{{ selected.order_no }}</p>
        <div class="detail-grid">
          <KyDetailRow label="状态" :value="rechargeStatusLabel(selected.status, selected.chain_auto_confirmed)" />
          <KyDetailRow label="申请金额" :value="formatUsdt(selected.requested_usdt)" />
          <KyDetailRow v-if="selected.received_usdt" label="实收金额" :value="formatUsdt(selected.received_usdt)" />
          <KyDetailRow label="汇率" :value="`1 USDT ≈ ${formatMoney(selected.exchange_rate)}`" />
          <KyDetailRow v-if="selected.credited_cny" label="到账金额" :value="formatMoney(selected.credited_cny)" />
          <KyDetailRow label="收款地址" :value="selected.receive_address" copyable />
          <KyDetailRow v-if="selected.from_address" label="付款地址" :value="selected.from_address" copyable />
          <KyDetailRow v-if="selected.txid" label="交易哈希" :value="selected.txid" copyable />
          <KyDetailRow label="创建时间" :value="formatDateTime(selected.created_at)" />
          <KyDetailRow v-if="selected.paid_at" label="到账时间" :value="formatDateTime(selected.paid_at)" />
          <KyDetailRow v-if="selected.expired_at" label="过期时间" :value="formatDateTime(selected.expired_at)" />
        </div>
        <p v-if="selected.status === 'rejected' && selected.reject_reason" class="detail-error">
          驳回原因：{{ selected.reject_reason }}
        </p>
        <div v-if="selected.proof_image_url" class="proof-block">
          <p class="detail-label">转账截图</p>
          <KyImage :src="resolveAssetUrl(selected.proof_image_url)" alt="转账截图" />
        </div>
        <div class="detail-actions">
          <KyButton @click="closeDetail">关闭</KyButton>
          <KyButton
            v-if="['rejected', 'expired', 'cancelled'].includes(selected.status)"
            type="primary"
            @click="retryRecharge"
          >
            重新发起充值
          </KyButton>
        </div>
      </template>
    </KyModal>
  </KySubPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import KySubPage from '@/components/KySubPage.vue'
import KyPullRefresh from '@/components/KyPullRefresh.vue'
import KyDetailRow from '@/components/KyDetailRow.vue'
import KyOrderCard from '@/components/KyOrderCard.vue'
import { KyButton, KyEmpty, KyImage, KyModal, KySpin, KyTag } from '@/components/ky'
import { clientApi, type RechargeOrderItem } from '@/api/client'
import {
  formatDateTime,
  formatMoney,
  formatUsdt,
  rechargeStatusColor,
  rechargeStatusLabel,
  resolveAssetUrl,
} from '@/lib/format'

const router = useRouter()
const loading = ref(false)
const orders = ref<RechargeOrderItem[]>([])
const selected = ref<RechargeOrderItem | null>(null)
const detailOpen = ref(false)

function openDetail(item: RechargeOrderItem) {
  selected.value = item
  detailOpen.value = true
}

function closeDetail() {
  detailOpen.value = false
  selected.value = null
}

function retryRecharge() {
  closeDetail()
  router.push({ name: 'Recharge' })
}

async function load() {
  loading.value = true
  try {
    orders.value = (await clientApi.getRechargeOrders()).data.orders
  } finally {
    loading.value = false
  }
}

onMounted(load)
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
