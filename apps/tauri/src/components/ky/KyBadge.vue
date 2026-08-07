<template>
  <span class="ky-badge">
    <slot />
    <span v-if="showCount" class="ky-badge__count">{{ displayCount }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    count?: number
    overflowCount?: number
  }>(),
  { count: 0, overflowCount: 99 },
)

const showCount = computed(() => props.count > 0)
const displayCount = computed(() =>
  props.count > props.overflowCount ? `${props.overflowCount}+` : String(props.count),
)
</script>
