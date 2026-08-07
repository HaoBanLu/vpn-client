<template>
  <KyPage sub>
    <PageHeader :title="'USDT 充值'" :subtitle="pageSubtitle">
      <template #extra>
        <KyButton type="link" @click="router.push({ name: 'RechargeOrders' })">充值记录</KyButton>
      </template>
    </PageHeader>

    <KySpin :spinning="loading">
      <KyCard highlight>
        <p class="label">当前余额</p>
        <p class="balance">{{ formatMoney(account.user?.balance ?? 0) }}</p>
        <div v-if="usdtConfig" class="rate-tags">
          <KyTag color="blue">汇率 1U = ¥{{ usdtConfig.exchange_rate }}</KyTag>
          <KyTag color="cyan">预计到账 {{ formatMoney(amountUsdt * usdtConfig.exchange_rate) }}</KyTag>
          <KyTag :color="isAutoMode ? 'green' : 'orange'">{{ isAutoMode ? '自动确认' : '人工审核' }}</KyTag>
        </div>
      </KyCard>

      <KyAlert v-if="!usdtEnabled" type="warning" message="USDT 充值暂未开放" show-icon />

      <template v-else-if="!activeOrder">
        <KyCard title="选择充值金额">
          <KySpace wrap>
            <KyButton
              v-for="amount in quickAmounts"
              :key="amount"
              :type="Math.abs(amountUsdt - amount) < 0.01 ? 'primary' : 'default'"
              @click="amountUsdt = amount"
            >
              {{ amount }} U
            </KyButton>
          </KySpace>
          <div class="amount-input-row">
            <KyInputNumber
              v-model="amountUsdt"
              :min="usdtConfig?.min_recharge_usdt || 1"
              :max="usdtConfig?.max_recharge_usdt || 10000"
              :step="1"
            />
            <span class="amount-suffix">USDT</span>
          </div>
          <KyButton type="primary" block size="large" style="margin-top: var(--ky-space-lg)" :loading="submitting" @click="createOrder">
            创建充值单
          </KyButton>
        </KyCard>
      </template>

      <template v-else>
        <KyCard title="充值进度">
          <KyTag color="processing">{{ rechargeStatusLabel(activeOrder.status, activeOrder.chain_auto_confirmed, isAutoMode) }}</KyTag>
          <p class="muted">{{ statusHint }}</p>
          <p class="muted">单号 {{ activeOrder.order_no }}</p>
        </KyCard>

        <KyCard title="第 1 步 · 转账">
          <p class="muted">向以下 TRC20 地址转账 {{ activeOrder.requested_usdt }} USDT</p>
          <p class="mono">{{ activeOrder.receive_address }}</p>
          <KyButton block size="large" class="ky-btn-block" style="margin-top: var(--ky-space-sm)" @click="copyAddress">
            复制收款地址
          </KyButton>
          <p v-if="usdtConfig?.confirm_tips" class="muted" style="margin-top: var(--ky-space-sm)">{{ usdtConfig.confirm_tips }}</p>
        </KyCard>

        <template v-if="activeOrder.status === 'pending_transfer' && isAutoMode">
          <KyCard title="等待自动确认">
            <KySpin inline />
            <p style="margin-top: var(--ky-space-sm)">
              转账后系统约每 {{ scanIntervalSeconds }} 秒检测链上到账，无需上传截图。
            </p>
            <p class="muted">到账后余额将自动增加，可在「充值记录」查看结果。</p>
          </KyCard>
          <KyCollapse style="background: transparent">
            <KyCollapsePanel header="选填：加速匹配（地址 / txid / 截图）">
              <KyInput
                v-model="fromAddress"
                placeholder="付款钱包地址 (选填)"
                size="large"
                style="margin-bottom: var(--ky-space-sm)"
              />
              <input ref="fileInput" type="file" accept="image/*" hidden @change="onPickProof" />
              <KyButton block size="large" class="ky-btn-block" :loading="uploadingProof" @click="fileInput?.click()">
                {{ proofFileName || '上传转账截图（选填）' }}
              </KyButton>
              <KyInput v-model="txid" placeholder="交易哈希（选填）" size="large" style="margin-top: var(--ky-space-sm)" />
              <KyButton
                block
                size="large"
                style="margin-top: var(--ky-space-md)"
                :loading="submitting"
                @click="saveTransferHint"
              >
                保存加速匹配
              </KyButton>
            </KyCollapsePanel>
          </KyCollapse>
          <KyButton type="link" block @click="cancelOrder">取消充值单</KyButton>
        </template>

        <KyCard v-else-if="activeOrder.status === 'pending_transfer'" title="第 2 步 · 提交凭证">
          <p class="muted">转账后上传截图，并填写付款钱包地址</p>
          <KyInput
            v-model="fromAddress"
            placeholder="付款钱包地址 (TRC20)"
            size="large"
            style="margin-bottom: var(--ky-space-sm)"
          />
          <input ref="fileInput" type="file" accept="image/*" hidden @change="onPickProof" />
          <KyButton block size="large" class="ky-btn-block" :loading="uploadingProof" @click="fileInput?.click()">
            {{ proofFileName || '上传转账截图' }}
          </KyButton>
          <KyInput v-model="txid" placeholder="交易哈希（选填）" size="large" style="margin-top: var(--ky-space-sm)" />
          <KyButton
            type="primary"
            block
            size="large"
            style="margin-top: var(--ky-space-md)"
            :loading="submitting"
            :disabled="!proofImageUrl"
            @click="submitProof"
          >
            提交审核
          </KyButton>
          <KyButton type="link" block @click="cancelOrder">取消充值单</KyButton>
        </KyCard>

        <KyCard v-else>
          <template v-if="activeOrder.status === 'submitted'">
            <p>{{ isAutoMode ? '正在确认到账，可在「充值记录」查看结果' : '等待人工审核，可在「充值记录」查看结果' }}</p>
          </template>
          <template v-else-if="activeOrder.status === 'paid'">
            <p v-if="activeOrder.credited_cny">
              {{ activeOrder.chain_auto_confirmed ? '已自动确认到账' : '已到账' }}
              {{ formatMoney(activeOrder.credited_cny) }}
            </p>
            <p v-if="activeOrder.paid_at" class="muted">到账时间：{{ activeOrder.paid_at }}</p>
          </template>
          <template v-else-if="activeOrder.status === 'rejected'">
            <KyAlert type="error" :message="activeOrder.reject_reason || '充值被驳回'" show-icon />
            <KyButton type="primary" block size="large" style="margin-top: var(--ky-space-md)" @click="restart">重新发起充值</KyButton>
          </template>
          <template v-else>
            <p>{{ rechargeStatusLabel(activeOrder.status, activeOrder.chain_auto_confirmed, isAutoMode) }}</p>
          </template>
        </KyCard>
      </template>
    </KySpin>
  </KyPage>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from '@/lib/ui/message'
