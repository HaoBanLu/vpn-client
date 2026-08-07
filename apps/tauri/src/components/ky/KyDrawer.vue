<template>
  <Teleport to="body">
    <div v-if="open" class="ky-overlay ky-overlay--drawer" @click.self="close">
      <div class="ky-drawer" :style="{ width: widthStyle }">
        <div class="ky-drawer__header">{{ title }}</div>
        <div class="ky-drawer__body">
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    open?: boolean
    title?: string
    width?: string | number
  }>(),
  { open: false, width: '100%' },
)

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const widthStyle = computed(() =>
  typeof props.width === 'number' ? `${props.width}px` : props.width,
)

function close() {
  emit('update:open', false)
}
</script>
