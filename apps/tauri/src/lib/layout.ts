import { kyTokens } from '@shared/theme/tokens'
import { detectClientPlatform } from '@/lib/app-meta'

const DESKTOP_PLATFORMS = new Set(['windows', 'macos', 'linux', 'tauri'])

/** 桌面端布局断点（与 PRD、shared tokens 一致） */
export const DESKTOP_BREAKPOINT = kyTokens.layout.desktopBreakpoint

export function isDesktopPlatform(): boolean {
  return DESKTOP_PLATFORMS.has(detectClientPlatform())
}

export function shouldUseDesktopLayout(viewportWidth: number): boolean {
  if (isDesktopPlatform() && viewportWidth >= 768) return true
  return viewportWidth >= DESKTOP_BREAKPOINT
}
