import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const uploadAppDebugLogs = vi.fn()

vi.mock('@/api/client', () => ({
  clientApi: {
    uploadAppDebugLogs: (...args: unknown[]) => uploadAppDebugLogs(...args),
  },
}))

vi.mock('@/lib/app-meta', () => ({
  APP_VERSION_NAME: '1.2.18',
  APP_VERSION_CODE: 138,
  detectClientPlatform: () => 'android',
}))

describe('app-debug-log auto upload', () => {
  beforeEach(async () => {
    vi.resetModules()
    uploadAppDebugLogs.mockReset()
    uploadAppDebugLogs.mockResolvedValue({ data: { accepted: 1 } })
    localStorage.clear()
    localStorage.setItem('tauri_token', 'test-token')
    const mod = await import('./app-debug-log')
    mod.__resetAppDebugForTests()
  })

  afterEach(async () => {
    const mod = await import('./app-debug-log')
    mod.__resetAppDebugForTests()
    vi.useRealTimers()
  })

  it('sanitizes tokens and passwords', async () => {
    const { sanitizeDebugText } = await import('./app-debug-log')
    expect(sanitizeDebugText('Bearer abc.def.ghi')).toContain('[redacted]')
    expect(sanitizeDebugText('password=secret123')).toContain('[redacted]')
  })

  it('does not auto-upload when debug disabled', async () => {
    const { appendDebugLog, configureAppDebug, __pendingCountForTests, flushDebugLogs } =
      await import('./app-debug-log')
    configureAppDebug(false)
    appendDebugLog('connect', '开始连接', 'error')
    expect(__pendingCountForTests()).toBe(0)
    await flushDebugLogs()
    expect(uploadAppDebugLogs).not.toHaveBeenCalled()
  })

  it('queues and immediately flushes warn/error when enabled', async () => {
    const { appendDebugLog, configureAppDebug } = await import('./app-debug-log')
    configureAppDebug(true)
    appendDebugLog('connect', '连接失败：timeout', 'error', { node: '新加坡-普通线路' })
    await vi.waitFor(() => expect(uploadAppDebugLogs).toHaveBeenCalled())
    const body = uploadAppDebugLogs.mock.calls[0][0]
    expect(body.device_id).toBe('tauri-android')
    expect(body.entries[0].category).toBe('connect')
    expect(body.entries[0].context.node).toBe('新加坡-普通线路')
  })

  it('debounces info logs for 30s', async () => {
    vi.useFakeTimers()
    const { appendDebugLog, configureAppDebug } = await import('./app-debug-log')
    configureAppDebug(true)
    appendDebugLog('connect', '开始连接', 'info')
    expect(uploadAppDebugLogs).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(uploadAppDebugLogs).toHaveBeenCalledTimes(1)
  })
})
