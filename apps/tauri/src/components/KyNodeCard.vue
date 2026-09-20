<template>
  <div
    class="ky-node-row"
    :class="{
      'ky-node-row--active': isActive,
      'ky-node-row--selected': selected && !isActive,
      'ky-node-row--unsupported': variant === 'unsupported',
      'ky-node-row--clickable': variant === 'connectable' && !isActive,
    }"
    role="button"
    :tabindex="variant === 'connectable' && !isActive ? 0 : undefined"
    @click="onRowActivate"
    @keydown.enter.prevent="onRowActivate"
  >
    <div class="ky-node-row__main">
      <div class="ky-node-row__info">
        <div class="ky-node-row__title-line">
          <span class="ky-node-row__name">{{ displayName }}</span>
          <span v-if="isActive" class="ky-node-row__badge ky-node-row__badge--ok">已连接</span>
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
          <span v-if="fastest" class="ky-node-row__fast">最快</span>
        </span>
        <span v-else-if="latencyPending" class="ky-node-row__latency ky-node-row__latency--muted">
          …
        </span>
        <span v-else class="ky-node-row__latency ky-node-row__latency--muted">—</span>

        <span v-if="!isActive" class="ky-node-row__action" :aria-busy="actionLoading">
          <span v-if="actionLoading" class="ky-node-row__action-spin" aria-hidden="true" />
          <span>{{ actionLoading ? '…' : actionLabel }}</span>
        </span>
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

function onRowActivate() {
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
  if (typeof props.latencyMs === 'number' && props.latencyMs > 0) return `${props.latencyMs}ms`
  return ''
})

const hasLatency = computed(
  () => typeof props.latencyMs === 'number' && props.latencyMs > 0,
)

const latencyColorValue = computed(() => latencyColor(props.latencyMs || 0))
</script>

<style scoped>
.ky-node-row {
  padding: 12px 14px;
  background: transparent;
}

.ky-node-row--clickable {
  cursor: pointer;
}

.ky-node-row--clickable:active {
  background: rgba(241, 245, 249, 0.9);
}

.ky-node-row--active {
  background: rgba(16, 185, 129, 0.08);
}

.ky-node-row--selected {
  background: var(--ky-nav-active-pill);
}

.ky-node-row__main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ky-node-row__info {
  flex: 1;
  min-width: 0;
}

.ky-node-row__title-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.ky-node-row__name {
  min-width: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ky-node-row__badge {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--ky-text-muted);
}

.ky-node-row__badge--ok {
  color: var(--ky-success);
}

.ky-node-row__badge--off {
  color: var(--ky-text-hint);
}

.ky-node-row__meta {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.35;
  color: var(--ky-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ky-node-row__trail {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ky-node-row__latency {
  min-width: 44px;
  text-align: right;
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.ky-node-row__latency--muted {
  color: var(--ky-text-hint);
  font-weight: 500;
}

.ky-node-row__fast {
  margin-left: 2px;
  font-size: 10px;
  font-weight: 700;
  color: var(--ky-success);
}

.ky-node-row__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  min-width: 48px;
  padding: 0 11px;
  border-radius: 999px;
  background: var(--ky-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 650;
  pointer-events: none;
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
  margin: 6px 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-danger);
}
</style>
