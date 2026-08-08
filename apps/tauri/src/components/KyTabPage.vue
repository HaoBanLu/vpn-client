<template>
  <KyPage :class="pageClass">
    <div v-if="showMobileBrandHeader" class="ky-tab-brand">
      <h1 class="ky-tab-brand__title">{{ title }}</h1>
    </div>
    <KyPullRefresh :on-refresh="onRefresh" :disabled="refreshDisabled">
      <slot name="before" />
      <KySpin :spinning="loading">
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
import { KySpin } from '@/components/ky'
import { shouldUseDesktopLayout } from '@/lib/layout'

const props = withDefaults(
  defineProps<{
    /** 移动端主 Tab 展示品牌页头（对齐 Compose KuayunMainTabBrandHeader） */
    title?: string
    subtitle?: string
    onRefresh: () => Promise<void> | void
    loading?: boolean
    refreshDisabled?: boolean
    pageClass?: string
    stackGap?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
    desktopLarger?: boolean
  }>(),
  {
    loading: false,
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
  padding: 4px 0 10px;
}

.ky-tab-brand__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--ky-text);
  letter-spacing: 0.02em;
}
</style>
