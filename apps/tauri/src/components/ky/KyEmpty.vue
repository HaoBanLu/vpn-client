<template>
  <div class="ky-empty">
    <p class="ky-empty__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{ description?: string }>()
</script>
