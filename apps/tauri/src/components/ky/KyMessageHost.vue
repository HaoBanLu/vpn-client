<template>
  <Teleport to="body">
    <div class="ky-message-host" aria-live="polite" aria-relevant="additions">
      <div
        v-for="item in messageState.items"
        :key="item.id"
        class="ky-message"
        :class="`ky-message--${item.type}`"
        role="status"
      >
        <span class="ky-message__icon" aria-hidden="true">
          <svg v-if="item.type === 'success'" viewBox="0 0 28 28" fill="none">
            <circle cx="14" cy="14" r="13" stroke="currentColor" stroke-width="1.5" opacity="0.35" />
            <path
              d="M8.5 14.2L12.2 17.8L19.5 10.2"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <svg v-else-if="item.type === 'error'" viewBox="0 0 28 28" fill="none">
            <circle cx="14" cy="14" r="13" stroke="currentColor" stroke-width="1.5" opacity="0.35" />
            <path
              d="M14 8.5V15.5"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
            <circle cx="14" cy="19.5" r="1.25" fill="currentColor" />
          </svg>
          <svg v-else viewBox="0 0 28 28" fill="none">
            <circle cx="14" cy="14" r="13" stroke="currentColor" stroke-width="1.5" opacity="0.35" />
            <circle cx="14" cy="9.5" r="1.25" fill="currentColor" />
            <path
              d="M14 12.5V19.5"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
        </span>
        <span class="ky-message__text">{{ item.content }}</span>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { messageState } from '@/lib/ui/message'
</script>
