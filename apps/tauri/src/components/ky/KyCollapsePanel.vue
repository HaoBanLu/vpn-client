<template>
  <div class="ky-collapse-panel">
    <button type="button" class="ky-collapse-panel__header" @click="open = !open">
      {{ header }}
    </button>
    <div v-show="open" class="ky-collapse-panel__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ header?: string }>()
const open = ref(false)
</script>
