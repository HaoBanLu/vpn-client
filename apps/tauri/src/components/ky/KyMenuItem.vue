<template>
  <button type="button" class="ky-menu-item" @click="$emit('click')">
    <slot />
  </button>
</template>

<script setup lang="ts">
defineEmits<{ click: [] }>()
</script>
