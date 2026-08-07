<template>
  <KyPage sub>
    <PageHeader title="流量统计" subtitle="最近 30 天使用情况" />
    <KySpin :spinning="loading">
      <div v-if="summary" class="metric-row">
        <KyMetricCard label="总流量" :value="`${summary.total_mb.toFixed(1)} MB`" highlight />
        <KyMetricCard label="上传" :value="`${summary.total_up_mb.toFixed(1)} MB`" />
        <KyMetricCard label="下载" :value="`${summary.total_down_mb.toFixed(1)} MB`" />
      </div>

      <KyEmpty v-if="!loading && daily.length === 0" description="暂无每日流量记录" />
      <KyCard v-else flat>
        <KyListItem
          v-for="item in daily"
          :key="item.date"
          :title="item.date"
          :subtitle="`合计 ${item.total_mb.toFixed(2)} MB · ↑ ${item.total_up_mb.toFixed(2)} / ↓ ${item.total_down_mb.toFixed(2)}`"
        />
      </KyCard>
    </KySpin>
  </KyPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyMetricCard from '@/components/KyMetricCard.vue'
import KyListItem from '@/components/KyListItem.vue'
import PageHeader from '@/components/PageHeader.vue'
import { KyEmpty, KySpin } from '@/components/ky'
import { clientApi, type DailyTrafficItem, type TrafficSummary } from '@/api/client'

const loading = ref(false)
const summary = ref<TrafficSummary | null>(null)
const daily = ref<DailyTrafficItem[]>([])

async function load() {
  loading.value = true
  try {
    const [summaryRes, dailyRes] = await Promise.all([
      clientApi.getTrafficSummary(),
      clientApi.getTrafficDaily(),
    ])
    summary.value = summaryRes.data
    daily.value = dailyRes.data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.metric-row {
  display: flex;
  gap: var(--ky-space-sm);
  margin-bottom: var(--ky-space-lg);
}
</style>
