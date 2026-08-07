<template>
  <div class="power-wrap">
    <!-- 连接中：向外扩散波纹（对齐 Android ExpandingRippleRings） -->
    <div v-if="connecting" class="ripple-layer" aria-hidden="true">
      <span class="ripple-ring" style="--i: 0" />
      <span class="ripple-ring" style="--i: 1" />
      <span class="ripple-ring" style="--i: 2" />
    </div>

    <!-- 已连接：稳态护盾环（对齐 Android ConnectedSteadyShield） -->
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
        <SafetyOutlined v-if="variant === 'connected'" class="power-icon power-icon--shield" />
        <PoweroffOutlined v-else class="power-icon" />
        <span class="power-label">{{ label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { PoweroffOutlined, SafetyOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  label: string
  variant?: 'default' | 'connected' | 'connecting'
  connecting?: boolean
  /** 对齐 Android：连接中仍可点，用于中断；不要 disabled */
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
  /* 对齐 Android：halo 172 + 波纹外扩余量 */
  min-height: 210px;
  padding: 0;
}

/* —— 色板对齐 Android ConnectVisual —— */
.power-halo {
  position: relative;
  z-index: 1;
  width: 172px;
  height: 172px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle, rgba(27, 77, 255, 0.1) 0%, transparent 70%);
}

.power-halo.connected {
  background: radial-gradient(circle, rgba(22, 163, 74, 0.18) 0%, transparent 70%);
}

.power-halo.connecting {
  background: radial-gradient(circle, rgba(37, 99, 235, 0.14) 0%, transparent 70%);
}

.power-btn {
  position: relative;
  width: 138px;
  height: 138px;
  border-radius: 50%;
  border: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  color: #fff;
  overflow: hidden;
  /* 对齐 connectButtonBrush: #1B4DFF → #4F7CFF */
  background: linear-gradient(135deg, #1b4dff 0%, #4f7cff 100%);
  box-shadow:
    0 10px 28px rgba(27, 77, 255, 0.35),
    0 4px 12px rgba(0, 0, 0, 0.2);
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
  background: linear-gradient(135deg, #16a34a 0%, #22c55e 100%);
  border: 2.5px solid rgba(255, 255, 255, 0.3);
  box-shadow:
    0 16px 36px rgba(22, 163, 74, 0.42),
    0 6px 14px rgba(0, 0, 0, 0.18);
}

.power-btn.connecting {
  background: linear-gradient(135deg, #2563eb 0%, #4f7cff 100%);
  box-shadow:
    0 10px 28px rgba(37, 99, 235, 0.4),
    0 4px 12px rgba(0, 0, 0, 0.2);
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

.power-icon {
  font-size: 28px;
  line-height: 1;
  z-index: 1;
}

.power-icon--shield {
  font-size: 30px;
}

.power-label {
  z-index: 1;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.03em;
}

.power-btn.connected .power-label {
  font-weight: 600;
  letter-spacing: 0.05em;
}

/* ExpandingRippleRings：3 环、2.4s 周期（对齐 Android Canvas 210dp / base 72dp / expand 42dp） */
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
  border: 2px solid rgba(37, 99, 235, 0.42);
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

/* ConnectedSteadyShield */
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
  background: radial-gradient(circle, rgba(22, 163, 74, 0.1) 0%, transparent 70%);
}

.shield-ring {
  position: absolute;
  inset: 0;
  margin: auto;
  border-radius: 50%;
  border-style: solid;
  border-color: rgba(22, 163, 74, 0.24);
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
  border-color: rgba(22, 163, 74, 0.17);
}

.shield-ring--3 {
  width: 172px;
  height: 172px;
  border-width: 1.5px;
  border-color: rgba(22, 163, 74, 0.11);
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
