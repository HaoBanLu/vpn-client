<template>
  <div
    class="ky-spin"
    :class="{
      'ky-spin--spinning': spinning,
      'ky-spin--inline': inline,
      'ky-spin--overlay': spinning && overlay,
    }"
  >
    <div v-if="spinning" class="ky-spin__indicator" aria-hidden="true">
      <div class="ky-spin__ring" />
    </div>
    <div class="ky-spin__content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    spinning?: boolean
    inline?: boolean
    /** true：内容保留可见，指示器居中半透明（适合已有 Hero 的页面） */
    overlay?: boolean
  }>(),
  { spinning: false, overlay: false },
)
</script>

<style scoped>
.ky-spin {
  position: relative;
  min-height: 0;
}

.ky-spin__content {
  min-height: inherit;
}

.ky-spin--spinning:not(.ky-spin--overlay) .ky-spin__content {
  opacity: 0.35;
  pointer-events: none;
}

.ky-spin--overlay.ky-spin--spinning .ky-spin__content {
  /* 保持可读，避免连接大钮下方再像多出一个「加载中」态 */
  opacity: 1;
}

.ky-spin__indicator {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  pointer-events: none;
}

.ky-spin--overlay .ky-spin__indicator {
  /* 只盖住下半内容区，别压在电源钮上 */
  top: 42%;
  background: linear-gradient(180deg, transparent, rgba(10, 14, 23, 0.35) 28%);
}

.ky-spin__ring {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2.5px solid rgba(0, 212, 255, 0.22);
  border-top-color: var(--ky-accent);
  animation: ky-spin-rotate 0.7s linear infinite;
}

@keyframes ky-spin-rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