import KyPage from '@/components/KyPage.vue'
import KyCard from '@/components/KyCard.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  KyAlert,
  KyButton,
  KyCollapse,
  KyCollapsePanel,
  KyInput,
  KyInputNumber,
  KySpace,
  KySpin,
  KyTag,
} from '@/components/ky'
import { clientApi, type RechargeOrderItem, type USDTConfig } from '@/api/client'
import { formatMoney, rechargeStatusLabel } from '@/lib/format'
import { useAccountStore } from '@/stores/account'

const router = useRouter()
const account = useAccountStore()

const loading = ref(false)
const submitting = ref(false)
const uploadingProof = ref(false)
const usdtEnabled = ref(false)
const usdtConfig = ref<USDTConfig | null>(null)
const amountUsdt = ref(50)
const activeOrder = ref<RechargeOrderItem | null>(null)
const fromAddress = ref('')
const txid = ref('')
const proofImageUrl = ref<string | null>(null)
const proofFileName = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
let pollTimer: number | null = null

const quickAmounts = computed(() => usdtConfig.value?.quick_amounts_usdt || [10, 20, 50, 100, 200])

const isAutoMode = computed(() => {
  if (!usdtConfig.value) return true
  if (usdtConfig.value.confirm_mode === 'manual') return false
  if (usdtConfig.value.confirm_mode === 'auto') return true
  return usdtConfig.value.auto_confirm_enabled !== false
})

const scanIntervalSeconds = computed(() => usdtConfig.value?.scan_interval_seconds || 60)

const pageSubtitle = computed(() => (isAutoMode.value ? 'TRC20 转账，自动确认到账' : 'TRC20 转账，人工审核入账'))

const pollIntervalMs = computed(() => Math.max(5000, Math.floor(scanIntervalSeconds.value / 2) * 1000))

const statusHint = computed(() => {
  if (!activeOrder.value) return ''
  if (activeOrder.value.status === 'pending_transfer' && isAutoMode.value) {
    return `请转账，约 ${scanIntervalSeconds.value} 秒检测一次`
  }
  return ''
})

