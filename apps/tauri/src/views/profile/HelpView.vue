<template>
  <KyPage sub>
    <PageHeader
      title="订阅导出"
      subtitle="高级功能：导出 Clash 订阅，供第三方客户端使用"
    />

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
      class="copy-btn"
      @click="copySubscriptionUrl"
    >
      复制订阅链接
    </KyButton>

    <KyAlert v-if="messageText" type="success" :message="messageText" show-icon />
    <KyAlert v-if="errorText" type="error" :message="errorText" show-icon />
  </KyPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
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
.subscription-url {
  margin: 0;
  color: var(--ky-text);
  font-family: ui-monospace, 'Cascadia Code', monospace;
  font-size: var(--ky-font-sm);
  line-height: 1.6;
  word-break: break-all;
}

.copy-btn {
  margin-top: var(--ky-space-sm);
}
</style>
