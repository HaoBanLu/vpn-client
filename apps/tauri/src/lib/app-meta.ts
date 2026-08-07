export const APP_VERSION_NAME = '1.2.10'
export const APP_VERSION_CODE = 130
export const PRIVACY_ACCEPTED_KEY = 'tauri_privacy_accepted'

/** 注册勾选条款后调用，对齐 Android AppRepository.acceptPrivacy。 */
export function acceptPrivacy() {
  if (localStorage.getItem(PRIVACY_ACCEPTED_KEY)) return
  localStorage.setItem(PRIVACY_ACCEPTED_KEY, '1')
}

/** 已登录老用户迁移：补写隐私同意，不增 UI。 */
export function ensurePrivacyAcceptedIfLoggedIn(isLoggedIn: boolean) {
  if (!isLoggedIn || localStorage.getItem(PRIVACY_ACCEPTED_KEY)) return
  acceptPrivacy()
}

export function detectClientPlatform(): string {
  const ua = navigator.userAgent.toLowerCase()
  if (ua.includes('android')) return 'android'
  if (ua.includes('iphone') || ua.includes('ipad')) return 'ios'
  if (ua.includes('mac')) return 'macos'
  if (ua.includes('win')) return 'windows'
  if (ua.includes('linux')) return 'linux'
  return 'tauri'
}
