/**
 * 跨云设计 Token（与 Android KuayunTheme、apps/tauri/src/style.css 对齐）
 * 视觉以 style.css 中 :root CSS 变量为准；此处供 TS 逻辑（断点、布局判断）使用。
 */
export const kyTokens = {
  color: {
    bg: '#0a0e17',
    bgElevated: '#141b2d',
    bgCard: '#1a2338',
    bgCardHover: '#202a42',
    bgInput: '#0f1726',
    border: '#2a3548',
    text: '#e8edf5',
    textMuted: '#9aa8bc',
    accent: '#00d4ff',
    accentDeep: '#1b4dff',
    success: '#4ade80',
    warning: '#fbbf24',
    danger: '#f87171',
  },
  radius: {
    sm: 8,
    md: 12,
    lg: 18,
    xl: 20,
    full: 999,
  },
  space: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 20,
    xl: 24,
    '2xl': 32,
  },
  font: {
    xs: 11,
    sm: 14,
    md: 14,
    lg: 16,
    xl: 24,
    '2xl': 26,
  },
  layout: {
    pageMaxWidth: 460,
    pageMaxWidthDesktop: 960,
    sideNavWidth: 220,
    bottomNavHeight: 62,
    desktopBreakpoint: 960,
    tabletBreakpoint: 768,
  },
} as const

export type KyTokenColor = keyof typeof kyTokens.color
