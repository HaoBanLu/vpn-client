<template>
  <div class="ky-sub-page">
    <header class="ky-sub-bar">
      <KyButton type="text" class="ky-sub-bar__back" aria-label="返回" title="返回" @click="onBack">
        <ArrowLeftOutlined />
      </KyButton>
      <h1 class="ky-sub-bar__title">{{ title }}</h1>
      <div class="ky-sub-bar__extra">
        <slot name="extra" />
      </div>
    </header>
    <div class="ky-sub-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { KyButton } from '@/components/ky'
import { resolveSubpageBack } from '@/lib/subpage-nav'

const props = defineProps<{
  title: string
  backTo?: string
}>()

const router = useRouter()
const route = useRoute()

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
  max-width: 42%;
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

/* overflow:hidden 的卡片在 flex 里 min-height 会变成 0，内容会被压扁裁切 */
.ky-sub-body > * {
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

  .ky-sub-body {
    padding: 16px 0 24px;
  }
}
</style>
