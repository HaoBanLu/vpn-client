<template>
  <div class="ky-auth-footer" :class="{ 'ky-auth-footer--split': split }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 左右分布（登录页：注册 | 忘记密码） */
    split?: boolean
  }>(),
  { split: false },
)
</script>

<style scoped>
.ky-auth-footer {
  margin-top: var(--ky-space-md);
  padding-top: var(--ky-space-md);
  border-top: 1px solid var(--ky-border-soft);
  text-align: center;
}

.ky-auth-footer--split {
  display: flex;
  justify-content: space-between;
  text-align: left;
}
</style>
