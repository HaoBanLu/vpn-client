<template>
  <div class="ky-package-card" :class="{ 'ky-package-card--tint': highlight }">
    <div class="ky-package-card__head">
      <span class="ky-package-card__name">{{ name }}</span>
      <StatusBadge v-if="badgeText" :text="badgeText" :variant="badgeVariant" :dot="false" />
    </div>
    <div class="ky-package-card__price-row">
      <p class="ky-package-card__price">{{ price }}</p>
      <KyButton
        :type="buttonType"
        size="small"
        class="ky-package-card__action"
        :loading="loading"
        :disabled="disabled"
        @click="$emit('action')"
      >
        {{ actionLabel }}
      </KyButton>
    </div>
    <p v-if="description" class="ky-package-card__desc">{{ description }}</p>
    <div class="ky-package-card__chips">
      <div class="ky-package-card__stat">
        <span class="ky-package-card__stat-label">时长</span>
        <span class="ky-package-card__stat-value">{{ durationDays }} 天</span>
      </div>
      <div class="ky-package-card__stat ky-package-card__stat--hl">
        <span class="ky-package-card__stat-label">流量</span>
        <span class="ky-package-card__stat-value">{{ trafficGb }} GB</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { KyButton } from '@/components/ky'
import type { StatusBadgeVariant } from '@/components/StatusBadge.vue'

const props = withDefaults(
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

/** 续费 / 购买 / 升级用实心主色；更换与余额不足用浅底，降低蓝块面积 */
const buttonType = computed(() =>
  /续费|立即购买|升级|开通/.test(props.actionLabel) ? 'primary' : 'default',
)
</script>

<style scoped>
.ky-package-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(232, 238, 248, 0.55);
  border: 1px solid var(--ky-border-soft);
}

.ky-package-card--tint {
  background: rgba(214, 228, 255, 0.55);
  border-color: rgba(27, 77, 255, 0.12);
}

.ky-package-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-sm);
}

.ky-package-card__name {
  font-size: var(--ky-font-md);
  font-weight: 700;
  color: var(--ky-text);
  line-height: 1.35;
}

.ky-package-card__price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.ky-package-card__price {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.15;
  color: var(--ky-accent);
  min-width: 0;
}

.ky-package-card__desc {
  margin: 0;
  font-size: var(--ky-font-sm);
  line-height: 1.5;
  color: var(--ky-text-muted);
}

.ky-package-card__chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.ky-package-card__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 88px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
}

.ky-package-card__stat--hl {
  background: rgba(27, 77, 255, 0.1);
}

.ky-package-card__stat-label {
  font-size: 11px;
  color: var(--ky-text-muted);
}

.ky-package-card__stat-value {
  font-size: var(--ky-font-sm);
  font-weight: 700;
  color: var(--ky-text);
}

.ky-package-card__stat--hl .ky-package-card__stat-value {
  color: var(--ky-accent);
}

.ky-package-card__action {
  flex-shrink: 0;
  min-width: 88px;
  font-weight: 600;
  align-self: center;
}
</style>
