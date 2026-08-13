<template>
  <KySubPage title="USDT 充值">
    <template #extra>
      <KyButton type="link" @click="router.push({ name: 'RechargeOrders' })">充值记录</KyButton>
    </template>

    <KySpin :spinning="loading && !usdtConfig" overlay>
      <div class="balance-hero">
        <p class="label">当前余额</p>
        <p class="balance">{{ formatMoney(account.user?.balance ?? 0) }}</p>
        <div v-if="usdtConfig" class="rate-tags">
          <span class="pill pill--info">汇率 1U = ¥{{ usdtConfig.exchange_rate }}</span>
          <span class="pill pill--info">预计到账 {{ formatMoney(amountUsdt * usdtConfig.exchange_rate) }}</span>
          <span class="pill" :class="isAutoMode ? 'pill--ok' : 'pill--warn'">
            {{ isAutoMode ? '自动确认' : '人工审核' }}
          </span>
        </div>
      </div>

      <KyAlert v-if="!usdtEnabled" type="warning" message="USDT 充值暂未开放" show-icon />

      <template v-else-if="!activeOrder">
        <div class="network-notice network-notice--compact">
          <p class="network-notice__title">仅支持 TRC20（USDT-TRON）</p>
          <p class="network-notice__body">请确保钱包选择 TRC20 网络</p>
        </div>

        <div class="amount-card">
          <p class="amount-card__title">选择充值金额</p>
          <div class="preset-row">
            <button
              v-for="amount in quickAmounts"
              :key="amount"
              type="button"
              class="preset-btn"
              :class="{ 'preset-btn--active': Math.abs(amountUsdt - amount) < 0.01 }"
              @click="amountUsdt = amount"
            >
              {{ amount }} U
            </button>
          </div>
          <div class="amount-input-row">
            <KyInputNumber
              v-model="amountUsdt"
              :min="usdtConfig?.min_recharge_usdt || 1"
              :max="usdtConfig?.max_recharge_usdt || 10000"
              :step="1"
            />
            <span class="amount-suffix">USDT</span>
          </div>
          <KyButton type="primary" block size="large" class="create-btn" :loading="submitting" @click="createOrder">
            创建充值单
          </KyButton>
        </div>
      </template>

      <template v-else>
        <!-- OrderStatusStrip -->
        <div class="order-status-strip">
          <div class="order-status-strip__top">
            <div>
              <p class="order-status-strip__label">订单状态</p>
              <p class="order-status-strip__status">
                {{ rechargeStatusLabel(activeOrder.status, activeOrder.chain_auto_confirmed, isAutoMode) }}
              </p>
            </div>
            <span class="network-chip">TRC20</span>
          </div>
          <div class="order-status-strip__divider" />
          <p class="muted">单号 {{ activeOrder.order_no }}</p>
          <p v-if="activeOrder.expired_at" class="expire-row">
            <span class="expire-icon" aria-hidden="true">⏱</span>
            请在 {{ formatExpireShort(activeOrder.expired_at) }} 前完成转账
          </p>
          <p v-if="isAutoMode && activeOrder.status === 'pending_transfer'" class="auto-hint">
            转账完成后无需操作，系统将自动确认入账
          </p>
          <p v-if="statusHint" class="muted">{{ statusHint }}</p>
        </div>

        <!-- TransferHeroCard -->
        <div class="transfer-hero">
          <p class="transfer-hero__label">应付金额</p>
          <p class="transfer-hero__amount">{{ formatUsdtAmount(activeOrder.requested_usdt) }} USDT</p>
          <p class="transfer-hero__cny">
            预计到账 {{ formatMoney((activeOrder.requested_usdt || 0) * (usdtConfig?.exchange_rate || 0)) }}
          </p>
          <p class="transfer-hero__tip">
            {{ isAutoMode ? '请按此金额转账，系统将自动匹配' : '请按此金额转账后提交凭证' }}
          </p>
        </div>

        <!-- NetworkNoticeCard -->
        <div class="network-notice">
          <p class="network-notice__title">仅支持 TRC20（USDT-TRON）</p>
          <ul class="network-notice__bullets">
            <li>必须使用 TRC20 网络，勿用 ERC20 / BEP20 等其他链</li>
            <li>仅转入 USDT，其他币种或网络将无法找回</li>
            <li>转账金额须与订单金额一致（允许极小误差）</li>
          </ul>
          <p v-if="usdtConfig?.confirm_tips" class="network-notice__custom">{{ usdtConfig.confirm_tips }}</p>
        </div>

        <!-- Address + QR -->
        <div class="address-card">
          <p class="amount-card__title">收款地址</p>
          <div v-if="qrDataUrl" class="qr-wrap">
            <img :src="qrDataUrl" alt="USDT 收款二维码" class="qr-img" width="180" height="180" />
            <p class="muted">使用钱包扫描二维码转账</p>
          </div>
          <p class="mono">{{ activeOrder.receive_address }}</p>
          <KyButton block size="large" class="copy-btn" @click="copyAddress">复制收款地址</KyButton>
        </div>

        <template v-if="activeOrder.status === 'pending_transfer' && isAutoMode">
          <div class="wait-card">
            <KySpin inline />
            <p class="wait-card__title">等待自动确认</p>
            <p class="muted">
              转账后系统约每 {{ scanIntervalSeconds }} 秒检测链上到账，无需上传截图。
            </p>
          </div>
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

        <div v-else-if="activeOrder.status === 'pending_transfer'" class="amount-card">
          <p class="amount-card__title">第 2 步 · 提交凭证</p>
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
        </div>

        <div v-else class="amount-card">
          <template v-if="activeOrder.status === 'submitted'">
            <p>{{ isAutoMode ? '正在确认到账，可在「充值记录」查看结果' : '等待人工审核，可在「充值记录」查看结果' }}</p>
          </template>
          <template v-else-if="activeOrder.status === 'paid'">
            <p v-if="activeOrder.credited_cny">
              {{ activeOrder.chain_auto_confirmed ? '已自动确认到账' : '已到账' }}
              {{ formatMoney(activeOrder.credited_cny) }}
            </p>
            <p v-if="activeOrder.paid_at" class="muted">到账时间：{{ formatExpireShort(activeOrder.paid_at) }}</p>
          </template>
          <template v-else-if="activeOrder.status === 'rejected'">
            <KyAlert type="error" :message="activeOrder.reject_reason || '充值被驳回'" show-icon />
            <KyButton type="primary" block size="large" style="margin-top: var(--ky-space-md)" @click="restart">
              重新发起充值
            </KyButton>
          </template>
          <template v-else>
            <p>{{ rechargeStatusLabel(activeOrder.status, activeOrder.chain_auto_confirmed, isAutoMode) }}</p>
            <KyButton
              v-if="['expired', 'cancelled'].includes(activeOrder.status)"
              type="primary"
              block
              size="large"
              style="margin-top: var(--ky-space-md)"
              @click="restart"
            >
              重新发起充值
            </KyButton>
          </template>
        </div>
      </template>
    </KySpin>
  </KySubPage>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { message } from '@/lib/ui/message'
