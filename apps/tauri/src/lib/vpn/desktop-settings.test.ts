import { describe, expect, it, beforeEach } from 'vitest'
import {
  DESKTOP_SETTINGS_KEYS,
  DESKTOP_MVP_PROXY_ONLY,
  effectiveConnectionMode,
  effectiveKillSwitchEnabled,
  loadDesktopSettings,
} from './desktop-settings'

describe('desktop-settings MVP', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('forces proxy mode and disables kill switch when MVP flag on', () => {
    localStorage.setItem(DESKTOP_SETTINGS_KEYS.connectionMode, 'tun')
    localStorage.setItem(DESKTOP_SETTINGS_KEYS.killSwitch, '1')
    const settings = loadDesktopSettings()
    if (DESKTOP_MVP_PROXY_ONLY) {
      expect(settings.connectionMode).toBe('proxy')
      expect(settings.killSwitch).toBe(false)
      expect(effectiveConnectionMode()).toBe('proxy')
      expect(effectiveKillSwitchEnabled()).toBe(false)
    }
  })
})
