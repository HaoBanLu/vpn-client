<template>
  <div
    class="ky-node-row"
    :class="{
      'ky-node-row--active': isActive,
      'ky-node-row--selected': selected && !isActive,
      'ky-node-row--unsupported': variant === 'unsupported',
    }"
  >
    <div class="ky-node-row__top">
      <span class="ky-node-row__name">{{ displayName }}</span>
      <span v-if="isActive" class="ky-node-row__connected">
        <CheckCircleFilled class="ky-node-row__connected-icon" />
        已连接
      </span>
      <span v-else-if="selected" class="ky-node-row__selected-label">已选</span>
      <span
        v-else
        class="ky-pill"
        :class="statusOnline ? 'ky-pill--online' : 'ky-pill--offline'"
      >
        <span class="ky-pill__dot" aria-hidden="true" />
        {{ statusOnline ? '在线' : '离线' }}
      </span>
    </div>

    <p v-if="showRegionLine" class="ky-node-row__meta">地区 {{ regionLabel }}</p>

    <div v-if="variant === 'connectable' && featureTags.length" class="ky-node-row__tags">
      <span
        v-for="tag in featureTags"
        :key="tag.text"
        class="ky-pill"
        :class="tag.kind === 'pool' ? 'ky-pill--cyan' : 'ky-pill--primary'"
      >
        <span class="ky-pill__dot" aria-hidden="true" />
        {{ tag.text }}
      </span>
    </div>

    <div v-if="variant === 'connectable'" class="ky-node-row__bottom">
      <div class="ky-node-row__latency-wrap">
        <span
          v-if="hasLatency"
          class="ky-pill"
          :style="latencyPillStyle"
        >
          <span class="ky-pill__dot" aria-hidden="true" />
          {{ latencyLabel }}
        </span>
        <span v-else-if="latencyPending" class="ky-node-row__latency-plain">测速中…</span>
        <span v-else class="ky-node-row__latency-plain">未测速</span>
        <span v-if="fastest && hasLatency" class="ky-pill ky-pill--fastest">
          <span class="ky-pill__dot" aria-hidden="true" />
          最快
        </span>
      </div>

      <button
        v-if="!isActive"
        type="button"
        class="ky-node-row__action"
        :disabled="actionDisabled || actionLoading"
        @click="$emit('action')"
      >
        <span v-if="actionLoading" class="ky-node-row__action-spin" aria-hidden="true" />
        <SwapOutlined v-else-if="isSwitch" class="ky-node-row__action-icon" />
        <ThunderboltFilled v-else class="ky-node-row__action-icon" />
        <span>{{ actionLoading ? '连接中' : actionLabel }}</span>
      </button>
    </div>

    <p v-if="variant === 'unsupported' && unsupportedText" class="ky-node-row__unsupported">
      {{ unsupportedText }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircleFilled, SwapOutlined, ThunderboltFilled } from '@ant-design/icons-vue'
import type { NodeItem } from '@/api/client'
import { latencyColor, nodeRegionLabel } from '@/lib/subscription'
import { displaySceneTags, shouldShowRegionLine } from '@/lib/vpn/node-list-display'

const props = withDefaults(
  defineProps<{
    node: NodeItem
    filterRegion?: string | null
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
    selected: false,
    isActive: false,
    filterRegion: null,
    actionLabel: '连接',
    actionLoading: false,
    actionDisabled: false,
    latencyPending: false,
  },
)

defineEmits<{ action: [] }>()

/** 对齐 Android displayNodeName：去掉 @apps/ 前缀 */
const displayName = computed(() => {
  let text = (props.node.name || '').trim()
  if (text.startsWith('@apps/')) text = text.slice(6)
  else if (text.startsWith('@')) text = text.slice(1)
  if (text.toLowerCase().startsWith('apps/')) text = text.slice(text.indexOf('/') + 1)
  return text
})

const regionLabel = computed(() => nodeRegionLabel(props.node.region, props.node.region_name))
const showRegionLine = computed(() =>
  shouldShowRegionLine(props.filterRegion, props.node.region),
)

const sceneTags = computed(() => displaySceneTags(props.node.scene_tags, props.filterRegion))

const featureTags = computed(() => {
  if (sceneTags.value.length > 0) {
    return sceneTags.value.map((text) => ({ text, kind: 'scene' as const }))
  }
  const mode = props.node.access_mode?.toLowerCase()
  if (mode === 'relay') return [{ text: '回国专线', kind: 'pool' as const }]
  if (mode === 'direct') return [{ text: '海外直连', kind: 'pool' as const }]
  return []
})

const statusOnline = computed(() => (props.node.status || '').toLowerCase() === 'online')

const latencyLabel = computed(() => {
  if (typeof props.latencyMs === 'number' && props.latencyMs > 0) return `${props.latencyMs}ms`
  if (props.latencyPending) return '测速中…'
  return '未测速'
})

const hasLatency = computed(
  () => typeof props.latencyMs === 'number' && props.latencyMs > 0,
)

const latencyPillStyle = computed(() => {
  const color = latencyColor(props.latencyMs || 0)
  return {
    color,
    background: `${color}26`,
  }
})

const isSwitch = computed(() => props.actionLabel === '切换')
</script>

<style scoped>
.ky-node-row {
  padding: 12px 14px;
  background: transparent;
}

.ky-node-row--active {
  background: rgba(232, 245, 233, 0.92);
}

.ky-node-row--selected {
  background: rgba(232, 238, 248, 0.55);
}

.ky-node-row__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ky-node-row__name {
  flex: 1;
  min-width: 0;
  font-size: var(--ky-font-md);
  font-weight: 650;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ky-node-row__connected {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: #4caf50;
}

.ky-node-row__connected-icon {
  font-size: 16px;
}

.ky-node-row__selected-label {
  flex-shrink: 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-text-muted);
}

.ky-node-row__meta {
  margin: 6px 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ky-node-row__tags {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  margin-top: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.ky-node-row__tags::-webkit-scrollbar {
  display: none;
}

/* 对齐 Android KuayunStatusBadge：色点 + 浅底 + 同色字 */
.ky-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
}

.ky-pill__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.ky-pill--online {
  background: rgba(76, 175, 80, 0.15);
  color: #4caf50;
}

.ky-pill--offline {
  background: var(--ky-surface-variant);
  color: var(--ky-text-muted);
}

.ky-pill--primary {
  background: rgba(27, 77, 255, 0.15);
  color: var(--ky-accent);
}

.ky-pill--cyan {
  background: rgba(0, 212, 255, 0.18);
  color: #0088a8;
}

.ky-pill--fastest {
  background: rgba(46, 125, 50, 0.15);
  color: #2e7d32;
}

.ky-node-row__bottom {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ky-node-row__latency-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

.ky-node-row__latency-plain {
  font-size: 12px;
  color: var(--ky-text-muted);
}

.ky-node-row__action {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-shrink: 0;
  min-height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 999px;
  background: var(--ky-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.ky-node-row__action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.ky-node-row__action-icon {
  font-size: 14px;
}

.ky-node-row__action-spin {
  width: 12px;
  height: 12px;
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
  margin: 8px 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-danger);
}
</style>
