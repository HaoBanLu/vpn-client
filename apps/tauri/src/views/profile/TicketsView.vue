<template>
  <KyPage sub>
    <PageHeader title="我的工单" subtitle="问题反馈与客服回复">
      <template #extra>
        <KyButton type="link" @click="showCreate = !showCreate">{{ showCreate ? '取消' : '新建' }}</KyButton>
      </template>
    </PageHeader>

    <KyStack gap="md">
      <KyCard v-if="showCreate" title="新建工单">
        <KyForm @finish="createTicket">
          <KyFormItem label="标题">
            <KyInput v-model="createTitle" placeholder="简要描述问题" size="large" />
          </KyFormItem>
          <KyFormItem label="内容">
            <KyTextarea v-model="createContent" :rows="4" placeholder="详细说明问题" />
          </KyFormItem>
          <KyFormItem label="优先级">
            <KySelect v-model="createPriority" size="large" :options="priorityOptions" />
          </KyFormItem>
          <KyButton type="primary" html-type="submit" block size="large" :loading="creating">提交工单</KyButton>
        </KyForm>
      </KyCard>

      <KySpin :spinning="loading">
        <KyEmpty v-if="!loading && tickets.length === 0 && !showCreate" description="暂无工单，点击右上角新建">
          <KyButton type="primary" @click="showCreate = true">新建工单</KyButton>
        </KyEmpty>
        <KyStack v-else-if="tickets.length > 0" gap="sm">
          <button
            v-for="item in tickets"
            :key="item.id"
            type="button"
            class="ticket-card"
            @click="selectTicket(item)"
          >
            <div class="ticket-card__main">
              <p class="ticket-card__title">{{ item.title }}</p>
              <p class="ticket-card__meta">
                {{ ticketStatusLabel(item.status) }} · {{ ticketPriorityLabel(item.priority) }}
              </p>
            </div>
            <span class="ticket-card__time">{{ item.created_at || '' }}</span>
          </button>
        </KyStack>
      </KySpin>
    </KyStack>

    <KyDrawer v-model:open="detailOpen" title="工单详情" width="480px">
      <template v-if="selected">
        <p class="ticket-title">{{ selected.title }}</p>
        <StatusBadge :text="ticketStatusLabel(selected.status)" variant="info" />
        <p class="muted" style="margin-top: var(--ky-space-md)">{{ selected.content }}</p>
        <KyDivider />
        <div v-for="reply in selected.replies || []" :key="reply.id" class="reply">
          <p>{{ reply.content }}</p>
          <span class="muted">{{ reply.created_at }}</span>
        </div>
        <KyForm style="margin-top: var(--ky-space-lg)" @finish="submitReply">
          <KyFormItem label="回复">
            <KyTextarea v-model="replyContent" :rows="3" />
          </KyFormItem>
          <KyButton type="primary" html-type="submit" block size="large" :loading="replying" :disabled="!replyContent.trim()">
            发送回复
          </KyButton>
        </KyForm>
      </template>
    </KyDrawer>
  </KyPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import KyStack from '@/components/KyStack.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import {
  KyButton,
  KyDivider,
  KyDrawer,
  KyEmpty,
  KyForm,
  KyFormItem,
  KyInput,
  KySelect,
  KySpin,
  KyTextarea,
} from '@/components/ky'
import { clientApi, type TicketItem } from '@/api/client'
import { ticketPriorityLabel, ticketStatusLabel } from '@/lib/format'

const route = useRoute()
const loading = ref(false)
const creating = ref(false)
const replying = ref(false)
const showCreate = ref(false)
const tickets = ref<TicketItem[]>([])
const selected = ref<TicketItem | null>(null)
const detailOpen = ref(false)
const createTitle = ref('')
const createContent = ref('')
const createPriority = ref('normal')
const replyContent = ref('')

const priorityOptions = [
  { label: '普通', value: 'normal' },
  { label: '高', value: 'high' },
  { label: '紧急', value: 'urgent' },
]

async function load() {
  loading.value = true
  try {
    tickets.value = (await clientApi.getTickets()).data.tickets
  } finally {
    loading.value = false
  }
}

async function createTicket() {
  if (!createTitle.value.trim() || !createContent.value.trim()) {
    message.warning('请填写标题和内容')
    return
  }
  creating.value = true
  try {
    await clientApi.createTicket({
      title: createTitle.value.trim(),
      content: createContent.value.trim(),
      priority: createPriority.value,
    })
    message.success('工单已提交')
    showCreate.value = false
    createTitle.value = ''
    createContent.value = ''
    await load()
  } finally {
    creating.value = false
  }
}

async function selectTicket(item: TicketItem) {
  selected.value = (await clientApi.getTicket(item.id)).data
  detailOpen.value = true
  replyContent.value = ''
}

async function submitReply() {
  if (!selected.value || !replyContent.value.trim()) return
  replying.value = true
  try {
    await clientApi.addTicketReply(selected.value.id, replyContent.value.trim())
    message.success('回复已发送')
    selected.value = (await clientApi.getTicket(selected.value.id)).data
    replyContent.value = ''
    await load()
  } finally {
    replying.value = false
  }
}

onMounted(() => {
  if (route.query.create === '1') {
    showCreate.value = true
  }
  void load()
})
</script>

<style scoped>
.ticket-title {
  margin: 0;
  font-size: var(--ky-font-lg);
  font-weight: 600;
  color: var(--ky-text);
}

.muted {
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.reply {
  padding: var(--ky-space-sm) 0;
  border-bottom: 1px solid var(--ky-border-soft);
}

.ticket-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-md);
  width: 100%;
  padding: var(--ky-space-md) var(--ky-space-lg);
  border: 1px solid var(--ky-border);
  border-radius: var(--ky-radius-lg);
  background: var(--ky-bg-card);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.ticket-card:hover {
  border-color: var(--ky-border-strong);
  background: var(--ky-bg-card-hover);
}

.ticket-card__main {
  min-width: 0;
  flex: 1;
}

.ticket-card__title {
  margin: 0;
  font-size: var(--ky-font-md);
  font-weight: 600;
  color: var(--ky-text);
}

.ticket-card__meta {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.ticket-card__time {
  flex-shrink: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-hint);
}
</style>
