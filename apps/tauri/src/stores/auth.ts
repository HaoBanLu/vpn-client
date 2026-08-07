import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { message } from '@/lib/ui/message'
import { clientApi, type UserBrief } from '@/api/client'
import { disconnectVpn } from '@/lib/vpn/bridge'
import { effectiveKillSwitchEnabled } from '@/lib/vpn/desktop-settings'
import { appendDebugLog } from '@/lib/debug/app-debug-log'
import { acceptPrivacy, ensurePrivacyAcceptedIfLoggedIn } from '@/lib/app-meta'
import { saveLoginCredentials } from '@/lib/login-credentials'

const TOKEN_KEY = 'tauri_token'
const USER_KEY = 'tauri_user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserBrief | null>(
    localStorage.getItem(USER_KEY) ? JSON.parse(localStorage.getItem(USER_KEY)!) : null,
  )

  const isAuthenticated = computed(() => !!token.value)

  function persistSession(session: { token: string; user: UserBrief }) {
    token.value = session.token
    user.value = session.user
    localStorage.setItem(TOKEN_KEY, session.token)
    localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  }

  async function login(email: string, password: string, rememberLogin = true) {
    const res = await clientApi.login(email, password)
    persistSession({ token: res.data.token, user: res.data.user })
    saveLoginCredentials(rememberLogin, email, password)
    ensurePrivacyAcceptedIfLoggedIn(true)
    message.success('登录成功')
  }

  async function register(email: string, password: string, emailCode?: string) {
    const res = await clientApi.register({
      email,
      password,
      email_code: emailCode,
    })
    persistSession({ token: res.data.token, user: res.data.user })
    acceptPrivacy()
    message.success('注册成功')
  }

  async function logout(options?: { silent?: boolean; skipVpn?: boolean }) {
    if (!options?.skipVpn) {
      try {
        appendDebugLog('auth', '用户退出登录', 'info')
        await disconnectVpn({ userInitiated: false, killSwitchEnabled: effectiveKillSwitchEnabled() })
      } catch {
        // ignore when VPN backend unavailable in browser dev
      }
    }
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    if (!options?.silent) {
      message.success('已退出登录')
    }
  }

  return { token, user, isAuthenticated, login, register, logout }
})
