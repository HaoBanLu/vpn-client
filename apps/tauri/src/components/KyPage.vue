<template>
  <div class="ky-page" :class="{ 'ky-page--sub': sub, 'ky-page--center': center }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 子页面（无底部导航留白） */
    sub?: boolean
    /** 垂直居中（登录等） */
    center?: boolean
  }>(),
  { sub: false, center: false },
)
</script>

<style scoped>
.ky-page {
  display: flex;
  flex-direction: column;
  gap: var(--ky-space-md);
  width: 100%;
  max-width: var(--ky-page-max-width);
  margin: 0 auto;
  min-height: min-content;
}

@media (min-width: 768px) {
  .ky-page:not(.ky-page--center):not(.ky-page--sub) {
    max-width: var(--ky-page-max-width-desktop);
  }
}

.ky-page--sub {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 16px;
  padding-bottom: var(--ky-space-2xl);
}

.ky-page--center {
  min-height: 100vh;
  justify-content: center;
  padding: var(--ky-space-md) 20px;
}

@media (max-width: 767px) {
  .ky-page--center {
    /* Android 原生 content padding 已避让系统栏 */
    min-height: 100%;
  }
}
</style>
