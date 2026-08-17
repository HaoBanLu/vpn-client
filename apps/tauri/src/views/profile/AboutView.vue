<template>
  <KySubPage title="关于跨云">

    <KyCard>
      <p class="version">版本 {{ APP_VERSION_NAME }}</p>
      <p class="muted">版本码 {{ APP_VERSION_CODE }}</p>
      <p class="desc">支持节点选择、一键连接、套餐订阅与流量查看。</p>
    </KyCard>

    <KyButton type="primary" block size="large" :loading="updateState.checking" @click="checkUpdate">
      检查更新
    </KyButton>

    <KyAlert v-if="updateState.manualCheckMessage" type="success" :message="updateState.manualCheckMessage" show-icon />
    <KyAlert v-if="updateState.manualCheckError" type="error" :message="updateState.manualCheckError" show-icon />

    <KyCard v-if="updateState.updateResult?.hasUpdate" title="发现新版本">
      <p>{{ updateState.updateResult.message }}</p>
      <KyButton
        type="primary"
        block
        size="large"
        style="margin-top: var(--ky-space-md)"
        :loading="updateState.installing"
        @click="installUpdate"
      >
        {{ updateState.updateResult.forceUpdate ? '立即更新' : '下载更新' }}
      </KyButton>
    </KyCard>

    <KyCard v-else-if="updateState.pendingInstall" title="待安装更新">
      <p>版本 {{ updateState.pendingInstall.versionLabel }} 已下载完成。</p>
      <KyButton
        type="primary"
        block
        size="large"
        style="margin-top: var(--ky-space-md)"
        :loading="updateState.installing"
        @click="installPending"
      >
        继续安装
      </KyButton>
    </KyCard>
  </KySubPage>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import KySubPage from '@/components/KySubPage.vue'
import KyCard from '@/components/KyCard.vue'
import { KyAlert, KyButton } from '@/components/ky'
import { APP_VERSION_CODE, APP_VERSION_NAME } from '@/lib/app-meta'
import { useAppUpdate } from '@/lib/app-update/use-app-update'

const { state: updateState, checkManual, acceptUpdate, installPendingApk, refreshPendingInstall } = useAppUpdate()

async function checkUpdate() {
  try {
    await checkManual()
  } catch {
    // error stored in updateState.manualCheckError
  }
}

async function installUpdate() {
  await acceptUpdate()
}

async function installPending() {
  await installPendingApk()
}

onMounted(() => {
  void refreshPendingInstall()
})
</script>

<style scoped>
.version {
  margin: 0;
  font-size: var(--ky-font-lg);
  font-weight: 600;
  color: var(--ky-text);
}

.muted {
  margin: var(--ky-space-xs) 0 0;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.desc {
  margin: var(--ky-space-md) 0 0;
  font-size: var(--ky-font-md);
  color: var(--ky-text-secondary);
  line-height: 1.6;
}
</style>
