<template>
  <div class="ky-sub-page">
    <header class="ky-sub-bar">
      <KyButton type="text" class="ky-sub-bar__back" aria-label="返回" title="返回" @click="onBack">
        <ArrowLeftOutlined />
      </KyButton>
      <h1 class="ky-sub-bar__title">{{ title }}</h1>
      <div class="ky-sub-bar__extra">
        <KyButton
          v-if="onRefresh && isDesktop"
          type="text"
          class="ky-sub-bar__refresh"
          aria-label="刷新"
          title="刷新"
          :disabled="refreshDisabled || refreshing"
          @click="triggerDesktopRefresh"
        >
          <ReloadOutlined :spin="refreshing || loading" />
        </KyButton>
        <slot name="extra" />
      </div>
    </header>
    <KyPullRefresh
      v-if="onRefresh"
      class="ky-sub-body ky-sub-body--pull"
      :on-refresh="runRefresh"
      :disabled="refreshDisabled"
    >
      <slot />
    </KyPullRefresh>
    <div v-else class="ky-sub-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { KyButton } from '@/components/ky'
import KyPullRefresh from '@/components/KyPullRefresh.vue'
import { resolveSubpageBack } from '@/lib/subpage-nav'
import { shouldUseDesktopLayout } from '@/lib/layout'

const props = withDefaults(
  defineProps<{
    title: string
    backTo?: string
    onRefresh?: () => Promise<void> | void
    refreshDisabled?: boolean
    loading?: boolean
  }>(),
  {
    refreshDisabled: false,
    loading: false,
  },
)

const router = useRouter()
const route = useRoute()
const refreshing = ref(false)
const isDesktop = ref(typeof window !== 'undefined' && shouldUseDesktopLayout(window.innerWidth))

function updateLayout() {
  isDesktop.value = shouldUseDesktopLayout(window.innerWidth)
}

async function runRefresh() {
  if (!props.onRefresh || props.refreshDisabled) return
  await props.onRefresh()
}

async function triggerDesktopRefresh() {
  if (!props.onRefresh || props.refreshDisabled || refreshing.value) return
  refreshing.value = true
  try {
    await props.onRefresh()
  } finally {
    refreshing.value = false
  }
}

function onBack() {
  const target = resolveSubpageBack({
    backTo: props.backTo,
    routeName: route.name,
    historyLength: window.history.length,
  })
  if (target === 'history-back') {
    router.back()
    return
  }
  void router.push(target)
}

onMounted(() => {
  updateLayout()
  window.addEventListener('resize', updateLayout)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateLayout)
})
</script>

<style scoped>
.ky-sub-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
  width: 100%;
  max-width: none;
}

.ky-sub-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  height: 48px;
  padding: 0 8px 0 2px;
  background: var(--ky-bg);
  border-bottom: 1px solid var(--ky-border-soft);
}

.ky-sub-bar__back {
  color: var(--ky-text) !important;
  padding: 0 !important;
  width: 40px;
  height: 40px;
  min-width: 40px;
  min-height: 40px;
  flex-shrink: 0;
}

.ky-sub-bar__title {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--ky-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ky-sub-bar__extra {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 2px;
  max-width: 48%;
}

.ky-sub-bar__refresh {
  color: var(--ky-text) !important;
  padding: 0 !important;
  width: 40px;
  height: 40px;
  min-width: 40px;
  min-height: 40px;
}

.ky-sub-body {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 16px;
  padding-bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ky-sub-body--pull {
  display: block;
  padding: 0;
}

.ky-sub-body--pull :deep(.ky-pull-refresh__content) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  padding-bottom: 24px;
}

/* overflow:hidden 的卡片在 flex 里 min-height 会变成 0，内容会被压扁裁切 */
.ky-sub-body:not(.ky-sub-body--pull) > *,
.ky-sub-body--pull :deep(.ky-pull-refresh__content > *) {
  flex-shrink: 0;
  min-width: 0;
}

@media (min-width: 960px) {
  .ky-sub-page {
    max-width: var(--ky-page-max-width-desktop);
  }

  .ky-sub-bar {
    padding-left: 0;
    padding-right: 0;
  }

  .ky-sub-body:not(.ky-sub-body--pull) {
    padding: 16px 0 24px;
  }

  .ky-sub-body--pull :deep(.ky-pull-refresh__content) {
    padding: 16px 0 24px;
  }
}
</style>
