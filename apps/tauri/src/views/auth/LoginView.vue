<template>
  <KyPage center>
    <KuayunBrandHeader
      title="欢迎回来"
      subtitle="登录跨云，开启安全加速"
      auth
      show-version
    />

    <KyCard class="login-card">
      <KyAlert
        v-if="loginBanner"
        type="info"
        :message="loginBanner"
        style="margin-bottom: 16px"
      />

      <KyAlert
        v-if="errorText"
        type="error"
        :message="errorText"
        style="margin-bottom: 16px"
      />

      <KyForm @finish="onSubmit">
        <KyFormItem label="邮箱">
          <KyInput v-model="email" placeholder="you@example.com" size="large" />
        </KyFormItem>

        <KyFormItem label="密码">
          <KyInput v-model="password" type="password" size="large" />
        </KyFormItem>

        <KyFormItem>
          <KyCheckbox v-model:checked="rememberLogin">记住账号密码</KyCheckbox>
        </KyFormItem>

        <KyButton type="primary" html-type="submit" block size="large" :loading="loading">登录</KyButton>
      </KyForm>

      <KyAuthFooter split>
        <KyButton type="link" @click="router.push({ name: 'Register' })">没有账号？去注册</KyButton>
        <KyButton type="link" @click="router.push({ name: 'ForgotPassword' })">忘记密码？</KyButton>
      </KyAuthFooter>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyAuthFooter from '@/components/KyAuthFooter.vue'
import KuayunBrandHeader from '@/components/KuayunBrandHeader.vue'
import { KyAlert, KyButton, KyCheckbox, KyForm, KyFormItem, KyInput } from '@/components/ky'
import { useAuthStore } from '@/stores/auth'
import { mapApiError } from '@/lib/api-error'
import { consumeLastInvalidation, formatLoginBanner } from '@/lib/last-invalidation-store'
import { loadSavedLoginCredentials } from '@/lib/login-credentials'

const router = useRouter()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const rememberLogin = ref(true)
const loading = ref(false)
const errorText = ref<string | null>(null)
const loginBanner = ref<string | null>(null)

onMounted(() => {
  const saved = loadSavedLoginCredentials()
  rememberLogin.value = saved.remember
  email.value = saved.email
  password.value = saved.password

  const pending = consumeLastInvalidation()
  if (pending) {
    loginBanner.value = formatLoginBanner(pending.title, pending.message)
  }
})

async function onSubmit() {
  if (!email.value.trim() || !password.value) {
    errorText.value = '请填写邮箱和密码'
    return
  }

  loading.value = true
  errorText.value = null
  try {
    await auth.login(email.value, password.value, rememberLogin.value)
    router.replace({ name: 'Connect' })
  } catch (error) {
    errorText.value = mapApiError(error, '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-card {
  width: 100%;
  max-width: 420px;
}
</style>
