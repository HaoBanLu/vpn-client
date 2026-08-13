import type { SupportChannelItem } from '@/api/client'

const SHORT_TITLES: Record<string, string> = {
  telegram: '客服',
  telegram_group: '群组',
  telegram_channel: '频道',
  email: '邮箱',
  web: '网页',
}

export function firstTelegramChannel(
  channels: SupportChannelItem[] | null | undefined,
): SupportChannelItem | null {
  return (channels ?? []).find((item) => item.type === 'telegram') ?? null
}

export function secondarySupportChannels(
  channels: SupportChannelItem[] | null | undefined,
): SupportChannelItem[] {
  const list = (channels ?? []).filter((item) => item.type !== 'ticket')
  const index = list.findIndex((item) => item.type === 'telegram')
  if (index < 0) return list
  return [...list.slice(0, index), ...list.slice(index + 1)]
}

export function supportChannelShortTitle(item: SupportChannelItem): string {
  return SHORT_TITLES[item.type] || item.label?.trim() || item.type
}

export function telegramButtonHint(item: SupportChannelItem): string {
  return item.label?.trim() || '将打开 Telegram'
}

export function supportMenuSubtitle(channels: SupportChannelItem[] | null | undefined): string {
  if (firstTelegramChannel(channels)) return 'Telegram 私聊客服'
  return '邮箱 / 工单'
}

export type OrdersTab = 'recharge' | 'purchase'

export function parseOrdersTab(value: unknown): OrdersTab {
  return value === 'purchase' ? 'purchase' : 'recharge'
}
