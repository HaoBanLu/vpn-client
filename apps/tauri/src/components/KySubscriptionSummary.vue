<template>
  <KyCard highlight>
    <div class="ky-subscription-summary">
      <div class="ky-subscription-summary__head">
        <div>
          <p class="ky-subscription-summary__label">{{ label }}</p>
          <p class="ky-subscription-summary__name">{{ packageName }}</p>
        </div>
        <StatusBadge v-if="statusText" :text="statusText" :variant="statusVariant" />
      </div>
      <div v-if="showProgress" class="ky-subscription-summary__progress">
        <div class="ky-subscription-summary__bar">
          <div class="ky-subscription-summary__fill" :style="{ width: `${progressPercent}%` }" />
        </div>
        <p class="ky-subscription-summary__usage">{{ usageText }}</p>
      </div>
    </div>
  </KyCard>
</template>

<script setup lang="ts">
import KyCard from '@/components/KyCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { StatusBadgeVariant } from '@/components/StatusBadge.vue'

withDefaults(
  defineProps<{
    packageName: string
    label?: string
    statusText?: string | null
    statusVariant?: StatusBadgeVariant
    progressPercent?: number
    usageText?: string
    showProgress?: boolean
  }>(),
  {
    label: '我使用的套餐',
    statusVariant: 'success',
    progressPercent: 0,
    showProgress: true,
  },
)
</script>

<style scoped>
.ky-subscription-summary__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--ky-space-sm);
}

.ky-subscription-summary__label {
  margin: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.ky-subscription-summary__name {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-lg);
  font-weight: 700;
  color: var(--ky-text);
}

.ky-subscription-summary__progress {
  margin-top: var(--ky-space-md);
}

.ky-subscription-summary__bar {
  height: 6px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.ky-subscription-summary__fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--ky-accent-deep), var(--ky-accent));
}

.ky-subscription-summary__usage {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}
</style>
