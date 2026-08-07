<template>
  <div class="ky-spin" :class="{ 'ky-spin--spinning': spinning, 'ky-spin--inline': inline }">
    <div v-if="spinning" class="ky-spin__indicator">
      <div class="ky-spin__ring" />
    </div>
    <div class="ky-spin__content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    spinning?: boolean
    inline?: boolean
  }>(),
  { spinning: false },
)
</script>
