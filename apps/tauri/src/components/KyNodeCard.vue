<template>
  <div
    class="ky-node-row"
    :class="{
      'ky-node-row--active': isActive,
      'ky-node-row--selected': selected && !isActive,
      'ky-node-row--unsupported': variant === 'unsupported',
    }"
  >
    <div class="ky-node-row__main">
      <div class="ky-node-row__info">
        <div class="ky-node-row__title-line">
          <span class="ky-node-row__name">{{ displayName }}</span>
          <span
            v-if="isActive"
            class="ky-node-row__connected"
            title="已连接"
            aria-label="已连接"
          >
            <svg class="ky-node-row__connected-icon" viewBox="0 0 20 20" aria-hidden="true">
              <circle cx="10" cy="10" r="10" fill="currentColor" />
              <path
                d="M5.8 10.2l2.6 2.6 5.8-5.8"
                fill="none"
                stroke="#fff"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span class="ky-node-row__connected-text">已连接</span>
          </span>
          <span v-else-if="selected" class="ky-node-row__badge">已选</span>
          <span v-else-if="!statusOnline" class="ky-node-row__badge ky-node-row__badge--off">离线</span>
        </div>
        <p v-if="metaLine" class="ky-node-row__meta">{{ metaLine }}</p>
      </div>

      <div v-if="variant === 'connectable'" class="ky-node-row__trail">
        <span
          v-if="hasLatency"
          class="ky-node-row__latency"
          :style="{ color: latencyColorValue }"
        >
          {{ latencyLabel }}
          <span v-if="fastest" class="ky-node-row__fast">
            <svg viewBox="0 0 12 12" aria-hidden="true">
              <path
                d="M6.8 1L3.2 6.6h2.4L5.2 11l4.2-6.2H7.2L6.8 1z"
                fill="currentColor"
              />
            </svg>
            最快
          </span>
        </span>
        <span v-else-if="latencyPending" class="ky-node-row__latency ky-node-row__latency--muted">
          …
        </span>
        <span v-else class="ky-node-row__latency ky-node-row__latency--muted">—</span>

        <button
          v-if="!isActive"
          type="button"
          class="ky-node-row__action"
          :class="{ 'ky-node-row__action--switch': isSwitchAction }"
          :disabled="actionDisabled || actionLoading"
          :aria-busy="actionLoading"
          @click.stop="onActionClick"
        >
          <span v-if="actionLoading" class="ky-node-row__action-spin" aria-hidden="true" />
          <svg
            v-else-if="isSwitchAction"
            class="ky-node-row__action-icon"
            viewBox="0 0 16 16"
            aria-hidden="true"
          >
            <path
              d="M3 5h8.2M9.5 2.8L11.8 5 9.5 7.2M13 11H4.8M6.5 8.8L4.2 11l2.3 2.2"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <svg
            v-else
            class="ky-node-row__action-icon"
            viewBox="0 0 16 16"
            aria-hidden="true"
          >
            <path
              d="M8.2 2.2L5.4 8.2h2.6L7.4 13.8l4-7.4H8.8L8.2 2.2z"
              fill="currentColor"
            />
          </svg>
          <span>{{ actionLoading ? '…' : actionLabel }}</span>
        </button>
      </div>
    </div>

    <p v-if="variant === 'unsupported' && unsupportedText" class="ky-node-row__unsupported">
      {{ unsupportedText }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { NodeItem } from '@/api/client'
import { latencyColor, nodeRegionLabel } from '@/lib/subscription'
import { displaySceneTags, shouldShowRegionLine } from '@/lib/vpn/node-list-display'
import { sanitizeLatencyMs } from '@/lib/vpn/client-latency-probe'

const props = withDefaults(
  defineProps<{
    node: NodeItem
    filterRegion?: string | null
    /** 通讯录分区内：不再重复展示地区名 */
    grouped?: boolean
    variant?: 'connectable' | 'unsupported'
    selected?: boolean
    isActive?: boolean
    latencyMs?: number
    latencyPending?: boolean
    fastest?: boolean
    actionLabel?: string
    actionLoading?: boolean
    actionDisabled?: boolean
    unsupportedText?: string | null
  }>(),
  {
    variant: 'connectable',
    grouped: false,
    selected: false,
    isActive: false,
    filterRegion: null,
    actionLabel: '连接',
    actionLoading: false,
    actionDisabled: false,
    latencyPending: false,
  },
)

const emit = defineEmits<{ action: [] }>()

function onActionClick() {
  if (props.variant !== 'connectable' || props.isActive) return
  if (props.actionDisabled || props.actionLoading) return
  emit('action')
}

const displayName = computed(() => {
  let text = (props.node.name || '').trim()
  if (text.startsWith('@apps/')) text = text.slice(6)
  else if (text.startsWith('@')) text = text.slice(1)
  if (text.toLowerCase().startsWith('apps/')) text = text.slice(text.indexOf('/') + 1)
  return text
})

const regionLabel = computed(() => nodeRegionLabel(props.node.region, props.node.region_name))
const showRegion = computed(() => {
  if (props.grouped) return false
  return shouldShowRegionLine(props.filterRegion, props.node.region)
})
const sceneTags = computed(() => displaySceneTags(props.node.scene_tags, props.filterRegion))

const metaLine = computed(() => {
  const parts: string[] = []
  if (showRegion.value && regionLabel.value) parts.push(regionLabel.value)
  if (props.variant === 'connectable' && sceneTags.value.length > 0) {
    parts.push(sceneTags.value.slice(0, 2).join(' · '))
  }
  return parts.join(' · ')
})

const statusOnline = computed(() => (props.node.status || '').toLowerCase() === 'online')

const latencyLabel = computed(() => {
  const ms = sanitizeLatencyMs(props.latencyMs)
  if (ms != null) return `${ms}ms`
  return ''
})

const hasLatency = computed(() => sanitizeLatencyMs(props.latencyMs) != null)

const latencyColorValue = computed(() => latencyColor(sanitizeLatencyMs(props.latencyMs) || 0))

const isSwitchAction = computed(() => (props.actionLabel || '').includes('切换'))
</script>

<style scoped>
/* 扁平行列表：主信息左、延迟/操作右；已连接用图标一眼可辨 */
.ky-node-row {
  position: relative;
  padding: 12px 0 12px 10px;
  margin: 0;
  border-radius: 0;
  background: transparent;
  transition: background 0.15s ease;
}

.ky-node-row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 3px;
  background: transparent;
  transition: background 0.15s ease;
}

