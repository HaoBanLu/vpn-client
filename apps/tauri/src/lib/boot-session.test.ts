import { describe, expect, it } from 'vitest'
import { shouldDropLeftoverTunnelOnLaunch } from './boot-session'

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
