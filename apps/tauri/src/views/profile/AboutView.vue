<template>
  <KySubPage title="关于跨云">

    <KyCard>
      <p class="version">版本 {{ APP_VERSION_NAME }}</p>
      <p class="muted">版本码 {{ APP_VERSION_CODE }}</p>
      <p class="desc">支持节点选择、一键连接、套餐订阅与流量查看。</p>
    </KyCard>

    <KyButton type="primary" block size="large" :loading="checking" @click="checkUpdate">检查更新</KyButton>

    <KyAlert v-if="checkMessage" type="success" :message="checkMessage" show-icon />
    <KyAlert v-if="checkError" type="error" :message="checkError" show-icon />

    <KyCard v-if="updateResult?.hasUpdate" title="发现新版本">
      <p>{{ updateResult.message }}</p>
      <KyButton
        type="primary"
        block
        size="large"
        style="margin-top: var(--ky-space-md)"
        :loading="installing"
        @click="installUpdate"
      >
        {{ updateResult.forceUpdate ? '立即更新' : '下载更新' }}
      </KyButton>
    </KyCard>
  </KySubPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KySubPage from '@/components/KySubPage.vue'
import KyCard from '@/components/KyCard.vue'
import { KyAlert, KyButton } from '@/components/ky'
import { APP_VERSION_CODE, APP_VERSION_NAME, detectClientPlatform } from '@/lib/app-meta'
import { checkAppUpdate, installAppUpdate, type AppUpdateResult } from '@/lib/desktop/updater'
import { message } from '@/lib/ui/message'

const checking = ref(false)
const installing = ref(false)
const checkMessage = ref<string | null>(null)
const checkError = ref<string | null>(null)
const updateResult = ref<AppUpdateResult | null>(null)

async function checkUpdate() {
  checking.value = true
  checkMessage.value = null
  checkError.value = null
  try {
    const result = await checkAppUpdate()
    updateResult.value = result.hasUpdate ? result : null
    checkMessage.value = result.message
  } catch (e: unknown) {
    checkError.value = e instanceof Error ? e.message : '检查更新失败'
  } finally {
    checking.value = false
  }
}

async function installUpdate() {
  if (!updateResult.value?.hasUpdate) return
  installing.value = true
  try {
    const ok = await installAppUpdate({
      downloadUrl: updateResult.value.downloadUrl,
      versionLabel: updateResult.value.latestVersionName,
      versionCode: updateResult.value.latestVersionCode,
    })
    if (ok) {
      const tip =
        updateResult.value.source === 'updater'
          ? '更新已安装，请重启应用'
          : detectClientPlatform() === 'android'
            ? '已开始下载，完成后将提示安装'
            : '已在浏览器打开下载页'
      message.success(tip)
    } else {
      message.error('无法启动更新，请稍后重试')
    }
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    installing.value = false
  }
}
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
