<template>
  <div
    ref="containerRef"
    class="ky-pull-refresh"
    :class="{
      'ky-pull-refresh--pulling': pulling,
      'ky-pull-refresh--refreshing': refreshing,
      'ky-pull-refresh--desktop': isDesktop,
    }"
  >
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
  { disabled: false, threshold: 64 },
)

const containerRef = ref<HTMLElement | null>(null)
const pullDistance = ref(0)
const pulling = ref(false)
const refreshing = ref(false)
const startY = ref(0)
const tracking = ref(false)
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

/** 只看本滚动容器：父级 overflow 在 App 壳层很常见，不能据此禁用下拉 */
function canPull(): boolean {
  if (props.disabled || refreshing.value || isDesktop.value) return false
  const el = containerRef.value
  if (!el) return false
  return el.scrollTop <= 0
}

function beginTrack(clientY: number) {
  if (!canPull()) return false
  startY.value = clientY
  tracking.value = true
  pulling.value = true
  pullDistance.value = 0
  return true
}

function moveTrack(clientY: number, event?: Event) {
  if (!tracking.value) return
  const delta = clientY - startY.value
  if (delta <= 0) {
    pullDistance.value = 0
    return
  }
  // 已进入下拉：阻止浏览器把手势当成页面滚动/回弹
  event?.preventDefault()
  pullDistance.value = Math.max(0, Math.min(delta, 120))
}

async function endTrack() {
  if (!tracking.value) return
  const shouldRefresh = pullDistance.value >= props.threshold
  tracking.value = false
  pulling.value = false
  if (shouldRefresh) {
    await triggerRefresh()
  } else {
    pullDistance.value = 0
  }
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

function onTouchStart(event: TouchEvent) {
  if (event.touches.length !== 1) return
  beginTrack(event.touches[0].clientY)
}

function onTouchMove(event: TouchEvent) {
  if (!tracking.value || event.touches.length !== 1) return
  moveTrack(event.touches[0].clientY, event)
}

function onTouchEnd() {
  void endTrack()
}

function onPointerDown(event: PointerEvent) {
  // Android WebView 真机主要走 touch；桌面鼠标仍可用 pointer（非 mouse 时）
  if (event.pointerType === 'mouse') return
  if (event.pointerType === 'touch') return // 交给 touch*，避免双触发
  if (!beginTrack(event.clientY)) return
  containerRef.value?.setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (event.pointerType === 'touch' || event.pointerType === 'mouse') return
  if (!tracking.value) return
  moveTrack(event.clientY, event)
}

function onPointerUp(event: PointerEvent) {
  if (event.pointerType === 'touch' || event.pointerType === 'mouse') return
  void endTrack()
}

onMounted(() => {
  updateLayout()
  window.addEventListener('resize', updateLayout)
  const el = containerRef.value
  if (!el) return
  // passive:false 才能在下拉时 preventDefault
  el.addEventListener('touchstart', onTouchStart, { passive: true })
  el.addEventListener('touchmove', onTouchMove, { passive: false })
  el.addEventListener('touchend', onTouchEnd)
  el.addEventListener('touchcancel', onTouchEnd)
  el.addEventListener('pointerdown', onPointerDown)
  el.addEventListener('pointermove', onPointerMove)
  el.addEventListener('pointerup', onPointerUp)
  el.addEventListener('pointercancel', onPointerUp)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateLayout)
  const el = containerRef.value
  if (!el) return
  el.removeEventListener('touchstart', onTouchStart)
  el.removeEventListener('touchmove', onTouchMove)
  el.removeEventListener('touchend', onTouchEnd)
  el.removeEventListener('touchcancel', onTouchEnd)
  el.removeEventListener('pointerdown', onPointerDown)
  el.removeEventListener('pointermove', onPointerMove)
  el.removeEventListener('pointerup', onPointerUp)
  el.removeEventListener('pointercancel', onPointerUp)
})
</script>

<style scoped>
.ky-pull-refresh {
  position: relative;
  width: 100%;
  /* 允许纵向滑动，避免 body 的 touch-action:manipulation 把下拉吃掉 */
  touch-action: pan-y;
}

.ky-pull-refresh.ky-tab-scroll--pinned {
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
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
