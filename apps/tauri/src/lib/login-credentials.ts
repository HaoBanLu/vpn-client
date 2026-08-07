/** 对齐 Android TokenStore 的「记住账号密码」本地存储。 */

const REMEMBER_KEY = 'tauri_remember_login'
const EMAIL_KEY = 'tauri_saved_email'
const PASSWORD_KEY = 'tauri_saved_password'

export interface SavedLoginCredentials {
  remember: boolean
  email: string
  password: string
}

export function isRememberLoginEnabled(): boolean {
  const raw = localStorage.getItem(REMEMBER_KEY)
  if (raw === null) return true
  return raw === '1'
}

export function loadSavedLoginCredentials(): SavedLoginCredentials {
  const remember = isRememberLoginEnabled()
  if (!remember) {
    return { remember: false, email: '', password: '' }
  }
  return {
    remember: true,
    email: localStorage.getItem(EMAIL_KEY) || '',
    password: localStorage.getItem(PASSWORD_KEY) || '',
  }
}

export function saveLoginCredentials(remember: boolean, email: string, password: string) {
  localStorage.setItem(REMEMBER_KEY, remember ? '1' : '0')
  if (remember) {
    localStorage.setItem(EMAIL_KEY, email.trim())
    localStorage.setItem(PASSWORD_KEY, password)
  } else {
    localStorage.removeItem(EMAIL_KEY)
    localStorage.removeItem(PASSWORD_KEY)
  }
}
