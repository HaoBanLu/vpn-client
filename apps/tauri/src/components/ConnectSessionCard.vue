<template>
  <KyCard flat class="session-card">
    <button
      v-if="nodeLabel"
      type="button"
      class="session-card__switch"
      @click="$emit('switch-node')"
    >
      <div class="session-card__switch-copy">
        <span class="session-card__switch-label">当前线路</span>
        <span class="session-card__switch-value">{{ nodeLabel }}</span>
      </div>
      <span class="session-card__switch-action">切换 ›</span>
    </button>

    <div class="session-card__speed">
      <div class="session-card__speed-item">
        <span class="session-card__speed-label">下载</span>
        <strong class="session-card__speed-value">{{ downloadSpeedText }}</strong>
      </div>
      <div class="session-card__divider" />
      <div class="session-card__speed-item">
        <span class="session-card__speed-label">上传</span>
        <strong class="session-card__speed-value">{{ uploadSpeedText }}</strong>
      </div>
    </div>

    <p class="session-card__duration">已连接 {{ durationText }}</p>

    <template v-if="subscriptionLine">
      <div class="session-card__sep" />
      <p class="session-card__meta">{{ subscriptionLine }}</p>
    </template>

    <div class="session-card__sep" />

    <!-- 对齐 Android：默认收起本次隧道流量，减少下方拥挤 -->
    <button type="button" class="session-card__traffic-toggle" @click="trafficExpanded = !trafficExpanded">
      <span>本次隧道流量</span>
      <span class="session-card__chevron" :class="{ open: trafficExpanded }">›</span>
    </button>
    <div v-if="trafficExpanded" class="session-card__traffic-body">
      <div class="session-card__traffic">
        <div>
          <span class="session-card__traffic-label">接收</span>
          <strong>{{ formatBytes(downloadBytes) }}</strong>
        </div>
        <div>
          <span class="session-card__traffic-label">发送</span>
          <strong>{{ formatBytes(uploadBytes) }}</strong>
        </div>
      </div>
      <p class="session-card__traffic-hint">仅统计本次连接，断开重连后重新计数</p>
    </div>
  </KyCard>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import KyCard from '@/components/KyCard.vue'
import { formatBytes } from '@/lib/format'
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

const subscriptionLine = computed(() => {
  const remaining = props.remainingGb
  const expires = props.expiresAt?.slice(0, 10)
  if (remaining != null && expires) return `套餐剩余 ${remaining.toFixed(1)} GB · ${expires} 到期`
  if (remaining != null) return `套餐剩余 ${remaining.toFixed(1)} GB`
  if (expires) return `${expires} 到期`
  return ''
})

const downloadSpeedText = computed(() => formatDisplaySpeed(props.downloadBps))
const uploadSpeedText = computed(() => formatDisplaySpeed(props.uploadBps))
</script>

<style scoped>
/* 样式落在 KyCard 根节点；内容在 __body，用 :deep 控间距 */
.session-card {
  border: 1px solid rgba(74, 222, 128, 0.22);
  border-radius: 22px;
  background: linear-gradient(
    135deg,
    rgba(74, 222, 128, 0.08) 0%,
    rgba(0, 212, 255, 0.05) 100%
  );
}

.session-card :deep(.ky-card__body) {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px;
}

.session-card__switch {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.session-card__switch:hover {
  background: rgba(255, 255, 255, 0.1);
}

.session-card__switch-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-card__switch-label {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.session-card__switch-value {
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
  align-items: stretch;
  gap: var(--ky-space-sm);
  padding: 12px 14px;
  border-radius: var(--ky-radius-md);
  background: rgba(255, 255, 255, 0.04);
}

.session-card__speed-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.session-card__speed-label {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.session-card__speed-value {
  font-size: var(--ky-font-lg);
  font-weight: 650;
  color: var(--ky-text);
}

.session-card__divider {
  width: 1px;
  align-self: stretch;
  background: var(--ky-border-soft);
}

.session-card__duration {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.session-card__meta {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.session-card__sep {
  height: 1px;
  width: 100%;
  background: rgba(255, 255, 255, 0.06);
}

.session-card__traffic-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--ky-text);
  font-size: var(--ky-font-sm);
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.session-card__chevron {
  display: inline-block;
  color: var(--ky-text-muted);
  font-size: 18px;
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
  gap: 8px;
  padding-bottom: 2px;
}

.session-card__traffic {
  display: flex;
  gap: 20px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
}

.session-card__traffic-label {
  display: block;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
  margin-bottom: 2px;
}

.session-card__traffic-hint {
  margin: 0;
  font-size: var(--ky-font-xs);
  line-height: 1.4;
  color: var(--ky-text-muted);
}
</style>