import KySubPage from '@/components/KySubPage.vue'
import {
  KyAlert,
  KyButton,
  KyCollapse,
  KyCollapsePanel,
  KyInput,
  KyInputNumber,
  KySpin,
} from '@/components/ky'
import { clientApi, type RechargeOrderItem, type USDTConfig } from '@/api/client'
import { formatExpireShort, formatMoney, formatUsdtAmount, rechargeStatusLabel } from '@/lib/format'
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
const qrDataUrl = ref<string | null>(null)
let pollTimer: number | null = null

const quickAmounts = computed(() => usdtConfig.value?.quick_amounts_usdt || [10, 50, 100, 200])

const isAutoMode = computed(() => {
  if (!usdtConfig.value) return true
  if (usdtConfig.value.confirm_mode === 'manual') return false
  if (usdtConfig.value.confirm_mode === 'auto') return true
  return usdtConfig.value.auto_confirm_enabled !== false
})

const scanIntervalSeconds = computed(() => usdtConfig.value?.scan_interval_seconds || 60)

const pollIntervalMs = computed(() => Math.max(5000, Math.floor(scanIntervalSeconds.value / 2) * 1000))

const statusHint = computed(() => {
  if (!activeOrder.value) return ''
  if (activeOrder.value.status === 'pending_transfer' && isAutoMode.value) {
    return `请转账，约 ${scanIntervalSeconds.value} 秒检测一次`
  }
  return ''
})

watch(
  () => activeOrder.value?.receive_address,
  async (address) => {
    qrDataUrl.value = null
    if (!address) return
    try {
      qrDataUrl.value = await QRCode.toDataURL(address, {
        width: 180,
        margin: 1,
        color: { dark: '#0f1729', light: '#ffffff' },
      })
    } catch {
      qrDataUrl.value = null
    }
  },
  { immediate: true },
)

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
.balance-hero {
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(214, 228, 255, 0.95), rgba(232, 238, 248, 0.9));
  border: 1px solid rgba(27, 77, 255, 0.08);
  margin-bottom: 14px;
}

