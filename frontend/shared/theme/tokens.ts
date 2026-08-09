/**
 * 跨云设计 Token（与 Android KuayunTheme LightColors、apps/tauri/src/style.css 对齐）
 * 视觉以 style.css 中 :root CSS 变量为准；此处供 TS 逻辑（断点、布局判断）使用。
 */
export const kyTokens = {
  color: {
    bg: '#f4f7fc',
    bgElevated: '#ffffff',
    bgCard: '#ffffff',
    bgCardHover: '#eef3fb',
    bgInput: '#ffffff',
    surfaceVariant: '#e8eef8',
    onPrimaryContainer: '#0a2463',
    border: '#c5d0e0',
    text: '#0f1729',
    textMuted: '#5a6b82',
    accent: '#1b4dff',
    accentSoft: '#4f7cff',
    accentDeep: '#1b4dff',
    accentCyan: '#00d4ff',
    success: '#2e7d32',
    warning: '#f57c00',
    danger: '#d32f2f',
    onAccent: '#ffffff',
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
