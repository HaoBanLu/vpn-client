<template>
  <KyCard flat class="quick-status">
    <p v-if="subscriptionLine" class="quick-status__meta">{{ subscriptionLine }}</p>

    <button type="button" class="quick-status__node" @click="$emit('pick-node')">
      <CloudOutlined class="quick-status__icon" />
      <div class="quick-status__node-copy">
        <span class="quick-status__label">{{ nodeLabel ? '当前节点' : '选择节点' }}</span>
        <span class="quick-status__value">{{ nodeLabel || '去连接节点' }}</span>
      </div>
      <span class="quick-status__chevron">›</span>
    </button>

    <p v-if="nodeLabel && !connecting" class="quick-status__hint">也可在连接页点「一键连接」重连此节点</p>
  </KyCard>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CloudOutlined } from '@ant-design/icons-vue'
import KyCard from '@/components/KyCard.vue'
import { displayNodeLabel } from '@/lib/connect-hero'

const props = defineProps<{
  selectedNode?: string | null
  remainingGb?: number | null
  expiresAt?: string | null
  connecting?: boolean
}>()

defineEmits<{ 'pick-node': [] }>()

const nodeLabel = computed(() => displayNodeLabel(props.selectedNode))

const subscriptionLine = computed(() => {
  const remaining = props.remainingGb
  const expires = props.expiresAt?.slice(0, 10)
  if (remaining != null && expires) return `剩余 ${remaining.toFixed(0)} GB · ${expires}`
  if (remaining != null) return `剩余 ${remaining.toFixed(0)} GB`
  if (expires) return `${expires} 到期`
  return ''
})
</script>

<style scoped>
.quick-status {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-sm);
}

.quick-status__meta {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.quick-status__node {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  width: 100%;
  padding: var(--ky-space-xs) 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.quick-status__icon {
  font-size: 22px;
  color: var(--ky-accent);
  flex-shrink: 0;
}

.quick-status__node-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.quick-status__label {
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.quick-status__value {
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-status__chevron {
  color: var(--ky-text-muted);
  font-size: 18px;
  flex-shrink: 0;
}

.quick-status__hint {
  margin: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}
</style>
