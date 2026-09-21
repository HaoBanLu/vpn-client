<template>
  <KyPage :class="[pageClass, { 'ky-page--tab-pinned': pinChrome }]">
    <div v-if="isDesktop" class="ky-tab-desktop-bar">
      <KyButton
        type="text"
        class="ky-tab-desktop-bar__refresh"
        aria-label="刷新"
        title="刷新"
        :disabled="refreshDisabled || desktopRefreshing"
        @click="triggerDesktopRefresh"
      >
        <ReloadOutlined :spin="desktopRefreshing || loading" />
      </KyButton>
    </div>
    <KuayunBrandHeader
      v-if="showMobileBrandHeader"
      class="ky-tab-brand"
      :title="title"
      :subtitle="subtitle"
      show-version
      tab
    />
    <div
      v-if="$slots.sticky && pinChrome"
      class="ky-tab-sticky"
      :class="{ 'ky-tab-sticky--flush': !showMobileBrandHeader }"
    >
      <slot name="sticky" />
    </div>
    <KyPullRefresh
      class="ky-tab-scroll"
      :class="{ 'ky-tab-scroll--pinned': pinChrome }"
      :on-refresh="onRefresh"
      :disabled="refreshDisabled"
    >
      <div
        class="ky-tab-body"
        :class="[`ky-tab-body--gap-${stackGap}`, { 'ky-tab-body--desktop-lg': desktopLarger }]"
      >
        <div v-if="$slots.sticky && !pinChrome" class="ky-tab-sticky-inline">
          <slot name="sticky" />
        </div>
        <slot name="before" />
        <KySpin :spinning="loading" :overlay="spinOverlay">
          <KyStack :gap="stackGap" :desktop-larger="desktopLarger">
            <slot />
          </KyStack>
        </KySpin>
        <slot name="after" />
      </div>
    </KyPullRefresh>
  </KyPage>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import KyPage from '@/components/KyPage.vue'
import KyPullRefresh from '@/components/KyPullRefresh.vue'
import KyStack from '@/components/KyStack.vue'
import KuayunBrandHeader from '@/components/KuayunBrandHeader.vue'
import { KyButton, KySpin } from '@/components/ky'
import { shouldUseDesktopLayout } from '@/lib/layout'

const props = withDefaults(
  defineProps<{
    /** 移动端主 Tab 展示品牌页头（对齐 Compose KuayunMainTabBrandHeader） */
    title?: string
    subtitle?: string
    onRefresh: () => Promise<void> | void
    loading?: boolean
    /** 有内容时浅色轻遮罩；默认 true 避免整页变暗闪烁 */
    spinOverlay?: boolean
    refreshDisabled?: boolean
    pageClass?: string
    stackGap?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
    desktopLarger?: boolean
  }>(),
  {
    loading: false,
    spinOverlay: true,
    refreshDisabled: false,
    stackGap: 'md',
    desktopLarger: true,
  },
)

const isDesktop = ref(typeof window !== 'undefined' && shouldUseDesktopLayout(window.innerWidth))
const desktopRefreshing = ref(false)

function updateLayout() {
  isDesktop.value = shouldUseDesktopLayout(window.innerWidth)
}

const showMobileBrandHeader = computed(() => !!props.title && !isDesktop.value)
/** 移动端固定品牌头 / sticky 区，列表单独滚动 */
const pinChrome = computed(() => !isDesktop.value)

async function triggerDesktopRefresh() {
  if (props.refreshDisabled || desktopRefreshing.value) return
  desktopRefreshing.value = true
  try {
    await props.onRefresh()
  } finally {
    desktopRefreshing.value = false
  }
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
.ky-tab-desktop-bar {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  margin: -4px 0 4px;
}

.ky-tab-desktop-bar__refresh {
  color: var(--ky-text) !important;
  padding: 0 !important;
  width: 36px;
  height: 36px;
  min-width: 36px;
  min-height: 36px;
}

.ky-tab-brand {
  margin-bottom: 4px;
  flex-shrink: 0;
}

.ky-tab-sticky {
  flex-shrink: 0;
  margin-bottom: var(--ky-space-md);
  background: var(--ky-bg);
}

/* 无品牌头时，筛选条贴顶，少占一截空白 */
.ky-tab-sticky--flush {
  margin-bottom: 4px;
}

.ky-tab-sticky-inline {
  margin-bottom: var(--ky-space-md);
}

.ky-page--tab-pinned {
  flex: 1;
  min-height: 0;
  height: 100%;
  gap: 0;
}

.ky-tab-scroll--pinned {
  flex: 1;
  min-height: 0;
}

/* 与 Android spacedBy(16.dp) 对齐：before / 内容 / after 同一套间隙 */
.ky-tab-body {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.ky-tab-body--gap-xs { gap: var(--ky-space-xs); }
.ky-tab-body--gap-sm { gap: var(--ky-space-sm); }
.ky-tab-body--gap-md { gap: var(--ky-space-md); }
.ky-tab-body--gap-lg { gap: var(--ky-space-lg); }
.ky-tab-body--gap-xl { gap: var(--ky-space-xl); }

@media (min-width: 768px) {
  .ky-tab-body--desktop-lg.ky-tab-body--gap-md {
    gap: var(--ky-space-lg);
  }
  .ky-tab-body--desktop-lg.ky-tab-body--gap-lg {
    gap: var(--ky-space-xl);
  }
}
</style>
