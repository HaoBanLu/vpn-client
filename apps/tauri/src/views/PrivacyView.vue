<template>
  <KyPage center>
    <KuayunBrandHeader title="隐私政策" subtitle="请阅读并同意后继续使用" large />
    <KyCard>
      <p class="content">
        我们仅收集账号、订阅与设备连接所必需的信息，用于提供 VPN 服务。流量日志粒度与会员 Web
        端一致，不会向第三方出售你的个人数据。继续使用即表示你同意我们的隐私政策与服务条款。
      </p>
      <KyButton type="primary" block size="large" @click="accept">同意并继续</KyButton>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import KuayunBrandHeader from '@/components/KuayunBrandHeader.vue'
import { KyButton } from '@/components/ky'
import { useAuthStore } from '@/stores/auth'
import { acceptPrivacy } from '@/lib/app-meta'

const router = useRouter()
const auth = useAuthStore()

function accept() {
  acceptPrivacy()
  router.replace(auth.isAuthenticated ? { name: 'Connect' } : { name: 'Login' })
}
</script>

<style scoped>
.content {
  color: var(--ky-text-secondary);
  line-height: 1.7;
  font-size: var(--ky-font-md);
  margin: 0 0 var(--ky-space-xl);
}
</style>
