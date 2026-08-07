<template>
  <div class="ky-stack" :class="[`ky-stack--gap-${gap}`, { 'ky-stack--desktop-lg': desktopLarger }]">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 间距阶梯，对应 --ky-space-* */
    gap?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
    /** 桌面端（≥768px）使用更大间距 */
    desktopLarger?: boolean
  }>(),
  { gap: 'md', desktopLarger: true },
)
</script>

<style scoped>
.ky-stack {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.ky-stack--gap-xs {
  gap: var(--ky-space-xs);
}

.ky-stack--gap-sm {
  gap: var(--ky-space-sm);
}

.ky-stack--gap-md {
  gap: var(--ky-space-md);
}

.ky-stack--gap-lg {
  gap: var(--ky-space-lg);
}

.ky-stack--gap-xl {
  gap: var(--ky-space-xl);
}

@media (min-width: 768px) {
  .ky-stack--desktop-lg.ky-stack--gap-md {
    gap: var(--ky-space-lg);
  }

  .ky-stack--desktop-lg.ky-stack--gap-lg {
    gap: var(--ky-space-xl);
  }
}
</style>
