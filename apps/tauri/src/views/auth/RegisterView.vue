<template>
  <KyPage center>
    <KuayunBrandHeader title="创建账户" subtitle="注册跨云，畅享全球加速" auth show-version />
    <KyCard soft>
      <KyForm @finish="onSubmit">
        <KyFormItem label="邮箱">
          <KyInput v-model="email" placeholder="you@example.com" size="large" />
        </KyFormItem>
        <KyFormItem label="密码">
          <KyInput v-model="password" type="password" size="large" />
        </KyFormItem>
        <KyFormItem v-if="requireEmailCode" label="邮箱验证码">
          <div class="code-row">
            <KyInput v-model="emailCode" placeholder="验证码" size="large" class="code-input" />
            <KyButton :disabled="cooldown > 0 || sendingCode" :loading="sendingCode" @click="sendCode">
              {{ cooldown > 0 ? `${cooldown}s` : '发送' }}
            </KyButton>
          </div>
        </KyFormItem>
        <KyFormItem>
          <KyCheckbox v-model:checked="acceptedTerms">我已阅读并同意服务条款与隐私政策</KyCheckbox>
        </KyFormItem>
        <KyButton
          type="primary"
          html-type="submit"
          block
          size="large"
          :loading="loading"
          :disabled="!acceptedTerms || registrationDisabled"
        >
          注册
        </KyButton>
      </KyForm>
    </KyCard>
    <div class="auth-footer">
      <KyButton type="link" @click="router.push({ name: 'Login' })">已有账号？去登录</KyButton>
    </div>
  </KyPage>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import KuayunBrandHeader from '@/components/KuayunBrandHeader.vue'
import { KyButton, KyCheckbox, KyForm, KyFormItem, KyInput } from '@/components/ky'
import { clientApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const emailCode = ref('')
const acceptedTerms = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const requireEmailCode = ref(false)
const registrationDisabled = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

function startCooldown(seconds: number) {
  cooldown.value = seconds
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function sendCode() {
  if (!email.value) {
    message.warning('请先填写邮箱')
    return
  }
  sendingCode.value = true
  try {
    await clientApi.sendEmailCode(email.value, 'register')
    message.success('验证码已发送')
    startCooldown(60)
  } finally {
    sendingCode.value = false
  }
}

async function onSubmit() {
  if (!email.value || password.value.length < 6) return
  loading.value = true
  try {
    await auth.register(email.value, password.value, requireEmailCode.value ? emailCode.value : undefined)
    router.replace({ name: 'Connect' })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const config = (await clientApi.getRegistrationConfig()).data
    requireEmailCode.value = config.email_verification_required
    registrationDisabled.value = !config.registration_enabled
    if (registrationDisabled.value) {
      message.warning('当前暂未开放注册')
    }
  } catch {
    // ignore config load failure
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.code-row {
  display: flex;
  gap: var(--ky-space-sm);
  width: 100%;
}

.code-input {
  flex: 1;
}

.auth-footer {
  margin-top: 12px;
  text-align: center;
}
</style>
