<template>
  <div class="ky-grid-2-layout">
    <slot />
  </div>
</template>

<style scoped>
.ky-grid-2-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--ky-space-md);
  width: 100%;
}

@media (min-width: 768px) {
  .ky-grid-2-layout {
    gap: var(--ky-space-lg);
  }
}

@media (min-width: 960px) {
  .ky-grid-2-layout {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--ky-space-xl);
  }
}
</style>