.label {
  margin: 0;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.balance {
  margin: 4px 0 0;
  font-size: 32px;
  font-weight: 700;
  color: var(--ky-accent);
  line-height: 1.15;
}

.rate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 650;
}

.pill--info {
  background: rgba(27, 77, 255, 0.1);
  color: var(--ky-accent);
}

.pill--ok {
  background: rgba(76, 175, 80, 0.14);
  color: #2e7d32;
}

.pill--warn {
  background: rgba(255, 152, 0, 0.16);
  color: #e65100;
}

.amount-card,
.address-card,
.wait-card,
.order-status-strip {
  padding: 16px;
  border-radius: 16px;
  background: var(--ky-bg-card);
  border: 1px solid var(--ky-border-soft);
  margin-bottom: 12px;
}

.amount-card__title {
  margin: 0 0 12px;
  font-size: var(--ky-font-md);
  font-weight: 700;
  color: var(--ky-text);
}

.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-btn {
  appearance: none;
  min-width: 68px;
  height: 36px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--ky-border);
  background: #fff;
  color: var(--ky-text);
  font-size: var(--ky-font-sm);
  font-weight: 600;
  cursor: pointer;
}

.preset-btn--active {
  border-color: transparent;
  background: var(--ky-accent);
  color: #fff;
}

.amount-input-row {
  display: flex;
  align-items: center;
  gap: var(--ky-space-sm);
  margin-top: 14px;
}

.amount-input-row :deep(.ky-input-number) {
  flex: 1;
}

.amount-suffix {
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.create-btn,
.copy-btn {
  margin-top: 16px;
  height: 44px !important;
  border-radius: 12px !important;
  font-weight: 650;
}

.muted {
  margin: var(--ky-space-sm) 0 0;
  color: var(--ky-text-muted);
  font-size: var(--ky-font-sm);
}

.mono {
  word-break: break-all;
  font-family: ui-monospace, monospace;
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--ky-text);
  line-height: 1.45;
}

.order-status-strip__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.order-status-strip__label {
  margin: 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.order-status-strip__status {
  margin: 2px 0 0;
  font-size: var(--ky-font-md);
  font-weight: 650;
  color: var(--ky-accent);
}

.order-status-strip__divider {
  height: 1px;
  background: var(--ky-border-soft);
  margin: 10px 0;
}

.network-chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(27, 77, 255, 0.1);
  color: var(--ky-accent);
  font-size: 12px;
  font-weight: 650;
}

.expire-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 6px 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-text-muted);
}

.expire-icon {
  font-size: 12px;
}

.auto-hint {
  margin: 8px 0 0;
  font-size: var(--ky-font-sm);
  color: var(--ky-accent);
}

.transfer-hero {
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(214, 228, 255, 0.55);
  text-align: center;
  margin-bottom: 12px;
}

.transfer-hero__label {
  margin: 0;
  font-size: var(--ky-font-sm);
  font-weight: 600;
  color: #1a2b5c;
}

.transfer-hero__amount {
  margin: 4px 0 0;
  font-size: 28px;
  font-weight: 750;
  color: var(--ky-accent);
  line-height: 1.2;
}

.transfer-hero__cny {
  margin: 4px 0 0;
  font-size: var(--ky-font-sm);
  color: #1a2b5c;
}

.transfer-hero__tip {
  margin: 8px 0 0;
  font-size: var(--ky-font-xs);
  color: rgba(26, 43, 92, 0.85);
}

.network-notice {
  padding: 14px;
  border-radius: 12px;
  background: rgba(248, 113, 113, 0.12);
  margin-bottom: 12px;
}

.network-notice--compact {
  margin-bottom: 14px;
}

.network-notice__title {
  margin: 0;
  font-size: var(--ky-font-sm);
  font-weight: 700;
  color: var(--ky-danger);
}

.network-notice__body {
  margin: 4px 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.network-notice__bullets {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: var(--ky-font-xs);
  color: var(--ky-text);
  line-height: 1.55;
}

.network-notice__custom {
  margin: 8px 0 0;
  font-size: var(--ky-font-xs);
  color: var(--ky-text-muted);
}

.qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.qr-img {
  border-radius: 12px;
  border: 1px solid var(--ky-border-soft);
  background: #fff;
}

.wait-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.wait-card__title {
  margin: 0;
  font-weight: 700;
  color: var(--ky-text);
}
</style>
