<template>
  <Teleport to="body">
    <div
      v-if="state.visible"
      class="app-update-overlay"
      :class="{ 'app-update-overlay--blocking': isBlocking }"
      @click.self="onBackdropClick"
    >
      <div class="app-update-panel" role="dialog" aria-modal="true">
        <div class="app-update-panel__title">{{ title }}</div>
        <div class="app-update-panel__body">
          <p v-if="bodyText" class="app-update-panel__text">{{ bodyText }}</p>
          <KyProgress
            v-if="showProgress"
            :percent="state.progress"
            :status="state.phase === 'error' ? 'exception' : state.phase === 'done' ? 'success' : 'normal'"
          />
          <p v-if="state.statusMessage && showProgress" class="app-update-panel__hint">{{ state.statusMessage }}</p>
        </div>
        <div v-if="showFooter" class="app-update-panel__footer">
          <KyButton v-if="showLater" size="small" :disabled="state.installing" @click="dismissPrompt">
            稍后再说
          </KyButton>
          <KyButton
            v-if="state.phase === 'prompt'"
            type="primary"
            size="small"
            :loading="state.installing"
            @click="acceptUpdate"
          >
            立即更新
          </KyButton>
          <KyButton
            v-else-if="state.phase === 'pending_install'"
            type="primary"
            size="small"
            :loading="state.installing"
            @click="installPendingApk"
          >
            立即安装
          </KyButton>
          <KyButton
            v-else-if="state.phase === 'error'"
            type="primary"
            size="small"
            @click="acceptUpdate"
          >
            重试
          </KyButton>
          <KyButton
            v-else-if="state.phase === 'done' && !isDesktopDone"
            type="primary"
            size="small"
            @click="hideOverlay"
          >
            知道了
          </KyButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { KyButton, KyProgress } from '@/components/ky'
import { useAppUpdate } from '@/lib/app-update/use-app-update'
import { isDesktopPlatform } from '@/lib/layout'

const { state, acceptUpdate, dismissPrompt, installPendingApk, hideOverlay, bindAndroidEvents, disposeUpdateListeners, refreshPendingInstall } =
  useAppUpdate()

const isBlocking = computed(() => state.phase !== 'prompt' || !!state.updateResult?.forceUpdate)

const title = computed(() => {
  if (state.phase === 'pending_install') return '新版本已下载'
  if (state.phase === 'downloading' || state.phase === 'installing') return '正在更新'
  if (state.phase === 'done') return '更新'
  if (state.phase === 'error') return '更新失败'
  return state.updateResult?.forceUpdate ? '需要更新到最新版本' : '发现新版本'
})

const bodyText = computed(() => {
  if (state.phase === 'prompt') return state.updateResult?.message || state.statusMessage
  if (state.phase === 'pending_install') return state.statusMessage
  if (state.phase === 'error') return state.statusMessage
  if (state.phase === 'done' && !showProgress.value) return state.statusMessage
  return ''
})

const showProgress = computed(() =>
  ['downloading', 'installing', 'done'].includes(state.phase),
)

const showFooter = computed(() =>
  ['prompt', 'pending_install', 'error', 'done'].includes(state.phase),
)

const showLater = computed(
  () => state.phase === 'prompt' && !state.updateResult?.forceUpdate,
)

const isDesktopDone = computed(() => state.phase === 'done' && isDesktopPlatform())

function onBackdropClick() {
  if (!isBlocking.value) dismissPrompt()
}

onMounted(async () => {
  await bindAndroidEvents()
  await refreshPendingInstall()
})

onUnmounted(() => {
  disposeUpdateListeners()
})
</script>

<style scoped>
.app-update-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.45);
  padding: 16px;
}

.app-update-overlay--blocking {
  pointer-events: auto;
}

.app-update-panel {
  width: min(420px, 100%);
  background: var(--ky-surface, #fff);
  border-radius: var(--ky-radius-lg, 12px);
  box-shadow: var(--ky-shadow-lg, 0 12px 40px rgba(0, 0, 0, 0.18));
  overflow: hidden;
}

.app-update-panel__title {
  padding: 16px 20px 0;
  font-size: var(--ky-font-lg, 16px);
  font-weight: 600;
  color: var(--ky-text, #111);
}

.app-update-panel__body {
  padding: 12px 20px 16px;
}

.app-update-panel__text {
  margin: 0 0 12px;
  white-space: pre-wrap;
  line-height: 1.6;
  color: var(--ky-text-secondary, #444);
  font-size: var(--ky-font-md, 14px);
}

.app-update-panel__hint {
  margin: 8px 0 0;
  font-size: var(--ky-font-sm, 12px);
  color: var(--ky-text-muted, #666);
}

.app-update-panel__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 20px 16px;
}
</style>
