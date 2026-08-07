<template>
  <div class="page-header" :class="{ 'page-header--desktop': isDesktop }">
    <KyButton type="text" class="back-btn" aria-label="返回" title="返回" @click="onBack">
      <ArrowLeftOutlined />
    </KyButton>
    <div class="page-header-text">
      <nav v-if="isDesktop" class="page-header-breadcrumb" aria-label="面包屑">
        <button type="button" class="page-header-breadcrumb__link" @click="onBack">
          {{ parentLabel }}
        </button>
        <span class="page-header-breadcrumb__sep" aria-hidden="true">/</span>
        <span class="page-header-breadcrumb__current">{{ title }}</span>
      </nav>
      <h2 v-if="!isDesktop" class="page-header-title">{{ title }}</h2>
      <p v-if="subtitle" class="page-header-subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.extra" class="page-header-extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { KyButton } from '@/components/ky'
import { shouldUseDesktopLayout } from '@/lib/layout'
import { isProfileRoute } from '@/lib/route-groups'

const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    backTo?: string
    parentLabel?: string
  }>(),
  { parentLabel: '我的' },
)

const router = useRouter()
const route = useRoute()
const isDesktop = ref(false)

function updateLayout() {
  isDesktop.value = shouldUseDesktopLayout(window.innerWidth)
}

function onBack() {
  if (props.backTo) {
    router.push(props.backTo)
    return
  }
  if (isProfileRoute(route.name) && route.name !== 'Profile') {
    router.push({ name: 'Profile' })
    return
  }
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push({ name: 'Profile' })
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
.page-header {
  display: flex;
  align-items: flex-start;
  gap: var(--ky-space-sm);
  margin-bottom: var(--ky-space-md);
}

.page-header--desktop {
  align-items: flex-start;
  margin-bottom: var(--ky-space-lg);
  padding-bottom: var(--ky-space-md);
  border-bottom: 1px solid var(--ky-border-soft);
}

.back-btn {
  color: var(--ky-accent) !important;
  padding: 0 !important;
  width: 34px;
  height: 34px;
  min-width: 34px;
  min-height: 34px;
  flex-shrink: 0;
  margin-top: 2px;
}

.page-header--desktop .back-btn {
  margin-top: 4px;
}

.page-header-text {
  flex: 1;
  min-width: 0;
}

.page-header-title {
  margin: 0;
  font-size: var(--ky-font-xl);
  font-weight: 700;
  color: var(--ky-text);
  line-height: 1.3;
}

.page-header-subtitle {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
  line-height: 1.5;
}

.page-header--desktop .page-header-subtitle {
  margin-top: var(--ky-space-sm);
}

.page-header-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--ky-space-xs);
  margin: 0;
  font-size: var(--ky-font-sm);
  line-height: 1.4;
}

.page-header-breadcrumb__link {
  padding: 0;
  border: none;
  background: none;
  color: var(--ky-accent);
  font: inherit;
  cursor: pointer;
}

.page-header-breadcrumb__link:hover {
  text-decoration: underline;
}

.page-header-breadcrumb__sep {
  color: var(--ky-text-muted);
}

.page-header-breadcrumb__current {
  font-size: var(--ky-font-xl);
  font-weight: 700;
  color: var(--ky-text);
}

.page-header-extra {
  flex-shrink: 0;
  align-self: flex-start;
}

.page-header--desktop .page-header-extra {
  align-self: center;
}
</style>
