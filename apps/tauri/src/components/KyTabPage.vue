<template>
  <KyPage :class="pageClass">
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
import KyPage from '@/components/KyPage.vue'
import KyPullRefresh from '@/components/KyPullRefresh.vue'
import KyStack from '@/components/KyStack.vue'
import { KySpin } from '@/components/ky'

withDefaults(
  defineProps<{
    /** 保留兼容；主 Tab 页不再展示页头，侧栏已有品牌区 */
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
</script>
