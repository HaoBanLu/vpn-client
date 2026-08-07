<template>
  <div class="ky-input-wrap" v-if="type === 'password'">
    <input
      class="ky-input"
      :class="{ 'ky-input--large': size === 'large' }"
      :type="visible ? 'text' : 'password'"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      @input="onInput"
    />
    <button type="button" class="ky-input-wrap__toggle" @click="visible = !visible">
      {{ visible ? '隐藏' : '显示' }}
    </button>
  </div>
  <input
    v-else
    class="ky-input"
    :class="{ 'ky-input--large': size === 'large' }"
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    @input="onInput"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'

withDefaults(
  defineProps<{
    modelValue?: string
    type?: 'text' | 'password'
    placeholder?: string
    size?: 'large'
    disabled?: boolean
  }>(),
  { type: 'text', modelValue: '' },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const visible = ref(false)

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>
