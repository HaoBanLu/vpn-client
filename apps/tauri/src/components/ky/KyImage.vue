<template>
  <img class="ky-image" :src="src" :alt="alt" />
</template>

<script setup lang="ts">
defineProps<{ src?: string; alt?: string }>()
</script>
