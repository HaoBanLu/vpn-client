<template>
  <Teleport to="body">
    <div class="ky-message-host">
      <div
        v-for="item in messageState.items"
        :key="item.id"
        class="ky-message"
        :class="item.type !== 'info' ? `ky-message--${item.type}` : undefined"
      >
        {{ item.content }}
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { messageState } from '@/lib/ui/message'
</script>
