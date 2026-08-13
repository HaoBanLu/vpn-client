<template>
  <KySubPage title="在线客服">

    <KySpin :spinning="loading" overlay>
      <KyStack gap="md">
        <KyAlert
          v-if="loadError"
          type="error"
          :message="loadError"
          show-icon
        />

        <KyAlert
          v-else-if="!loading && !config?.enabled"
          type="info"
          message="在线客服暂未开放"
          show-icon
        />

        <template v-else-if="config">
          <KyCard v-if="config.description || config.work_hours" highlight>
            <p v-if="config.description" class="support-desc">{{ config.description }}</p>
            <p v-if="config.work_hours" class="support-hours">服务时间：{{ config.work_hours }}</p>
          </KyCard>

          <KyEmpty
            v-if="contactChannels.length === 0 && !showTicketEntry"
            description="暂未配置客服渠道"
          />

          <KyStack v-if="contactChannels.length > 0" gap="sm">
            <p class="support-section-title">联系渠道</p>
            <KyCard
              v-for="(item, idx) in contactChannels"
              :key="`${item.type}-${idx}`"
              class="support-channel-card"
              @click="openChannel(item)"
            >
              <div class="support-channel">
                <div class="support-channel__copy">
                  <p class="support-channel__title">{{ channelTitle(item) }}</p>
                  <p class="support-channel__hint">{{ channelHint(item) }}</p>
                </div>
                <span class="support-channel__action">打开</span>
              </div>
            </KyCard>
          </KyStack>

          <KyStack v-if="showTicketEntry" gap="sm">
            <p class="support-section-title">应用内反馈</p>
            <KyButton block size="large" type="primary" @click="goTickets">
              提交工单
            </KyButton>
            <p class="support-ticket-hint">填写问题后由客服在线回复，可随时查看进度</p>
          </KyStack>
        </template>
      </KyStack>
    </KySpin>
  </KySubPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KySubPage from '@/components/KySubPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyStack from '@/components/KyStack.vue'
import { KyAlert, KyButton, KyEmpty, KySpin } from '@/components/ky'
import { clientApi, type SupportChannelItem, type SupportConfigData } from '@/api/client'
import { mapApiError } from '@/lib/api-error'
import { openSupportChannelUrl } from '@/lib/open-url'

const router = useRouter()
const loading = ref(false)
const loadError = ref<string | null>(null)
const config = ref<SupportConfigData | null>(null)

const contactChannels = computed(() =>
  (config.value?.channels ?? []).filter((item) => item.type !== 'ticket'),
)

const showTicketEntry = computed(() => {
  if (!config.value?.enabled) return false
  if (config.value.ticket_enabled === false) return false
  return true
})

function channelTitle(item: SupportChannelItem) {
  return item.label?.trim() || defaultChannelLabel(item.type)
}

function channelHint(item: SupportChannelItem) {
  const map: Record<string, string> = {
    telegram: '打开 Telegram 私聊客服',
    telegram_group: '加入官方交流群',
    telegram_channel: '关注官方频道公告',
    email: '发送邮件联系客服',
    web: '打开网页客服',
  }
  return map[item.type] || '在外部应用中打开'
}

function defaultChannelLabel(type: string) {
  const map: Record<string, string> = {
    telegram: 'Telegram 客服',
    telegram_group: 'Telegram 群组',
    telegram_channel: 'Telegram 频道',
    email: '邮箱客服',
    web: '网页客服',
  }
  return map[type] || type
}

async function load() {
  loading.value = true
  loadError.value = null
  try {
    config.value = (await clientApi.getSupportConfig()).data
  } catch (error) {
    loadError.value = mapApiError(error, '客服配置加载失败')
  } finally {
    loading.value = false
  }
}

async function openChannel(item: SupportChannelItem) {
  const url = item.url?.trim()
  if (!url) {
    message.warning('该渠道暂未配置有效链接')
    return
  }
  try {
    await openSupportChannelUrl(url, item.type)
  } catch {
    message.error('无法打开客服链接，请稍后重试')
  }
}

function goTickets() {
  router.push({ name: 'Tickets', query: { create: '1' } })
}

onMounted(load)
</script>

<style scoped>
.support-desc {
  margin: 0;
  font-size: var(--ky-font-sm);
  line-height: 1.6;
  color: var(--ky-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.support-hours {
  margin: var(--ky-space-sm) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.support-section-title {
  margin: 0;
  font-size: var(--ky-font-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ky-text-muted);
}

.support-channel-card {
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.support-channel-card:hover {
  border-color: var(--ky-border-strong);
}

.support-channel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-md);
}

.support-channel__copy {
  min-width: 0;
  flex: 1;
}

.support-channel__title {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.support-channel__hint {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.support-channel__action {
  flex-shrink: 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: var(--ky-accent);
}

.support-ticket-hint {
  margin: 0;
  text-align: center;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}
</style>
