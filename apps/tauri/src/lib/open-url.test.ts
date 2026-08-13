import { describe, expect, it } from 'vitest'
import { telegramAppUrl } from './open-url'

describe('telegramAppUrl', () => {
  it('maps public username to tg://', () => {
    expect(telegramAppUrl('https://t.me/kuayun_support')).toBe('tg://resolve?domain=kuayun_support')
  })

  it('keeps invite links as https (returns null so caller uses original)', () => {
    expect(telegramAppUrl('https://t.me/+AbCdEf')).toBeNull()
    expect(telegramAppUrl('https://t.me/joinchat/xxxxx')).toBeNull()
  })

  it('ignores non-telegram urls', () => {
    expect(telegramAppUrl('https://example.com/help')).toBeNull()
  })
})

describe('telegram fallback contract', () => {
  it('tg:// is only a first try; https remains the fallback url', () => {
    const https = 'https://t.me/ooookay'
    const app = telegramAppUrl(https)
    expect(app).toBe('tg://resolve?domain=ooookay')
    expect(app).not.toBe(https)
  })
})
