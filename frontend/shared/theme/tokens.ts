/**
 * 跨云设计 Token（与 apps/tauri/src/style.css、会员中心 soft-blue 对齐）
 * 视觉以 style.css 中 :root CSS 变量为准；此处供 TS 逻辑（断点、布局判断）使用。
 */
export const kyTokens = {
  color: {
    bg: '#f8fafc',
    bgElevated: '#ffffff',
    bgCard: '#ffffff',
    bgCardHover: '#f1f5f9',
    bgInput: '#ffffff',
    surfaceVariant: '#f1f5f9',
    onPrimaryContainer: '#1e3a8a',
    border: '#e2e8f0',
    text: '#0f172a',
    textMuted: '#64748b',
    accent: '#3b82f6',
    accentSoft: '#60a5fa',
    accentDeep: '#2563eb',
    /** Logo / 品牌渐变起点（非按钮主色） */
    accentBrand: '#1b4dff',
    accentCyan: '#00a8e8',
    success: '#10b981',
    warning: '#f59e0b',
    danger: '#e11d48',
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
