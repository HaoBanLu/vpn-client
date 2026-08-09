<template>
  <KyPage sub>
    <PageHeader title="应用直连" subtitle="指定应用不走 VPN，其余流量默认加速" />

    <KyAlert
      type="warning"
      message="开启应用直连后，所选应用将暴露真实 IP。请仅对确需直连的应用开启。"
      show-icon
    />

    <KyCard flat class="info-card">
      <p class="info-title">分应用代理</p>
      <p class="info-body">
        {{ platformHint }}
      </p>
      <ul class="info-list">
        <li>未勾选的应用仍走 VPN 加速</li>
        <li>系统应用与本应用默认保持代理，避免误伤</li>
        <li>修改后需重连 VPN 才能完全生效</li>
      </ul>
    </KyCard>

    <KyCard flat class="empty-card">
      <KyEmpty :description="emptyDescription" />
      <KyButton v-if="canOpenBypass" block size="large" type="primary" @click="goBypass">
        去配置规则直连
      </KyButton>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { KyAlert, KyButton, KyEmpty } from '@/components/ky'
import { detectClientPlatform } from '@/lib/app-meta'

const router = useRouter()
const platform = detectClientPlatform()

const platformHint = computed(() => {
  if (platform === 'android') {
    return 'Android 端将读取已安装应用列表供勾选。当前构建尚未开放系统应用列表权限，功能入口已对齐，完整选择器随下一版权限链路开放。'
  }
  return '分应用直连依赖系统应用列表，目前仅 Android 客户端支持。桌面端请使用「规则直连」按域名/IP 绕过。'
})

const emptyDescription = computed(() =>
  platform === 'android' ? '应用列表即将开放，请稍后更新' : '桌面端请改用规则直连',
)

const canOpenBypass = computed(() => platform !== 'android')

function goBypass() {
  router.push({ name: 'DirectBypassRules' })
}
</script>

<style scoped>
.info-card {
  margin-top: 12px;
}

.info-title {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 700;
  color: var(--ky-text);
}

.info-body {
  margin: 8px 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.5;
}

.info-list {
  margin: 12px 0 0;
  padding-left: 18px;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
  line-height: 1.6;
}

.empty-card {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
