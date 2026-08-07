<template>
  <div
    ref="containerRef"
    class="ky-pull-refresh"
    :class="{
      'ky-pull-refresh--pulling': pulling,
      'ky-pull-refresh--refreshing': refreshing,
      'ky-pull-refresh--desktop': isDesktop,
    }"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <!-- 桌面已去掉右上角刷新钮；仅移动端保留下拉刷新指示 -->
    <div v-if="!isDesktop" class="ky-pull-refresh__indicator" :style="indicatorStyle">
      <template v-if="refreshing">
        <ReloadOutlined class="ky-pull-refresh__icon ky-pull-refresh__icon--spin" />
        <span class="ky-pull-refresh__hint">刷新中…</span>
      </template>
      <span v-else-if="pullDistance >= threshold">松开刷新</span>
      <span v-else-if="pullDistance > 0">下拉刷新</span>
    </div>
    <div class="ky-pull-refresh__content" :style="{ transform: `translateY(${contentOffset}px)` }">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { shouldUseDesktopLayout } from '@/lib/layout'

const props = withDefaults(
  defineProps<{
    onRefresh: () => Promise<void> | void
    disabled?: boolean
    threshold?: number
  }>(),
  { disabled: false, threshold: 72 },
)

const containerRef = ref<HTMLElement | null>(null)
const pullDistance = ref(0)
const pulling = ref(false)
const refreshing = ref(false)
const startY = ref(0)
const activePointerId = ref<number | null>(null)
const isDesktop = ref(false)

const indicatorStyle = computed(() => {
  if (isDesktop.value) return undefined
  return { height: `${indicatorHeight.value}px` }
})

const indicatorHeight = computed(() => {
  if (refreshing.value) return 40
  return Math.min(pullDistance.value, 56)
})
const contentOffset = computed(() => {
  if (isDesktop.value) return 0
  if (refreshing.value) return 40
  return Math.min(pullDistance.value * 0.5, 28)
})

function updateLayout() {
  isDesktop.value = shouldUseDesktopLayout(window.innerWidth)
}

function canPull(): boolean {
  if (props.disabled || refreshing.value || isDesktop.value) return false
  const el = containerRef.value
  if (!el) return false
  let node: HTMLElement | null = el
  while (node) {
    if (node.scrollTop > 0) return false
    node = node.parentElement
  }
  return window.scrollY <= 0
}

function onPointerDown(event: PointerEvent) {
  if (!canPull() || event.pointerType === 'mouse') return
  startY.value = event.clientY
  activePointerId.value = event.pointerId
  pulling.value = true
  containerRef.value?.setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!pulling.value || activePointerId.value !== event.pointerId) return
  const delta = event.clientY - startY.value
  pullDistance.value = Math.max(0, Math.min(delta, 120))
}

async function triggerRefresh() {
  if (props.disabled || refreshing.value) return
  refreshing.value = true
  pullDistance.value = 0
  try {
    await props.onRefresh()
  } finally {
    refreshing.value = false
  }
}

function onPointerUp(event: PointerEvent) {
  if (activePointerId.value !== event.pointerId) return
  const shouldRefresh = pullDistance.value >= props.threshold
  pulling.value = false
  activePointerId.value = null
  if (shouldRefresh) {
    void triggerRefresh()
  } else {
    pullDistance.value = 0
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
.ky-pull-refresh {
  position: relative;
  width: 100%;
}

.ky-pull-refresh__indicator {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-xs);
  transition: height 0.15s ease;
}

.ky-pull-refresh__content {
  transition: transform 0.15s ease;
}

.ky-pull-refresh--refreshing .ky-pull-refresh__content {
  transform: translateY(40px) !important;
}

.ky-pull-refresh__icon {
  font-size: 16px;
}

.ky-pull-refresh__icon--spin {
  animation: ky-pull-refresh-spin 0.8s linear infinite;
}

.ky-pull-refresh__hint {
  font-size: var(--ky-font-xs);
}

@keyframes ky-pull-refresh-spin {
  to {
    transform: rotate(360deg);
  }
}

.ky-pull-refresh--desktop .ky-pull-refresh__content,
.ky-pull-refresh--desktop.ky-pull-refresh--refreshing .ky-pull-refresh__content {
  transform: none !important;
}
</style>
