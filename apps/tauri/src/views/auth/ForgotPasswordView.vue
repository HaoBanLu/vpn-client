<template>
  <KyPage center>
    <KuayunBrandHeader title="找回密码" subtitle="通过邮箱验证码重置登录密码" auth show-version />
    <KyCard>
      <KyForm @finish="onSubmit">
        <KyFormItem label="邮箱">
          <KyInput v-model="email" placeholder="you@example.com" size="large" />
        </KyFormItem>
        <KyFormItem label="邮箱验证码">
          <div class="code-row">
            <KyInput v-model="emailCode" placeholder="验证码" size="large" class="code-input" />
            <KyButton :disabled="cooldown > 0 || sendingCode" :loading="sendingCode" @click="sendCode">
              {{ cooldown > 0 ? `${cooldown}s` : '发送' }}
            </KyButton>
          </div>
        </KyFormItem>
        <KyFormItem label="新密码">
          <KyInput v-model="newPassword" type="password" size="large" />
        </KyFormItem>
        <KyFormItem label="确认新密码">
          <KyInput v-model="confirmPassword" type="password" size="large" />
        </KyFormItem>
        <KyButton type="primary" html-type="submit" block size="large" :loading="loading" :disabled="!canSubmit">
          重置密码
        </KyButton>
      </KyForm>
      <KyAuthFooter>
        <KyButton type="link" @click="router.push({ name: 'Login' })">返回登录</KyButton>
      </KyAuthFooter>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyAuthFooter from '@/components/KyAuthFooter.vue'
import KuayunBrandHeader from '@/components/KuayunBrandHeader.vue'
import { KyButton, KyForm, KyFormItem, KyInput } from '@/components/ky'
import { clientApi } from '@/api/client'

const router = useRouter()

const email = ref('')
const emailCode = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const resetEnabled = ref(true)

let timer: ReturnType<typeof setInterval> | null = null

const canSubmit = computed(
  () =>
    resetEnabled.value &&
    email.value &&
    emailCode.value &&
    newPassword.value.length >= 6 &&
    newPassword.value === confirmPassword.value,
)

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
    await clientApi.forgotPassword(email.value)
    message.success('验证码已发送')
    startCooldown(60)
  } finally {
    sendingCode.value = false
  }
}

async function onSubmit() {
  loading.value = true
  try {
    await clientApi.resetPassword({
      email: email.value,
      email_code: emailCode.value,
      new_password: newPassword.value,
    })
    message.success('密码已重置，请登录')
    router.replace({ name: 'Login' })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const config = (await clientApi.getRegistrationConfig()).data
    resetEnabled.value = config.password_reset_enabled
  } catch {
    // ignore
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
</style>
