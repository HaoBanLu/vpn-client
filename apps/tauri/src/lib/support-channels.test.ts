import { describe, expect, it } from 'vitest'
import type { SupportChannelItem } from '@/api/client'
import {
  firstTelegramChannel,
  parseOrdersTab,
  secondarySupportChannels,
  supportChannelShortTitle,
  supportMenuSubtitle,
  telegramButtonHint,
} from './support-channels'

function ch(type: string, label = '', url = 'https://t.me/x'): SupportChannelItem {
  return { type, label, url }
}

describe('support channels', () => {
  it('picks the first telegram as primary', () => {
    const channels = [ch('telegram_group', '群'), ch('telegram', '小助手'), ch('email', '邮箱')]
    expect(firstTelegramChannel(channels)?.label).toBe('小助手')
  })

  it('keeps remaining channels after lifting telegram', () => {
    const channels = [ch('telegram', '客服'), ch('telegram_group', '群'), ch('ticket', '工单')]
    expect(secondarySupportChannels(channels).map((item) => item.type)).toEqual(['telegram_group'])
  })

  it('uses short titles for secondary list', () => {
    expect(supportChannelShortTitle(ch('telegram_group'))).toBe('群组')
    expect(supportChannelShortTitle(ch('telegram_channel'))).toBe('频道')
    expect(supportChannelShortTitle(ch('email'))).toBe('邮箱')
  })

  it('falls back telegram hint and menu subtitle', () => {
    expect(telegramButtonHint(ch('telegram'))).toBe('将打开 Telegram')
    expect(telegramButtonHint(ch('telegram', '值班客服'))).toBe('值班客服')
    expect(supportMenuSubtitle([ch('telegram')])).toBe('Telegram 私聊客服')
    expect(supportMenuSubtitle([ch('email')])).toBe('邮箱 / 工单')
  })
})

describe('parseOrdersTab', () => {
  it('defaults to recharge', () => {
    expect(parseOrdersTab(undefined)).toBe('recharge')
    expect(parseOrdersTab('recharge')).toBe('recharge')
    expect(parseOrdersTab('purchase')).toBe('purchase')
    expect(parseOrdersTab('other')).toBe('recharge')
  })
})
