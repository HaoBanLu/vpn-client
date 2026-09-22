<template>
  <div class="power-wrap">
    <!-- 连接中：向外扩散波纹 -->
    <div v-if="connecting" class="ripple-layer" aria-hidden="true">
      <span class="ripple-ring" style="--i: 0" />
      <span class="ripple-ring" style="--i: 1" />
      <span class="ripple-ring" style="--i: 2" />
    </div>

    <!-- 已连接：稳态护盾环 -->
    <div v-else-if="variant === 'connected'" class="shield-layer" aria-hidden="true">
      <span class="shield-glow" />
      <span class="shield-ring shield-ring--1" />
      <span class="shield-ring shield-ring--2" />
      <span class="shield-ring shield-ring--3" />
    </div>

    <div class="power-halo" :class="variant">
      <button
        type="button"
        class="power-btn"
        :class="[variant, { pressed }]"
        :aria-label="label"
        @pointerdown="pressed = true"
        @pointerup="pressed = false"
        @pointerleave="pressed = false"
        @pointercancel="pressed = false"
        @click="onClick"
      >
        <span v-if="variant === 'connected'" class="power-btn__sheen" />
        <span class="power-btn__icon" aria-hidden="true">
          <span v-if="connecting" class="power-btn__spinner" />
          <svg v-else-if="variant === 'connected'" class="power-btn__svg" viewBox="0 0 24 24" fill="none">
            <path
              d="M12 2.5 18.5 6v5.2c0 4.45-2.95 8.62-6.5 9.3-3.55-.68-6.5-4.85-6.5-9.3V6L12 2.5Z"
              stroke="currentColor"
              stroke-width="1.75"
              stroke-linejoin="round"
            />
            <path
              d="M8.75 12.25 11 14.5l5.25-5.5"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          <svg v-else class="power-btn__svg" viewBox="0 0 24 24" fill="none">
            <path
              d="M13.2 2.5 5.5 14.2h6.3l-1.1 7.3 8.3-11.5h-6.4l.6-7.5Z"
              fill="currentColor"
            />
          </svg>
        </span>
        <span class="power-label">{{ label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  label: string
  variant?: 'default' | 'connected' | 'connecting'
  connecting?: boolean
  /** 连接中仍可点，用于中断；不要 disabled */
  disabled?: boolean
}>()

const emit = defineEmits<{ click: [] }>()

const pressed = ref(false)

function onClick() {
  if (props.disabled) return
  emit('click')
}
</script>

<style scoped>
.power-wrap {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  min-height: 200px;
  padding: 0;
}

.power-halo {
  position: relative;
  z-index: 1;
  width: 168px;
  height: 168px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle, var(--ky-accent-bg) 0%, transparent 70%);
}

.power-halo.connected {
  background: radial-gradient(circle, var(--ky-success-bg) 0%, transparent 70%);
}

.power-halo.connecting {
  background: radial-gradient(circle, var(--ky-accent-bg) 0%, transparent 70%);
}

.power-btn {
  position: relative;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  border: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  color: var(--ky-on-accent);
  overflow: hidden;
  background: linear-gradient(135deg, var(--ky-accent-deep) 0%, var(--ky-accent-soft) 100%);
  box-shadow:
    0 10px 28px rgba(59, 130, 246, 0.32),
    0 4px 12px rgba(15, 23, 42, 0.12);
  transform: scale(1);
  transition: transform 0.18s cubic-bezier(0.34, 1.3, 0.64, 1), box-shadow 0.18s ease;
  -webkit-tap-highlight-color: transparent;
}

.power-btn.pressed:not(.connecting) {
  transform: scale(0.93);
}

.power-btn.connected.pressed {
  transform: scale(0.96);
  transition: transform 0.12s ease;
}

.power-btn.connected {
  background: linear-gradient(135deg, #059669 0%, var(--ky-success) 100%);
  border: 2.5px solid rgba(255, 255, 255, 0.3);
  box-shadow:
    0 16px 36px rgba(16, 185, 129, 0.38),
    0 6px 14px rgba(15, 23, 42, 0.12);
}

.power-btn.connecting {
  background: linear-gradient(135deg, var(--ky-accent-deep) 0%, var(--ky-accent-soft) 100%);
  box-shadow:
    0 10px 28px rgba(37, 99, 235, 0.36),
    0 4px 12px rgba(15, 23, 42, 0.12);
}

.power-btn__sheen {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.14) 0%,
    transparent 45%,
    rgba(0, 0, 0, 0.06) 100%
  );
  pointer-events: none;
}

.power-btn__icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
  flex-shrink: 0;
}

.power-btn__svg {
  width: 44px;
  height: 44px;
  display: block;
}

.power-btn__spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: power-spin 0.75s linear infinite;
}

@keyframes power-spin {
  to {
    transform: rotate(360deg);
  }
}

.power-label {
  z-index: 1;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.04em;
  min-width: 4em;
  text-align: center;
}

.power-btn.connected .power-label {
  letter-spacing: 0.04em;
}

.ripple-layer {
  position: absolute;
  width: 210px;
  height: 210px;
  pointer-events: none;
}

.ripple-ring {
  position: absolute;
  inset: 0;
  margin: auto;
  width: 144px;
  height: 144px;
  border-radius: 50%;
  border: 2px solid rgba(59, 130, 246, 0.4);
  animation: ripple-expand 2.4s linear infinite;
  animation-delay: calc(var(--i) * -0.8s);
}

@keyframes ripple-expand {
  0% {
    transform: scale(1);
    opacity: 0.42;
  }
  100% {
    transform: scale(1.58);
    opacity: 0;
  }
}

.shield-layer {
  position: absolute;
  width: 220px;
  height: 220px;
  pointer-events: none;
}

.shield-glow {
  position: absolute;
  inset: 20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.12) 0%, transparent 70%);
}

.shield-ring {
  position: absolute;
  inset: 0;
  margin: auto;
  border-radius: 50%;
  border-style: solid;
  border-color: rgba(16, 185, 129, 0.28);
}

.shield-ring--1 {
  width: 140px;
  height: 140px;
  border-width: 2px;
  opacity: 0.9;
}

.shield-ring--2 {
  width: 156px;
  height: 156px;
  border-width: 1.8px;
  border-color: rgba(16, 185, 129, 0.18);
}

.shield-ring--3 {
  width: 172px;
  height: 172px;
  border-width: 1.5px;
  border-color: rgba(16, 185, 129, 0.12);
  animation: shield-shimmer 9s ease-in-out infinite alternate;
}

@keyframes shield-shimmer {
  from {
    opacity: 0.72;
  }
  to {
    opacity: 1;
  }
}
</style>