.ky-node-row--active {
  background: rgba(16, 185, 129, 0.06);
}

.ky-node-row--active::before {
  background: var(--ky-success);
}

.ky-node-row--selected {
  background: rgba(59, 130, 246, 0.05);
}

.ky-node-row--selected::before {
  background: var(--ky-accent);
}

.ky-node-row__main {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0;
}

.ky-node-row__info {
  flex: 1;
  min-width: 0;
}

.ky-node-row__title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ky-node-row__name {
  min-width: 0;
  font-size: var(--ky-font-lg);
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ky-node-row--active .ky-node-row__name {
  color: #059669;
}

.ky-node-row__connected {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px 2px 3px;
  border-radius: 999px;
  color: #059669;
  background: rgba(16, 185, 129, 0.12);
}

.ky-node-row__connected-icon {
  width: 14px;
  height: 14px;
  display: block;
  color: #10b981;
}

.ky-node-row__connected-text {
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.01em;
}

.ky-node-row__badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 650;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  color: var(--ky-text-muted);
  background: rgba(148, 163, 184, 0.12);
}

.ky-node-row__badge--off {
  color: var(--ky-text-hint);
  background: rgba(148, 163, 184, 0.1);
}

.ky-node-row__meta {
  margin: 4px 0 0;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.35;
  color: var(--ky-text-hint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ky-node-row__trail {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.ky-node-row__latency {
  min-width: 44px;
  text-align: right;
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.ky-node-row__latency--muted {
  color: var(--ky-text-hint);
  font-weight: 500;
}

.ky-node-row__fast {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  margin-left: 4px;
  font-size: 10px;
  font-weight: 700;
  color: var(--ky-success);
  vertical-align: middle;
}

.ky-node-row__fast svg {
  width: 10px;
  height: 10px;
}

.ky-node-row__action {
  appearance: none;
  border: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 30px;
  min-width: 56px;
  padding: 0 12px;
  border-radius: var(--ky-radius-md);
  background: var(--ky-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.18);
  transition: transform 0.12s ease, opacity 0.12s ease, background 0.12s ease;
}

.ky-node-row__action--switch {
  background: var(--ky-accent-deep);
}

.ky-node-row__action-icon {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.ky-node-row__action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.ky-node-row__action:not(:disabled):active {
  transform: scale(0.97);
}

.ky-node-row__action-spin {
  width: 11px;
  height: 11px;
  border: 1.5px solid rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
  border-radius: 50%;
  animation: ky-node-spin 0.7s linear infinite;
}

@keyframes ky-node-spin {
  to {
    transform: rotate(360deg);
  }
}

.ky-node-row__unsupported {
  margin: 4px 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-danger);
}
</style>
