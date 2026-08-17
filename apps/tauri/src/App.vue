<template>
  <router-view />
  <KyMessageHost />
  <KyConfirmHost />
  <AppUpdateOverlay />
</template>

<script setup lang="ts">
import { KyConfirmHost, KyMessageHost } from '@/components/ky'
import AppUpdateOverlay from '@/components/AppUpdateOverlay.vue'
import { useConnectStore } from '@/stores/connect'

const connect = useConnectStore()
void connect.initVpnBridge().then(() => connect.recoverAfterAppUpdate())
</script>
