<template>
  <KyPage sub>
    <PageHeader title="规则直连" subtitle="匹配规则的流量不经代理，将暴露真实 IP" />

    <KyAlert
      type="warning"
      message="启用规则直连后，匹配域名/IP 的流量将绕过 VPN，请谨慎添加。"
      show-icon
    />

    <KyButton type="primary" block @click="openAddDialog">添加规则</KyButton>

    <KyCard v-if="rules.length === 0" flat class="empty-card">
      <KyEmpty description="暂无规则直连" />
    </KyCard>

    <KyCard v-for="rule in rules" :key="rule.id" flat class="rule-card">
      <div class="rule-row">
        <div class="rule-copy">
          <KyTag>{{ typeLabel(rule.type) }}</KyTag>
          <span class="rule-value">{{ rule.value }}</span>
        </div>
        <div class="rule-actions">
          <KySwitch :checked="rule.enabled" @update:checked="(v) => onToggle(rule.id, v)" />
          <KyButton type="text" danger size="small" @click="onDelete(rule.id)">删除</KyButton>
        </div>
      </div>
    </KyCard>

    <KyModal
      :open="addVisible"
      title="添加规则直连"
      ok-text="添加"
      @update:open="(v) => (addVisible = v)"
      @ok="onAddOk"
    >
      <KyFormItem label="规则类型">
        <KySelect v-model="addType" :options="typeOptions" />
      </KyFormItem>
      <KyFormItem label="规则内容">
        <KyInput v-model="addValue" placeholder="例如 example.com 或 192.168.1.0/24" />
      </KyFormItem>
      <KyAlert v-if="addError" type="error" :message="addError" show-icon />
    </KyModal>
  </KyPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  KyAlert,
  KyButton,
  KyEmpty,
  KyFormItem,
  KyInput,
  KyModal,
  KySelect,
  KySwitch,
  KyTag,
} from '@/components/ky'
import {
  createDirectBypassRule,
  DIRECT_BYPASS_TYPE_META,
  loadDirectBypassRules,
  saveDirectBypassRules,
  type DirectBypassRule,
  type DirectBypassRuleTypeName,
  validateDirectBypassRule,
} from '@/lib/vpn/direct-bypass-rule'
import { Modal } from '@/lib/ui/confirm'
import { message } from '@/lib/ui/message'
import { useConnectStore } from '@/stores/connect'

const connect = useConnectStore()

const rules = ref<DirectBypassRule[]>(loadDirectBypassRules())
const addVisible = ref(false)
const addType = ref<DirectBypassRuleTypeName>('DOMAIN_SUFFIX')
const addValue = ref('')
const addError = ref<string | null>(null)

const typeOptions = Object.entries(DIRECT_BYPASS_TYPE_META).map(([key, meta]) => ({
  label: meta.label,
  value: key,
}))

function typeLabel(type: DirectBypassRuleTypeName) {
  return DIRECT_BYPASS_TYPE_META[type]?.label ?? type
}

function persist(next: DirectBypassRule[]) {
  saveDirectBypassRules(next)
  rules.value = next
  if (connect.isConnected) {
    void connect.reconnect('正在应用规则直连…')
    message.success('规则已更新，正在重连以生效')
    return
  }
  message.success('规则已保存')
}

function openAddDialog() {
  addType.value = 'DOMAIN_SUFFIX'
  addValue.value = ''
  addError.value = null
  addVisible.value = true
}

function confirmEnableRule(id: string) {
  persist(rules.value.map((r) => (r.id === id ? { ...r, enabled: true } : r)))
  message.success('规则已启用')
}

function confirmAddRule() {
  const rule = createDirectBypassRule(addType.value, addValue.value)
  persist([...rules.value, rule])
  message.success('规则已添加')
  addValue.value = ''
}

function onAddOk() {
  addError.value = null
  try {
    validateDirectBypassRule(addType.value, addValue.value)
  } catch (e: unknown) {
    addError.value = e instanceof Error ? e.message : '规则无效'
    addVisible.value = true
    return
  }
  Modal.confirm({
    title: '隐私风险提示',
    content: '规则直连会让匹配流量绕过 VPN，可能暴露真实 IP。确定添加？',
    okText: '仍要添加',
    onOk: () => confirmAddRule(),
  })
}

function onToggle(id: string, enabled: boolean) {
  if (!enabled) {
    persist(rules.value.map((r) => (r.id === id ? { ...r, enabled: false } : r)))
    return
  }
  Modal.confirm({
    title: '隐私风险提示',
    content: '启用后匹配流量将绕过 VPN，可能暴露真实 IP。确定启用？',
    okText: '仍要启用',
    onOk: () => confirmEnableRule(id),
  })
}

function onDelete(id: string) {
  Modal.confirm({
    title: '删除规则',
    content: '确定删除此规则直连？',
    okText: '删除',
    onOk: () => {
      persist(rules.value.filter((r) => r.id !== id))
      message.success('已删除')
    },
  })
}
</script>

<style scoped>
.empty-card,
.rule-card {
  margin-top: var(--ky-space-sm);
}

.rule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ky-space-md);
}

.rule-copy {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  flex-wrap: wrap;
  min-width: 0;
}

.rule-value {
  font-size: var(--ky-font-sm);
  color: var(--ky-text);
  word-break: break-all;
}

.rule-actions {
  display: flex;
  align-items: center;
  gap: var(--ky-space-xs);
  flex-shrink: 0;
}
</style>
