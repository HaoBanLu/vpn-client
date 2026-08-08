<template>
  <KyPage :class="pageClass">
    <KuayunBrandHeader
      v-if="showMobileBrandHeader"
      class="ky-tab-brand"
      :title="title"
      :subtitle="subtitle"
      show-version
      tab
    />
    <KyPullRefresh :on-refresh="onRefresh" :disabled="refreshDisabled">
      <slot name="before" />
      <KySpin :spinning="loading" :overlay="spinOverlay">
        <KyStack :gap="stackGap" :desktop-larger="desktopLarger">
          <slot />
        </KyStack>
      </KySpin>
      <slot name="after" />
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
    /** 有内容时仅半透明遮罩，避免连接页大圈下再叠一层转圈 */
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
}
</style>
