let revealed = false

function isTauri(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}

/**
 * 主界面就绪后通知 Rust：显示主窗口。
 * 主窗口在配置里 visible:false，避免 WebView 未渲染完时出现白闪。
 */
export async function revealAppWindow(): Promise<void> {
  if (revealed) return
  revealed = true

  if (!isTauri()) return

  try {
    const { invoke } = await import('@tauri-apps/api/core')
    await invoke('boot_reveal_main')
  } catch (e) {
    console.warn('[boot] boot_reveal_main failed', e)
    revealed = false
  }
}
