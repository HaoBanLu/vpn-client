<template>
  <button
    :type="htmlType === 'submit' ? 'submit' : 'button'"
    class="ky-btn"
    :class="[
      `ky-btn--${visualType}`,
      size && `ky-btn--${size}`,
      { 'ky-btn--block': block, 'ky-btn--loading': loading },
    ]"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="ky-btn__spinner" />
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    type?: 'primary' | 'default' | 'link' | 'text' | 'danger'
    size?: 'small' | 'large'
    block?: boolean
    loading?: boolean
    disabled?: boolean
    htmlType?: 'button' | 'submit'
    danger?: boolean
  }>(),
  { type: 'default', htmlType: 'button' },
)

defineEmits<{ click: [event: MouseEvent] }>()

const visualType = computed(() => (props.danger ? 'danger' : props.type))
</script>
