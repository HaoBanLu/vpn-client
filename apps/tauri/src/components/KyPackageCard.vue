<template>
  <KyCard :highlight="highlight">
    <div class="ky-package-card">
      <div class="ky-package-card__head">
        <span class="ky-package-card__name">{{ name }}</span>
        <StatusBadge v-if="badgeText" :text="badgeText" :variant="badgeVariant" />
      </div>
      <p class="ky-package-card__price">{{ price }}</p>
      <p v-if="description" class="ky-package-card__desc">{{ description }}</p>
      <div class="ky-package-card__chips">
        <span class="ky-package-card__chip">时长 {{ durationDays }} 天</span>
        <span class="ky-package-card__chip ky-package-card__chip--highlight">流量 {{ trafficGb }} GB</span>
      </div>
      <KyButton
        type="primary"
        block
        class="ky-package-card__action"
        :loading="loading"
        :disabled="disabled"
        @click="$emit('action')"
      >
        {{ actionLabel }}
      </KyButton>
    </div>
  </KyCard>
</template>

<script setup lang="ts">
import KyCard from '@/components/KyCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { KyButton } from '@/components/ky'
import type { StatusBadgeVariant } from '@/components/StatusBadge.vue'

withDefaults(
  defineProps<{
    name: string
    price: string
    durationDays: number
    trafficGb: number
    actionLabel: string
    description?: string | null
    badgeText?: string | null
    badgeVariant?: StatusBadgeVariant
    highlight?: boolean
    loading?: boolean
    disabled?: boolean
  }>(),
  { badgeVariant: 'success', highlight: false, loading: false, disabled: false },
)

defineEmits<{ action: [] }>()
</script>

<style scoped>
.ky-package-card {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-xs);
  min-height: 100%;
}

.ky-package-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-sm);
}

.ky-package-card__name {
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
  line-height: 1.35;
}

.ky-package-card__price {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-xl);
  font-weight: 700;
  line-height: 1.2;
  color: var(--ky-accent);
}

.ky-package-card__desc {
  margin: 0;
  font-size: var(--ky-font-sm);
  line-height: 1.5;
  color: var(--ky-text-muted);
}

.ky-package-card__chips {
  display: flex;
  gap: var(--ky-space-sm);
  flex-wrap: wrap;
  margin-top: var(--ky-space-sm);
}

.ky-package-card__chip {
  padding: 4px 10px;
  border-radius: var(--ky-radius-sm);
  font-size: var(--ky-font-xs);
  background: rgba(255, 255, 255, 0.06);
  color: var(--ky-text-muted);
}

.ky-package-card__chip--highlight {
  background: var(--ky-accent-bg);
  color: var(--ky-accent);
}

.ky-package-card__action {
  margin-top: var(--ky-space-md);
  height: 40px;
  font-size: var(--ky-font-sm);
  font-weight: 600;
}
</style>
