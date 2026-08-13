import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { message } from '@/lib/ui/message'
import { clientApi, type UserBrief } from '@/api/client'
import { disconnectVpn } from '@/lib/vpn/bridge'
import { effectiveKillSwitchEnabled } from '@/lib/vpn/desktop-settings'
import { appendDebugLog, configureAppDebug, flushDebugLogs } from '@/lib/debug/app-debug-log'
import { acceptPrivacy, ensurePrivacyAcceptedIfLoggedIn } from '@/lib/app-meta'
import { saveLoginCredentials } from '@/lib/login-credentials'
import { isNetworkConnectivityError } from '@/lib/api-error'

const TOKEN_KEY = 'tauri_token'
const USER_KEY = 'tauri_user'

function readStoredUser(): UserBrief | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw || raw === 'undefined' || raw === 'null') return null
  try {
    return JSON.parse(raw) as UserBrief
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserBrief | null>(readStoredUser())

  const isAuthenticated = computed(() => !!token.value)

  function persistSession(session: { token: string; user: UserBrief }) {
    token.value = session.token
    user.value = session.user
    localStorage.setItem(TOKEN_KEY, session.token)
    localStorage.setItem(USER_KEY, JSON.stringify(session.user ?? null))
    configureAppDebug(Boolean(session.user?.app_debug_enabled))
  }

  async function ensureTunnelRecovered() {
    try {
      const { useConnectStore } = await import('@/stores/connect')
      const connect = useConnectStore()
      await connect.initVpnBridge()
      await connect.recoverAfterAppUpdate()
    } catch {
      // 浏览器开发或插件未就绪时忽略
    }
  }

  async function withTunnelRetry<T>(reason: string, run: () => Promise<T>): Promise<T> {
    await ensureTunnelRecovered()
    try {
      return await run()
    } catch (error) {
      if (!isNetworkConnectivityError(error)) throw error
      try {
        const { useConnectStore } = await import('@/stores/connect')
        await useConnectStore().dropLeftoverTunnel(reason)
      } catch {
        // ignore
      }
      return await run()
    }
  }

  async function login(email: string, password: string, rememberLogin = true) {
    const res = await withTunnelRetry('login_blocked', () => clientApi.login(email, password))
    persistSession({ token: res.data.token, user: res.data.user })
    saveLoginCredentials(rememberLogin, email, password)
    ensurePrivacyAcceptedIfLoggedIn(true)
    message.success('登录成功')
  }

  async function register(email: string, password: string, emailCode?: string) {
    const res = await withTunnelRetry('register_blocked', () =>
      clientApi.register({
        email,
        password,
        email_code: emailCode,
      }),
    )
    persistSession({ token: res.data.token, user: res.data.user })
    acceptPrivacy()
    message.success('注册成功')
  }

  async function logout(options?: { silent?: boolean; skipVpn?: boolean }) {
    if (!options?.skipVpn) {
      try {
        appendDebugLog('auth', '用户退出登录', 'info')
        await flushDebugLogs()
        await disconnectVpn({ userInitiated: false, killSwitchEnabled: effectiveKillSwitchEnabled() })
      } catch {
        // ignore when VPN backend unavailable in browser dev
      }
    }
    configureAppDebug(false)
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    try {
      const { useAccountStore } = await import('@/stores/account')
      useAccountStore().reset()
    } catch {
      // pinia 未就绪时忽略
    }
    if (!options?.silent) {
      message.success('已退出登录')
    }
  }

  return { token, user, isAuthenticated, login, register, logout }
})
