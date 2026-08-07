<template>
  <div class="ky-form-item">
    <label v-if="label" class="ky-form-item__label">{{ label }}</label>
    <slot />
    <p v-if="error" class="ky-form-item__error">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label?: string
  name?: string
  error?: string
}>()
</script>
