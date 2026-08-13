<template>
  <KySubPage title="我的设备">
    <template #extra>
      <span class="quota-extra">{{ quotaUsed }}/{{ quotaMax }}</span>
    </template>
    <KySpin :spinning="loading" overlay>
      <KyEmpty v-if="!loading && !sessions.length" description="暂无登录设备" />
      <KyCard v-for="item in sessions" :key="item.session_id" flat class="device-card">
        <div class="device-row">
          <div>
            <p class="device-name">{{ item.device_model || item.device_name || '未知设备' }}</p>
            <p class="device-meta">
              <StatusBadge v-if="item.device_type" :text="item.device_type" variant="info" />
              <StatusBadge v-if="item.is_current" text="当前设备" variant="warning" />
              <StatusBadge v-if="item.is_online" text="在线" variant="success" />
              <StatusBadge
                v-if="item.ip_binding_mode === 'multi'"
                text="多IP"
                variant="info"
              />
            </p>
            <p v-if="nodeLine(item)" class="device-node">{{ nodeLine(item) }}</p>
            <p v-if="item.last_active_at" class="device-time">
              最后活跃: {{ formatTime(item.last_active_at) }}
            </p>
          </div>
          <KyButton
            v-if="!item.is_current"
            danger
            type="text"
            :loading="revokingId === item.session_id"
            @click="revoke(item.session_id)"
          >
            踢下线
          </KyButton>
        </div>
      </KyCard>
    </KySpin>
  </KySubPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from '@/lib/ui/message'
import { clientApi, type MemberSessionItem } from '@/api/client'
import KySubPage from '@/components/KySubPage.vue'
import KyCard from '@/components/KyCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { KyButton, KyEmpty, KySpin } from '@/components/ky'

const loading = ref(true)
const sessions = ref<MemberSessionItem[]>([])
const quotaUsed = ref(0)
const quotaMax = ref(1)
const revokingId = ref<string | null>(null)

function formatTime(raw: string) {
  return raw.slice(0, 16).replace('T', ' ')
}

function nodeLine(item: MemberSessionItem) {
  return [item.vpn_connected_node, item.exit_ip].filter(Boolean).join(' ')
}

async function load() {
  loading.value = true
  try {
    const data = (await clientApi.getMySessions()).data
    sessions.value = data.sessions || []
    quotaUsed.value = data.device_quota?.used ?? 0
    quotaMax.value = data.device_quota?.max ?? 1
  } catch (e) {
    message.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function revoke(sessionId: string) {
  revokingId.value = sessionId
  try {
    const data = (await clientApi.revokeMySession(sessionId)).data
    sessions.value = data.sessions || []
    quotaUsed.value = data.device_quota?.used ?? 0
    message.success('设备已踢下线')
  } catch (e) {
    message.error((e as Error).message || '操作失败')
  } finally {
    revokingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.device-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.device-name {
  font-weight: 600;
  margin: 0 0 6px;
}
.device-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 0 0 6px;
}
.device-node,
.device-time {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--ky-text-muted);
}

.quota-extra {
  font-size: 13px;
  color: var(--ky-text-muted);
  white-space: nowrap;
}
</style>
