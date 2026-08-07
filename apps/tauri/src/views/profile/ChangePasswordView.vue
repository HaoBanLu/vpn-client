<template>
  <KyPage sub>
    <PageHeader title="修改密码" subtitle="更新账户登录密码" />
    <KyCard>
      <KyForm @finish="onSubmit">
        <KyFormItem label="当前密码">
          <KyInput v-model="oldPassword" type="password" size="large" />
        </KyFormItem>
        <KyFormItem label="新密码">
          <KyInput v-model="newPassword" type="password" size="large" />
        </KyFormItem>
        <KyFormItem label="确认新密码">
          <KyInput v-model="confirmPassword" type="password" size="large" />
        </KyFormItem>
        <KyButton type="primary" html-type="submit" block size="large" :loading="loading" :disabled="!canSubmit">
          保存
        </KyButton>
      </KyForm>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { KyButton, KyForm, KyFormItem, KyInput } from '@/components/ky'
import { clientApi } from '@/api/client'

const router = useRouter()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const canSubmit = computed(
  () => oldPassword.value && newPassword.value.length >= 6 && newPassword.value === confirmPassword.value,
)

async function onSubmit() {
  loading.value = true
  try {
    await clientApi.changePassword(oldPassword.value, newPassword.value)
    message.success('密码已更新')
    router.push({ name: 'Profile' })
  } finally {
    loading.value = false
  }
}
</script>