function shouldPoll(order: RechargeOrderItem | null) {
  if (!order) return false
  if (order.status === 'submitted') return true
  return isAutoMode.value && order.status === 'pending_transfer'
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    if (!activeOrder.value) return
    try {
      const res = await clientApi.getRechargeOrder(activeOrder.value.id)
      activeOrder.value = res.data
      await account.refreshAccount()
      if (['paid', 'rejected', 'expired', 'cancelled'].includes(res.data.status)) {
        stopPolling()
        if (res.data.status === 'paid') {
          message.success(res.data.chain_auto_confirmed ? '已自动确认到账' : '充值已到账')
        }
        await load()
      }
    } catch {
      // ignore
    }
  }, pollIntervalMs.value)
}

async function load() {
  loading.value = true
  try {
    const [methods, orders] = await Promise.all([
      clientApi.getPaymentMethods(),
      clientApi.getRechargeOrders(),
      account.refreshAccount(),
    ])
    usdtEnabled.value = methods.data.usdt_enabled
    usdtConfig.value = methods.data.usdt || null
    if (usdtConfig.value?.quick_amounts_usdt?.[0]) {
      amountUsdt.value = usdtConfig.value.quick_amounts_usdt[0]
    }
    activeOrder.value =
      orders.data.orders.find((o) => ['pending_transfer', 'submitted'].includes(o.status)) || null
    if (shouldPoll(activeOrder.value)) {
      startPolling()
    } else {
      stopPolling()
    }
  } finally {
    loading.value = false
  }
}

async function createOrder() {
  submitting.value = true
  try {
    const res = await clientApi.createRechargeOrder(amountUsdt.value)
    activeOrder.value = res.data.order
    message.success(isAutoMode.value ? '充值单已创建，请转账后等待自动确认' : '充值单已创建')
    if (isAutoMode.value) {
      startPolling()
    }
    await load()
  } finally {
    submitting.value = false
  }
}

async function onPickProof(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingProof.value = true
  try {
    const res = await clientApi.uploadRechargeProof(file)
    proofImageUrl.value = res.data.url
    proofFileName.value = file.name
    message.success('截图上传成功')
  } finally {
    uploadingProof.value = false
    input.value = ''
  }
}

async function saveTransferHint() {
  if (!activeOrder.value) return
  if (!fromAddress.value.trim() && !proofImageUrl.value && !txid.value.trim()) {
    message.warning('请至少填写一项信息')
    return
  }
  submitting.value = true
  try {
    const res = await clientApi.saveRechargeTransferHint(activeOrder.value.id, {
      from_address: fromAddress.value.trim() || undefined,
      proof_image_url: proofImageUrl.value || undefined,
      txid: txid.value.trim() || undefined,
    })
    activeOrder.value = res.data
    message.success('已保存，系统将加速匹配到账')
    startPolling()
  } finally {
    submitting.value = false
  }
}

async function submitProof() {
  if (!activeOrder.value || !proofImageUrl.value || !fromAddress.value.trim()) {
    message.warning('请填写付款地址并上传截图')
    return
  }
  submitting.value = true
  try {
    const res = await clientApi.submitRechargeOrder(activeOrder.value.id, {
      from_address: fromAddress.value.trim(),
      proof_image_url: proofImageUrl.value,
      txid: txid.value.trim() || undefined,
    })
    activeOrder.value = res.data
    message.success('已提交审核')
    startPolling()
  } finally {
    submitting.value = false
  }
}

async function cancelOrder() {
  if (!activeOrder.value) return
  submitting.value = true
  try {
    await clientApi.cancelRechargeOrder(activeOrder.value.id)
    activeOrder.value = null
    proofImageUrl.value = null
    proofFileName.value = null
    stopPolling()
    message.success('充值单已取消')
  } finally {
    submitting.value = false
  }
}

function restart() {
  activeOrder.value = null
  proofImageUrl.value = null
  proofFileName.value = null
  fromAddress.value = ''
  txid.value = ''
  stopPolling()
}

async function copyAddress() {
  if (!activeOrder.value) return
  await navigator.clipboard.writeText(activeOrder.value.receive_address)
  message.success('已复制收款地址')
}

onMounted(load)
onUnmounted(stopPolling)
</script>

<style scoped>
.label {
  margin: 0;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.balance {
  margin: var(--ky-space-xs) 0 0;
  font-size: var(--ky-font-2xl);
  font-weight: 700;
  color: var(--ky-accent);
}

.rate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ky-space-sm);
  margin-top: var(--ky-space-md);
}

.amount-input-row {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  margin-top: var(--ky-space-md);
}

.amount-input-row :deep(.ky-input-number) {
  flex: 1;
}

.amount-suffix {
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.muted {
  margin: var(--ky-space-sm) 0 0;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.mono {
  word-break: break-all;
  font-family: ui-monospace, monospace;
  margin-top: var(--ky-space-sm);
}
</style>
