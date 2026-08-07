<template>
  <span class="ky-tag" :class="`ky-tag--${variant}`"><slot /></span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ color?: string }>()

const variant = computed(() => {
  const map: Record<string, string> = {
    success: 'success',
    warning: 'warning',
    error: 'error',
    processing: 'processing',
    blue: 'blue',
    cyan: 'cyan',
    gold: 'gold',
    green: 'green',
    orange: 'orange',
    default: 'default',
  }
  return map[props.color || 'default'] || props.color || 'default'
})
</script>
