<template>
  <KyPage sub>
    <PageHeader title="诊断日志" subtitle="本地连接与 VPN 事件记录，可上传供客服排查" />

    <div class="toolbar">
      <KyButton :loading="uploading" @click="upload">上传日志</KyButton>
      <KyButton danger :disabled="entries.length === 0" @click="clear">清空</KyButton>
    </div>

    <KyAlert
      v-if="uploadMessage"
      type="success"
      :message="uploadMessage"
      show-icon
      closable
      @close="uploadMessage = null"
    />
    <KyAlert
      v-if="uploadError"
      type="error"
      :message="uploadError"
      show-icon
      closable
      @close="uploadError = null"
    />

    <KyCard v-if="entries.length === 0" flat>
      <KyEmpty description="暂无诊断日志" />
    </KyCard>

    <KyCard v-for="entry in entries" :key="entry.ts + entry.tag + entry.message" flat class="log-card">
      <div class="log-head">
        <KyTag :color="levelColor(entry.level)">{{ entry.level }}</KyTag>
        <span class="log-tag">{{ entry.tag }}</span>
        <span class="log-time">{{ formatTime(entry.ts) }}</span>
      </div>
      <p class="log-message">{{ entry.message }}</p>
    </KyCard>
  </KyPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { KyAlert, KyButton, KyEmpty, KyTag } from '@/components/ky'
import {
  clearDebugLogs,
  listDebugLogs,
  uploadDebugLogs,
  type AppDebugLogEntry,
} from '@/lib/debug/app-debug-log'
import { Modal } from '@/lib/ui/confirm'
import { message } from '@/lib/ui/message'

const entries = ref<AppDebugLogEntry[]>([])
const uploading = ref(false)
const uploadMessage = ref<string | null>(null)
const uploadError = ref<string | null>(null)

function refresh() {
  entries.value = listDebugLogs()
}

function formatTime(ts: string) {
  return dayjs(ts).format('MM-DD HH:mm:ss')
}

function levelColor(level: AppDebugLogEntry['level']) {
  if (level === 'error') return 'error'
  if (level === 'warn') return 'warning'
  return 'default'
}

async function upload() {
  if (entries.value.length === 0) {
    message.info('暂无日志可上传')
    return
  }
  uploading.value = true
  uploadMessage.value = null
  uploadError.value = null
  try {
    const accepted = await uploadDebugLogs()
    uploadMessage.value = `已上传 ${accepted} 条日志`
  } catch (e: unknown) {
    uploadError.value = e instanceof Error ? e.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

function clear() {
  Modal.confirm({
    title: '清空诊断日志',
    content: '确定清空本地全部诊断日志？此操作不可恢复。',
    okText: '清空',
    onOk: () => {
      clearDebugLogs()
      refresh()
      message.success('已清空')
    },
  })
}

onMounted(refresh)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: var(--ky-space-sm);
  margin-bottom: var(--ky-space-md);
}

.log-card {
  margin-bottom: var(--ky-space-sm);
}

.log-head {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  flex-wrap: wrap;
}

.log-tag {
  font-size: var(--ky-font-xs);
  color: var(--ky-text-secondary);
  font-weight: 600;
}

.log-time {
  margin-left: auto;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.log-message {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text);
  line-height: 1.5;
  word-break: break-word;
}
</style>
