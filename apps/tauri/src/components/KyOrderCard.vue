<template>
  <button type="button" class="ky-order-card" @click="$emit('click')">
    <div class="ky-order-card__head">
      <span class="ky-order-card__no">{{ orderNo }}</span>
      <slot name="status" />
    </div>
    <p class="ky-order-card__amount">{{ amount }}</p>
    <p v-if="sub" class="ky-order-card__sub">{{ sub }}</p>
    <p v-if="time" class="ky-order-card__time">{{ time }}</p>
    <p v-if="error" class="ky-order-card__error">{{ error }}</p>
    <p class="ky-order-card__hint">点击查看详情</p>
  </button>
</template>

<script setup lang="ts">
defineProps<{
  orderNo: string
  amount: string
  sub?: string
  time?: string
  error?: string
}>()

defineEmits<{ click: [] }>()
</script>

<style scoped>
.ky-order-card {
  width: 100%;
  text-align: left;
  border: 1px solid var(--ky-border);
  border-radius: var(--ky-radius-lg);
  background: var(--ky-bg-card);
  padding: var(--ky-space-md);
  color: var(--ky-text);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.ky-order-card:hover {
  border-color: var(--ky-border-strong);
  background: var(--ky-bg-card-hover);
}

.ky-order-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-sm);
}

.ky-order-card__no {
  font-weight: 600;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-secondary);
}

.ky-order-card__amount {
  margin: var(--ky-space-sm) 0 var(--ky-space-xs);
  font-size: var(--ky-font-lg);
  font-weight: 700;
  color: var(--ky-accent);
}

.ky-order-card__sub,
.ky-order-card__time {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ky-order-card__error {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-danger);
}

.ky-order-card__hint {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-accent);
}
</style>
