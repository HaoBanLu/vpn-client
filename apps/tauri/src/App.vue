<template>
  <router-view />
  <KyMessageHost />
  <KyConfirmHost />
</template>

<script setup lang="ts">
import { KyConfirmHost, KyMessageHost } from '@/components/ky'
</script>
