<template>
  <router-view />
  <KyMessageHost />
  <KyConfirmHost />
</template>

<script setup lang="ts">
import { KyConfirmHost, KyMessageHost } from '@/components/ky'
import { useConnectStore } from '@/stores/connect'

const connect = useConnectStore()
void connect.initVpnBridge().then(() => connect.recoverAfterAppUpdate())
</script>
