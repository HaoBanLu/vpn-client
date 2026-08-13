<template>
  <KySubPage title="购买记录">
    <KyPullRefresh :on-refresh="load">
      <KySpin :spinning="loading" overlay>
        <KyEmpty v-if="!loading && orders.length === 0" description="暂无购买记录">
          <KyButton type="primary" size="large" @click="router.push({ name: 'Packages' })">去购买套餐</KyButton>
        </KyEmpty>
        <div v-else class="order-list">
          <KyOrderCard
            v-for="item in orders"
            :key="item.id"
            :order-no="`订单 #${item.id}`"
            :amount="formatMoney(item.amount)"
            :sub="`支付方式：${item.payment_method || '-'}`"
            :time="formatDateTime(item.created_at)"
            @click="openDetail(item)"
          >
            <template #status>
              <KyTag :color="orderStatusColor(item.status)">{{ orderStatusLabel(item.status) }}</KyTag>
            </template>
          </KyOrderCard>
        </div>
      </KySpin>
    </KyPullRefresh>

    <KyModal
      v-model:open="detailOpen"
      title="购买订单详情"
      :show-footer="false"
      @cancel="closeDetail"
    >
      <template v-if="selected">
        <div class="detail-grid">
          <KyDetailRow label="订单号" :value="`#${selected.id}`" />
          <KyDetailRow label="状态" :value="orderStatusLabel(selected.status)" />
          <KyDetailRow label="支付金额" :value="formatMoney(selected.amount)" />
          <KyDetailRow label="支付方式" :value="selected.payment_method || '-'" />
          <KyDetailRow label="创建时间" :value="formatDateTime(selected.created_at)" />
          <KyDetailRow label="支付时间" :value="formatDateTime(selected.paid_at)" />
        </div>
        <div class="detail-actions">
          <KyButton @click="closeDetail">关闭</KyButton>
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
import { KyButton, KyEmpty, KyModal, KySpin, KyTag } from '@/components/ky'
import { clientApi, type OrderItem } from '@/api/client'
import {
  formatDateTime,
  formatMoney,
  orderStatusColor,
  orderStatusLabel,
} from '@/lib/format'

const router = useRouter()
const loading = ref(false)
const orders = ref<OrderItem[]>([])
const selected = ref<OrderItem | null>(null)
const detailOpen = ref(false)

function openDetail(item: OrderItem) {
  selected.value = item
  detailOpen.value = true
}

function closeDetail() {
  detailOpen.value = false
  selected.value = null
}

async function load() {
  loading.value = true
  try {
    orders.value = (await clientApi.getOrders()).data.orders
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

.detail-grid {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-md);
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ky-space-sm);
  margin-top: var(--ky-space-lg);
}
</style>
