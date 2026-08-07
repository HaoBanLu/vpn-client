import { describe, expect, it } from 'vitest'
import { shouldIgnoreDisconnectedWhileConnecting } from '@/lib/vpn/connect-inflight'

describe('shouldIgnoreDisconnectedWhileConnecting', () => {
  it('ignores disconnected while connectPending', () => {
    expect(
      shouldIgnoreDisconnectedWhileConnecting({
        connectPending: true,
        connectionState: 'disconnected',
        isSwitching: false,
        userInitiatedDisconnect: false,
        nextState: 'disconnected',
      }),
    ).toBe(true)
  })

  it('ignores disconnected while connectionState is connecting', () => {
    expect(
      shouldIgnoreDisconnectedWhileConnecting({
        connectPending: false,
        connectionState: 'connecting',
        isSwitching: false,
        userInitiatedDisconnect: false,
        nextState: 'disconnected',
      }),
    ).toBe(true)
  })

  it('applies disconnected after user interrupt', () => {
    expect(
      shouldIgnoreDisconnectedWhileConnecting({
        connectPending: true,
        connectionState: 'connecting',
        isSwitching: false,
        userInitiatedDisconnect: true,
        nextState: 'disconnected',
      }),
    ).toBe(false)
  })

  it('applies connected / failed normally', () => {
    expect(
      shouldIgnoreDisconnectedWhileConnecting({
        connectPending: true,
        connectionState: 'connecting',
        isSwitching: false,
        userInitiatedDisconnect: false,
        nextState: 'connected',
      }),
    ).toBe(false)
  })
})
