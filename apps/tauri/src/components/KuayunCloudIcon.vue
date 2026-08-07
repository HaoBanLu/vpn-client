<template>
  <span class="kuayun-cloud-icon" :class="[`size-${size}`]" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M6.5 12.5c0-2.5 2-4.5 4.5-4.5.8 0 1.5.2 2.1.5.8-1.5 2.4-2.5 4.2-2.5 2.6 0 4.7 2.1 4.7 4.7 0 .2 0 .4-.1.5 1.2.6 2 1.8 2 3.3 0 2-1.6 3.6-3.6 3.6H7.1c-2 0-3.6-1.6-3.6-3.6 0-1.7 1.2-3.1 2.8-3.5-.1-.4-.2-.8-.2-1.2z"
      />
    </svg>
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { size: 'md' },
)
</script>

<style scoped>
.kuayun-cloud-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.kuayun-cloud-icon svg {
  display: block;
}

.size-sm svg {
  width: 16px;
  height: 16px;
}

.size-md svg {
  width: 24px;
  height: 24px;
}

.size-lg svg {
  width: 36px;
  height: 36px;
}
</style>
