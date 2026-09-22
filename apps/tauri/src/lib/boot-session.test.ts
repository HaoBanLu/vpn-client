import { describe, expect, it } from 'vitest'
import {
  clearUpgradeApiGrace,
  isInUpgradeApiGrace,
  markUpgradeApiGrace,
  shouldDropLeftoverTunnelOnLaunch,
  UPGRADE_API_GRACE_UNTIL_KEY,
} from './boot-session'

describe('shouldDropLeftoverTunnelOnLaunch', () => {
  it('does not drop on same version even if vpn is up', () => {
    expect(
      shouldDropLeftoverTunnelOnLaunch({
        previousVersionCode: '143',
        currentVersionCode: 143,
        vpnActive: true,
      }),
    ).toBe(false)
  })

  it('drops leftover vpn after overlay install when tunnel is still up', () => {
    expect(
      shouldDropLeftoverTunnelOnLaunch({
        previousVersionCode: null,
        currentVersionCode: 143,
        vpnActive: true,
      }),
    ).toBe(true)
    expect(
      shouldDropLeftoverTunnelOnLaunch({
        previousVersionCode: '142',
        currentVersionCode: 143,
        vpnActive: true,
      }),
    ).toBe(true)
  })

  it('drops after version bump even if JS vpn state is disconnected', () => {
    expect(
      shouldDropLeftoverTunnelOnLaunch({
        previousVersionCode: '143',
        currentVersionCode: 144,
        vpnActive: false,
      }),
    ).toBe(true)
  })

  it('drops when system vpn is still present on same version', () => {
    expect(
      shouldDropLeftoverTunnelOnLaunch({
        previousVersionCode: '144',
        currentVersionCode: 144,
        vpnActive: false,
        systemVpnActive: true,
      }),
    ).toBe(true)
  })

  it('does not drop on first install without vpn', () => {
    expect(
      shouldDropLeftoverTunnelOnLaunch({
        previousVersionCode: null,
        currentVersionCode: 143,
        vpnActive: false,
      }),
    ).toBe(false)
  })
})

describe('upgrade api grace', () => {
  it('marks and clears grace window', () => {
    const data: Record<string, string> = {}
    const storage = {
      getItem: (key: string) => data[key] ?? null,
      setItem: (key: string, value: string) => {
        data[key] = value
      },
      removeItem: (key: string) => {
        delete data[key]
      },
      clear: () => {
        for (const key of Object.keys(data)) delete data[key]
      },
      key: () => null,
      get length() {
        return Object.keys(data).length
      },
    } satisfies Storage
    const now = 1_000_000
    markUpgradeApiGrace(now, storage)
    expect(isInUpgradeApiGrace(now + 1_000, storage)).toBe(true)
    expect(isInUpgradeApiGrace(now + 100_000, storage)).toBe(false)
    expect(Number(storage.getItem(UPGRADE_API_GRACE_UNTIL_KEY))).toBe(now + 90_000)
    clearUpgradeApiGrace(storage)
    expect(isInUpgradeApiGrace(now + 1_000, storage)).toBe(false)
  })
})
