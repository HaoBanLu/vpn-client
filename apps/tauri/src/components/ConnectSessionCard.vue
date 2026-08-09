<template>
  <KyCard flat class="session-card">
    <button
      v-if="nodeLabel"
      type="button"
      class="session-card__switch"
      @click="$emit('switch-node')"
    >
      <span class="session-card__switch-value">{{ nodeLabel }}</span>
      <span class="session-card__switch-action">切换 ›</span>
    </button>

    <!-- 对齐 Android CompactSpeedAndDurationRow：下载 | 上传 | 时长 同行 -->
    <div class="session-card__speed">
      <div class="session-card__speed-item">
        <div class="session-card__speed-head">
          <span class="session-card__speed-dot session-card__speed-dot--down" />
          <span class="session-card__speed-label">下载</span>
        </div>
        <strong
          class="session-card__speed-value"
          :class="{ idle: downloadBps <= 0 }"
        >{{ downloadSpeedText }}</strong>
      </div>
      <div class="session-card__divider" />
      <div class="session-card__speed-item">
        <div class="session-card__speed-head">
          <span class="session-card__speed-dot session-card__speed-dot--up" />
          <span class="session-card__speed-label">上传</span>
        </div>
        <strong
          class="session-card__speed-value"
          :class="{ idle: uploadBps <= 0 }"
        >{{ uploadSpeedText }}</strong>
      </div>
      <div class="session-card__divider" />
      <div class="session-card__duration">
        <span class="session-card__duration-icon" aria-hidden="true">⏱</span>
        <strong>{{ durationText }}</strong>
      </div>
    </div>

    <p v-if="subscriptionLine" class="session-card__meta">{{ subscriptionLine }}</p>

    <!-- 对齐 Android：默认收起本次隧道流量 -->
    <button type="button" class="session-card__traffic-toggle" @click="trafficExpanded = !trafficExpanded">
      <span>本次隧道流量</span>
      <span class="session-card__chevron" :class="{ open: trafficExpanded }">›</span>
    </button>
    <div v-if="trafficExpanded" class="session-card__traffic-body">
      <div class="session-card__traffic">
        <div>
          <span class="session-card__traffic-label">接收</span>
          <strong class="session-card__traffic-down">{{ formatSessionBytes(downloadBytes) }}</strong>
        </div>
        <div>
          <span class="session-card__traffic-label">发送</span>
          <strong class="session-card__traffic-up">{{ formatSessionBytes(uploadBytes) }}</strong>
        </div>
      </div>
      <p class="session-card__traffic-hint">仅统计本次连接，断开重连后重新计数</p>
    </div>
  </KyCard>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import KyCard from '@/components/KyCard.vue'
import { displayNodeLabel } from '@/lib/connect-hero'
import { formatDisplaySpeed, formatSessionDuration } from '@/lib/vpn/session-throughput'

const props = defineProps<{
  downloadBytes: number
  uploadBytes: number
  durationMs: number
  downloadBps: number
  uploadBps: number
  remainingGb?: number | null
  expiresAt?: string | null
  selectedNode?: string | null
}>()

defineEmits<{ 'switch-node': [] }>()

const trafficExpanded = ref(false)

const nodeLabel = computed(() => displayNodeLabel(props.selectedNode))

const durationText = computed(() =>
  props.durationMs > 0 ? formatSessionDuration(props.durationMs) : '00:00',
)

/** 对齐 Android ConnectNodeDetailCard：剩余 X.X GB · yyyy-mm-dd 到期 */
const subscriptionLine = computed(() => {
  const remaining = props.remainingGb
  const expires = props.expiresAt?.slice(0, 10)
  if (remaining != null && expires) return `剩余 ${remaining.toFixed(1)} GB · ${expires} 到期`
  if (remaining != null) return `剩余 ${remaining.toFixed(1)} GB`
  if (expires) return `${expires} 到期`
  return ''
})

const downloadSpeedText = computed(() => formatDisplaySpeed(props.downloadBps))
const uploadSpeedText = computed(() => formatDisplaySpeed(props.uploadBps))

/** 对齐 Android VpnSessionStatsTracker.formatBytes（MB 一位小数） */
function formatSessionBytes(bytes: number): string {
  const n = Math.max(0, bytes)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<style scoped>
.session-card {
  border-radius: 18px;
  background: var(--ky-bg-card);
  border: 1px solid var(--ky-border-soft);
  box-shadow: var(--ky-shadow-sm);
}

.session-card :deep(.ky-card__body) {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
}

.session-card__switch {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: rgba(15, 23, 41, 0.04);
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.session-card__switch:hover {
  background: rgba(15, 23, 41, 0.07);
}

.session-card__switch-value {
  flex: 1;
  min-width: 0;
  font-size: var(--ky-font-md);
  font-weight: 650;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-card__switch-action {
  flex-shrink: 0;
  font-size: var(--ky-font-sm);
  font-weight: 650;
  color: var(--ky-accent);
}

.session-card__speed {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(15, 23, 41, 0.04);
}

.session-card__speed-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.session-card__speed-head {
  display: flex;
  align-items: center;
  gap: 3px;
}

.session-card__speed-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.session-card__speed-dot::after {
  content: '';
  width: 0;
  height: 0;
  border-left: 3.5px solid transparent;
  border-right: 3.5px solid transparent;
}

.session-card__speed-dot--down {
  background: rgba(74, 222, 128, 0.12);
}

.session-card__speed-dot--down::after {
  border-top: 5px solid #4ade80;
  margin-top: 2px;
}

.session-card__speed-dot--up {
  background: rgba(37, 99, 235, 0.12);
}

.session-card__speed-dot--up::after {
  border-bottom: 5px solid #2563eb;
  margin-bottom: 2px;
}

.session-card__speed-label {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.session-card__speed-value {
  font-size: var(--ky-font-md);
  font-weight: 650;
  color: var(--ky-text);
}

.session-card__speed-value.idle {
  color: var(--ky-text-muted);
}

.session-card__divider {
  width: 1px;
  height: 32px;
  flex-shrink: 0;
  background: rgba(15, 23, 41, 0.08);
}

.session-card__duration {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-left: 4px;
  flex-shrink: 0;
}

.session-card__duration-icon {
  font-size: 12px;
  opacity: 0.65;
  line-height: 1;
}

.session-card__duration strong {
  font-size: var(--ky-font-sm);
  font-weight: 650;
  color: var(--ky-text);
  font-variant-numeric: tabular-nums;
}

.session-card__meta {
  margin: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.session-card__traffic-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--ky-text);
  font-size: var(--ky-font-xs);
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.session-card__chevron {
  display: inline-block;
  color: var(--ky-text-muted);
  font-size: 16px;
  line-height: 1;
  transform: rotate(0deg);
  transition: transform 0.15s ease;
}

.session-card__chevron.open {
  transform: rotate(90deg);
}

.session-card__traffic-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 2px;
}

.session-card__traffic {
  display: flex;
  gap: 20px;
}

.session-card__traffic-label {
  display: block;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  margin-bottom: 2px;
}

.session-card__traffic-down {
  color: #4ade80;
  font-size: var(--ky-font-md);
}

.session-card__traffic-up {
  color: #60a5fa;
  font-size: var(--ky-font-md);
}

.session-card__traffic-hint {
  margin: 0;
  font-size: 11px;
  line-height: 1.4;
  color: var(--ky-text-muted);
}
</style>
