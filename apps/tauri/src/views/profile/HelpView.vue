<template>
  <KySubPage title="导出订阅">
    <p class="help-lead">若 App 无法连接，可导出 Clash 订阅到第三方客户端。</p>
    <KyButton type="primary" block size="large" :loading="loading" @click="loadSubscriptionUrl">
      生成 Clash 订阅链接
    </KyButton>

    <KyCard v-if="subscriptionUrl" flat>
      <p class="subscription-url">{{ subscriptionUrl }}</p>
    </KyCard>

    <KyButton
      v-if="subscriptionUrl"
      block
      size="large"
      @click="copySubscriptionUrl"
    >
      复制订阅链接
    </KyButton>

    <KyAlert v-if="messageText" type="success" :message="messageText" show-icon />
    <KyAlert v-if="errorText" type="error" :message="errorText" show-icon />
  </KySubPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KySubPage from '@/components/KySubPage.vue'
import KyCard from '@/components/KyCard.vue'
import { KyAlert, KyButton } from '@/components/ky'
import { resolveApiBaseUrl } from '@/lib/api-config'
import { clientApi } from '@/api/client'

const API_BASE = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL)

const loading = ref(false)
const subscriptionUrl = ref<string | null>(null)
const messageText = ref<string | null>(null)
const errorText = ref<string | null>(null)

function buildSubscriptionUrl(token: string) {
  return `${API_BASE.replace(/\/$/, '')}/v1/subscription/clash?token=${encodeURIComponent(token)}`
}

async function loadSubscriptionUrl() {
  loading.value = true
  messageText.value = null
  errorText.value = null
  try {
    const res = await clientApi.getSubscriptionToken()
    subscriptionUrl.value = buildSubscriptionUrl(res.data.token)
  } catch (e: unknown) {
    errorText.value = e instanceof Error ? e.message : '获取订阅链接失败'
  } finally {
    loading.value = false
  }
}

async function copySubscriptionUrl() {
  if (!subscriptionUrl.value) return
  try {
    await navigator.clipboard.writeText(subscriptionUrl.value)
    messageText.value = '订阅链接已复制'
  } catch {
    errorText.value = '复制失败，请手动选择链接复制'
  }
}
</script>

<style scoped>
.help-lead {
  margin: 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.5;
}

.subscription-url {
  margin: 0;
  color: var(--ky-text);
  font-family: ui-monospace, 'Cascadia Code', monospace;
  font-size: var(--ky-font-sm);
  word-break: break-all;
  line-height: 1.5;
  -webkit-user-select: all;
  user-select: all;
}
</style>
