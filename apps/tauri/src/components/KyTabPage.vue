<template>
  <KyPage :class="[pageClass, { 'ky-page--tab-pinned': pinChrome }]">
    <KuayunBrandHeader
      v-if="showMobileBrandHeader"
      class="ky-tab-brand"
      :title="title"
      :subtitle="subtitle"
      show-version
      tab
    />
    <div v-if="$slots.sticky && pinChrome" class="ky-tab-sticky">
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
import KyPage from '@/components/KyPage.vue'
import KyPullRefresh from '@/components/KyPullRefresh.vue'
import KyStack from '@/components/KyStack.vue'
import KuayunBrandHeader from '@/components/KuayunBrandHeader.vue'
import { KySpin } from '@/components/ky'
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

function updateLayout() {
  isDesktop.value = shouldUseDesktopLayout(window.innerWidth)
}

const showMobileBrandHeader = computed(() => !!props.title && !isDesktop.value)
/** 移动端固定品牌头 / sticky 区，列表单独滚动 */
const pinChrome = computed(() => !isDesktop.value)

onMounted(() => {
  updateLayout()
  window.addEventListener('resize', updateLayout)
})
onUnmounted(() => {
  window.removeEventListener('resize', updateLayout)
})
</script>

<style scoped>
.ky-tab-brand {
  margin-bottom: 4px;
  flex-shrink: 0;
}

.ky-tab-sticky {
  flex-shrink: 0;
  margin-bottom: var(--ky-space-md);
  background: var(--ky-bg);
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
