import { describe, expect, it } from 'vitest'
import {
  decideDesktopNetworkRestore,
  DESKTOP_NETWORK_RESTORE,
  nextDesktopHealthFailStreak,
  shouldProceedDesktopAutoReconnect,
  shouldReconnectAfterDesktopNetworkRecovery,
  shouldReconnectOnDesktopHealthStreak,
} from './network-restore-policy'

describe('network-restore-policy', () => {
  it('online while connected → schedule_reconnect (align Android 3.15.7)', () => {
    expect(
      decideDesktopNetworkRestore({
        connectionState: 'connected',
        userInitiatedDisconnect: false,
        autoReconnectEnabled: true,
      }),
    ).toBe('schedule_reconnect')
  })

  it('online while disconnected/failed/connecting → schedule_reconnect', () => {
    for (const connectionState of ['disconnected', 'failed', 'connecting'] as const) {
      expect(
        decideDesktopNetworkRestore({
          connectionState,
          userInitiatedDisconnect: false,
          autoReconnectEnabled: true,
        }),
      ).toBe('schedule_reconnect')
    }
  })

  it('user disconnect → none', () => {
    expect(
      decideDesktopNetworkRestore({
        connectionState: 'connected',
        userInitiatedDisconnect: true,
        autoReconnectEnabled: true,
      }),
    ).toBe('none')
  })

  it('auto-reconnect off + connected → heal only', () => {
    expect(
      decideDesktopNetworkRestore({
        connectionState: 'connected',
        userInitiatedDisconnect: false,
        autoReconnectEnabled: false,
      }),
    ).toBe('heal')
    expect(
      decideDesktopNetworkRestore({
        connectionState: 'failed',
        userInitiatedDisconnect: false,
        autoReconnectEnabled: false,
      }),
    ).toBe('none')
  })

  it('proxy path gate still available for heal fallback', () => {
    expect(
      shouldReconnectAfterDesktopNetworkRecovery({
        navigatorOnline: true,
        proxyBasicOk: false,
        proxyOverseasOk: false,
      }),
    ).toBe(true)
    expect(
      shouldReconnectAfterDesktopNetworkRecovery({
        navigatorOnline: true,
        proxyBasicOk: true,
        proxyOverseasOk: true,
      }),
    ).toBe(false)
    expect(
      shouldReconnectAfterDesktopNetworkRecovery({
        navigatorOnline: false,
        proxyBasicOk: false,
        proxyOverseasOk: false,
      }),
    ).toBe(false)
  })

  it('health streak resets offline and escalates online', () => {
    expect(
      nextDesktopHealthFailStreak({
        navigatorOnline: false,
        probeFailed: true,
        previousStreak: 5,
      }),
    ).toBe(0)
    expect(
      nextDesktopHealthFailStreak({
        navigatorOnline: true,
        probeFailed: true,
        previousStreak: 0,
      }),
    ).toBe(1)
    expect(
      shouldReconnectOnDesktopHealthStreak({
        navigatorOnline: true,
        failStreak: DESKTOP_NETWORK_RESTORE.healthFailStreakToReconnect,
      }),
    ).toBe(true)
    expect(
      shouldReconnectOnDesktopHealthStreak({
        navigatorOnline: true,
        failStreak: 1,
      }),
    ).toBe(false)
  })

  it('debounce constant aligns with Android', () => {
    expect(DESKTOP_NETWORK_RESTORE.reconnectDebounceMs).toBe(1_500)
  })

  it('auto reconnect waits without network', () => {
    expect(shouldProceedDesktopAutoReconnect(false)).toBe(false)
    expect(shouldProceedDesktopAutoReconnect(true)).toBe(true)
  })
})
