<template>
  <form class="ky-form" @submit.prevent="$emit('finish')">
    <slot />
  </form>
</template>

<script setup lang="ts">
defineEmits<{ finish: [] }>()
</script>
