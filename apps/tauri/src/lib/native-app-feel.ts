/** 移动端去掉网页手感：长按选中、系统菜单、双指缩放由 CSS/原生 WebView 一起禁。输入框仍可选择。 */
export function installNativeAppFeel() {
  const ua = navigator.userAgent.toLowerCase()
  if (!ua.includes('android') && !ua.includes('iphone') && !ua.includes('ipad')) return

  const allowSelect = (el: EventTarget | null) => {
    if (!(el instanceof Element)) return false
    return !!el.closest('input, textarea, [contenteditable="true"]')
  }

  document.addEventListener(
    'contextmenu',
    (e) => {
      if (allowSelect(e.target)) return
      e.preventDefault()
    },
    { capture: true },
  )

  document.addEventListener(
    'selectstart',
    (e) => {
      if (allowSelect(e.target)) return
      e.preventDefault()
    },
    { capture: true },
  )

  document.addEventListener(
    'dragstart',
    (e) => {
      if (allowSelect(e.target)) return
      e.preventDefault()
    },
    { capture: true },
  )
}
