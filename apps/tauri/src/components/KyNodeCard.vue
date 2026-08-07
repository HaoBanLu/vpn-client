<template>
  <KyCard :highlight="highlight" :flat="variant === 'unsupported'">
    <div class="ky-node-card">
      <div class="ky-node-card__main">
        <div class="ky-node-card__body">
          <div class="ky-node-card__head">
            <span class="ky-node-card__name">{{ node.name }}</span>
            <StatusBadge :text="statusBadge.text" :variant="statusBadge.variant" />
          </div>
          <!-- 对齐 Android：不展示协议；已筛地区时隐藏重复地区行 -->
          <p v-if="showRegionLine" class="ky-node-card__meta">地区 {{ regionLabel }}</p>
          <div v-if="variant === 'connectable' && sceneTags.length" class="ky-node-card__tags">
            <span v-for="tag in sceneTags" :key="tag" class="ky-node-card__tag">{{ tag }}</span>
          </div>
          <span
            v-if="variant === 'connectable' && latencyMs !== undefined"
            class="ky-node-card__latency"
            :style="{ color: latencyColor(latencyMs) }"
          >
            {{ latencyMs }}ms
          </span>
          <p v-if="variant === 'unsupported' && unsupportedText" class="ky-node-card__unsupported">
            {{ unsupportedText }}
          </p>
          <p
            v-if="variant === 'connectable' && selected && selectedStatusText"
            class="ky-node-card__selected"
          >
            {{ selectedStatusText }}
          </p>
        </div>
        <div
          v-if="variant === 'connectable' && !(selected && selectedStatusText)"
          class="ky-node-card__trail"
        >
          <KyButton
            size="small"
            class="ky-node-card__action"
            :loading="actionLoading"
            :disabled="actionDisabled"
            @click="$emit('action')"
          >
            {{ actionLabel }}
          </KyButton>
        </div>
      </div>
    </div>
  </KyCard>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import KyCard from '@/components/KyCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { KyButton } from '@/components/ky'
import type { NodeItem } from '@/api/client'
import { latencyColor, nodeRegionLabel } from '@/lib/subscription'
import { displaySceneTags, shouldShowRegionLine } from '@/lib/vpn/node-list-display'

const props = withDefaults(
  defineProps<{
    node: NodeItem
    /** 当前地区筛选，对齐 Android filterRegion */
    filterRegion?: string | null
    variant?: 'connectable' | 'unsupported'
    highlight?: boolean
    selected?: boolean
    selectedStatusText?: string | null
    latencyMs?: number
    actionLabel?: string
    actionLoading?: boolean
    actionDisabled?: boolean
    unsupportedText?: string | null
  }>(),
  {
    variant: 'connectable',
    highlight: false,
    selected: false,
    filterRegion: null,
    actionLabel: '使用此节点',
    actionLoading: false,
    actionDisabled: false,
  },
)

defineEmits<{ action: [] }>()

const regionLabel = computed(() => nodeRegionLabel(props.node.region, props.node.region_name))
const showRegionLine = computed(() =>
  shouldShowRegionLine(props.filterRegion, props.node.region),
)
const sceneTags = computed(() => displaySceneTags(props.node.scene_tags, props.filterRegion))

const statusBadge = computed(() => {
  if (props.variant === 'unsupported') {
    return { text: 'App 不可用', variant: 'error' as const }
  }
  return {
    text: props.node.status === 'online' ? '在线' : '离线',
    variant: (props.node.status === 'online' ? 'online' : 'offline') as 'online' | 'offline',
  }
})
</script>

<style scoped>
.ky-node-card {
  display: flex;
  flex-direction: column;
}

.ky-node-card__main {
  display: flex;
  align-items: center;
  gap: var(--ky-space-md);
}

.ky-node-card__body {
  flex: 1;
  min-width: 0;
}

.ky-node-card__trail {
  flex-shrink: 0;
  align-self: center;
}

.ky-node-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ky-space-sm);
}

.ky-node-card__name {
  flex: 1;
  min-width: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
  word-break: break-word;
}

.ky-node-card__meta {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ky-node-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: var(--ky-space-sm);
}

.ky-node-card__tag {
  padding: 2px 8px;
  border-radius: var(--ky-radius-sm);
  font-size: var(--ky-font-xs);
  color: var(--ky-accent);
  background: rgba(56, 189, 248, 0.12);
}

.ky-node-card__latency {
  display: inline-block;
  margin-top: var(--ky-space-sm);
  padding: 2px 8px;
  border-radius: var(--ky-radius-sm);
  font-size: var(--ky-font-xs);
  font-weight: 600;
  background: rgba(255, 255, 255, 0.06);
}

.ky-node-card__unsupported {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-danger);
}

.ky-node-card__action {
  min-width: 4.5em;
  min-height: 32px;
  padding: 0 12px;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  white-space: nowrap;
}

.ky-node-card__selected {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-accent);
}
</style>
