const BOOT_STARTED_AT = Date.now()
/** 最短展示 splash，避免主界面就绪过快时闪一下；生产冷启动通常已够快 */
const MIN_BOOT_MS = 400

let revealed = false

function isTauri(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}

/**
 * 主界面就绪后通知 Rust：显示主窗口并关闭启动 Loading。
 * 主窗口在配置里 visible:false，启动过程中用户只会看到 Loading 小窗。
 */
export async function revealAppWindow(): Promise<void> {
  if (revealed) return
  revealed = true

  const remain = MIN_BOOT_MS - (Date.now() - BOOT_STARTED_AT)
  if (remain > 0) {
    await new Promise((r) => setTimeout(r, remain))
  }

  if (!isTauri()) return

  try {
    const { invoke } = await import('@tauri-apps/api/core')
    await invoke('boot_reveal_main')
  } catch (e) {
    console.warn('[boot] boot_reveal_main failed', e)
    revealed = false
  }
}
