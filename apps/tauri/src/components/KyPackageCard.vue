<template>
  <div
    class="ky-package-card"
    :class="{
      'ky-package-card--recommend': isRecommend,
      'ky-package-card--current': isCurrent,
    }"
  >
    <div v-if="badgeText" class="ky-package-card__badge">
      <StatusBadge :text="badgeText" :variant="badgeVariant" :dot="false" />
    </div>

    <div class="ky-package-card__title">【{{ name }}】</div>

    <div class="ky-package-card__price-block">
      <div class="ky-package-card__price-line">
        <span class="ky-package-card__currency">¥</span>
        <span class="ky-package-card__amount">{{ priceAmount }}</span>
        <span v-if="cycleSuffix" class="ky-package-card__suffix">{{ cycleSuffix }}</span>
      </div>
      <p v-if="monthlyHint" class="ky-package-card__monthly">{{ monthlyHint }}</p>
    </div>

    <ul class="ky-package-card__features">
      <li v-for="line in featureLines" :key="line" class="ky-package-card__feature">
        <span class="ky-package-card__check" aria-hidden="true">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </span>
        <span class="ky-package-card__feature-text">{{ line }}</span>
      </li>
    </ul>

    <KyButton
      :type="buttonType"
      block
      class="ky-package-card__action"
      :class="{ 'ky-package-card__action--recommend': isRecommend }"
      :loading="loading"
      :disabled="disabled"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </KyButton>
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
    /** 已格式化金额，如 ¥119.99 */
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

const isRecommend = computed(() => props.badgeVariant === 'recommend' || props.badgeText === '推荐')
const isCurrent = computed(() => props.highlight || props.badgeText === '当前套餐')

const priceAmount = computed(() => props.price.replace(/^¥\s*/, '').trim() || '0.00')

const priceNumber = computed(() => {
  const n = Number(priceAmount.value.replace(/,/g, ''))
  return Number.isFinite(n) ? n : 0
})

const cycleSuffix = computed(() => {
  const days = props.durationDays
  if (days <= 0) return ''
  if (days === 30) return '/月'
  if (days === 90) return '/季'
  if (days === 365 || days === 366) return '/年'
  if (days === 730) return '/两年'
  if (days === 1095) return '/三年'
  return `/${days}天`
})

const monthlyHint = computed(() => {
  const days = props.durationDays
  if (days <= 30 || priceNumber.value <= 0) return null
  const months = days / 30
  if (months <= 1.05) return null
  const avg = priceNumber.value / months
  return `月均 ¥${avg.toFixed(2)} · 有效期 ${days} 天`
})

const featureLines = computed(() => {
  const lines: string[] = []
  const days = props.durationDays
  const gb = props.trafficGb

  if (days <= 0) {
    lines.push(`${gb} GB 总流量`)
    lines.push('一次性购买 · 永久有效')
  } else if (days > 30) {
    const months = days / 30
    const monthly = Math.round((gb / months) * 100) / 100
    lines.push(`${monthly} GB 流量/月（合计 ${gb} GB）`)
    if (days === 90) lines.push('季付（90 天）')
    else if (days === 365 || days === 366) lines.push('年付（365 天）')
    else lines.push(`有效期 ${days} 天`)
  } else {
    lines.push(`${gb} GB 流量`)
    lines.push(`有效期 ${days} 天`)
  }

  if (props.description) {
    const descLines = props.description
      .split(/[\n；;|]/)
      .map((s) => s.trim())
      .filter(Boolean)
    for (const line of descLines.slice(0, 3)) {
      if (!lines.includes(line)) lines.push(line)
    }
  }

  return lines
})

/** 续费 / 购买 / 升级用实心主色；更换与余额不足用浅底 */
const buttonType = computed(() =>
  /续费|立即购买|升级|开通/.test(props.actionLabel) ? 'primary' : 'default',
)
</script>

<style scoped>
.ky-package-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 18px 16px 16px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--ky-bg-card) 88%, transparent);
  border: 2px solid rgba(226, 232, 240, 0.95);
  box-shadow: 0 8px 30px rgb(0 0 0 / 0.04);
  backdrop-filter: blur(8px);
  overflow: hidden;
}

.ky-package-card--recommend {
  border-color: color-mix(in srgb, var(--ky-accent) 55%, #93c5fd);
}

.ky-package-card--current:not(.ky-package-card--recommend) {
  background: var(--ky-nav-active-pill);
  border-color: rgba(59, 130, 246, 0.22);
}

.ky-package-card__badge {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
}

.ky-package-card__title {
  padding-right: 72px;
  font-size: 17px;
  font-weight: 700;
  color: var(--ky-text);
  line-height: 1.35;
}

.ky-package-card__price-block {
  margin-top: 14px;
}

.ky-package-card__price-line {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  min-width: 0;
}

.ky-package-card__currency {
  padding-bottom: 4px;
  font-size: 13px;
  font-weight: 650;
  color: var(--ky-text-muted);
}

.ky-package-card__amount {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.05;
  color: var(--ky-text);
}

.ky-package-card__suffix {
  padding-bottom: 5px;
  margin-left: 2px;
  font-size: 13px;
  color: var(--ky-text-muted);
}

.ky-package-card__monthly {
  margin: 6px 0 0;
  font-size: 12px;
  font-weight: 650;
  color: var(--ky-success);
  line-height: 1.4;
}

.ky-package-card__features {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.ky-package-card__feature {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.ky-package-card__check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 999px;
  background: var(--ky-accent-bg);
  color: var(--ky-accent);
}

.ky-package-card__feature-text {
  font-size: var(--ky-font-sm);
  line-height: 1.45;
  color: var(--ky-text-secondary, var(--ky-text-muted));
}

.ky-package-card__action {
  margin-top: 18px;
  font-weight: 650;
  border-radius: 12px !important;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--ky-accent) 22%, transparent);
}

.ky-package-card__action--recommend {
  background: linear-gradient(90deg, #8b5cf6 0%, var(--ky-accent) 55%, var(--ky-accent) 100%) !important;
  border-color: transparent !important;
  box-shadow: 0 8px 18px color-mix(in srgb, var(--ky-accent) 28%, transparent);
}
</style>
