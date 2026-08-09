<template>
  <div
    class="ky-spin"
    :class="{
      'ky-spin--spinning': showIndicator,
      'ky-spin--inline': inline,
      'ky-spin--overlay': showIndicator && overlay,
      'ky-spin--empty': showIndicator && !overlay,
    }"
  >
    <div v-if="showIndicator" class="ky-spin__indicator" aria-hidden="true">
      <div class="ky-spin__ring" />
    </div>
    <div class="ky-spin__content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    spinning?: boolean
    inline?: boolean
    /** true：内容保留可见，浅色轻指示（适合已有内容的页面） */
    overlay?: boolean
    /** 延迟显示，避免快请求闪一下黑框 */
    delayMs?: number
  }>(),
  { spinning: false, overlay: false, delayMs: 180 },
)

const showIndicator = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

function clearTimer() {
  if (timer != null) {
    clearTimeout(timer)
    timer = null
  }
}

watch(
  () => props.spinning,
  (spinning) => {
    clearTimer()
    if (!spinning) {
      showIndicator.value = false
      return
    }
    const delay = Math.max(0, props.delayMs ?? 0)
    if (delay === 0) {
      showIndicator.value = true
      return
    }
    timer = setTimeout(() => {
      showIndicator.value = true
      timer = null
    }, delay)
  },
  { immediate: true },
)

onUnmounted(clearTimer)
</script>

<style scoped>
.ky-spin {
  position: relative;
  min-height: 0;
}

.ky-spin__content {
  min-height: inherit;
}

/* 空态加载：不压暗整页，只留出指示器空间感 */
.ky-spin--empty.ky-spin--spinning .ky-spin__content {
  opacity: 0.55;
  pointer-events: none;
  filter: none;
}

.ky-spin--overlay.ky-spin--spinning .ky-spin__content {
  opacity: 1;
  pointer-events: none;
}

.ky-spin__indicator {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  pointer-events: none;
}

/* 浅色原生感：轻白蒙层 + 主色环，禁止深色渐变 */
.ky-spin--overlay .ky-spin__indicator {
  background: rgba(244, 247, 252, 0.42);
  backdrop-filter: blur(1px);
}

.ky-spin--inline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
}

.ky-spin--inline .ky-spin__indicator {
  position: static;
  inset: auto;
  background: transparent;
}

.ky-spin__ring {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2.5px solid rgba(27, 77, 255, 0.16);
  border-top-color: var(--ky-accent);
  animation: ky-spin-rotate 0.7s linear infinite;
  background: transparent;
  box-shadow: none;
}

.ky-spin--inline .ky-spin__ring {
  width: 20px;
  height: 20px;
  border-width: 2px;
}

@keyframes ky-spin-rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
