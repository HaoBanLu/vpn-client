import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
// 首屏同步导入 Login/Main/Connect，避免启动后再串行拉分包。
// 桌面启动：主窗 visible:false，Vue 就绪后 boot_reveal_main 再 show。
import LoginView from '@/views/auth/LoginView.vue'
import MainShell from '@/layouts/MainShell.vue'
import ConnectView from '@/views/connect/ConnectView.vue'

const profileChildRoutes = [
  {
    path: 'recharge',
    name: 'Recharge',
    component: () => import('@/views/profile/RechargeView.vue'),
  },
  {
    path: 'recharge-orders',
    name: 'RechargeOrders',
    component: () => import('@/views/profile/RechargeOrdersView.vue'),
  },
  {
    path: 'purchase-orders',
    name: 'PurchaseOrders',
    component: () => import('@/views/profile/PurchaseOrdersView.vue'),
  },
  {
    path: 'traffic',
    name: 'Traffic',
    component: () => import('@/views/profile/TrafficView.vue'),
  },
  {
    path: 'change-password',
    name: 'ChangePassword',
    component: () => import('@/views/profile/ChangePasswordView.vue'),
  },
  {
    path: 'tickets',
    name: 'Tickets',
    component: () => import('@/views/profile/TicketsView.vue'),
  },
  {
    path: 'devices',
    name: 'Devices',
    component: () => import('@/views/profile/DevicesView.vue'),
  },
  {
    path: 'support',
    name: 'Support',
    component: () => import('@/views/profile/SupportView.vue'),
  },
  {
    path: 'help',
    name: 'Help',
    component: () => import('@/views/profile/HelpView.vue'),
  },
  {
    path: 'about',
    name: 'About',
    component: () => import('@/views/profile/AboutView.vue'),
  },
  {
    path: 'stability-settings',
    name: 'StabilitySettings',
    component: () => import('@/views/profile/StabilitySettingsView.vue'),
  },
  {
    path: 'app-direct-connect',
    name: 'AppDirectConnect',
    component: () => import('@/views/profile/AppDirectConnectView.vue'),
  },
  {
    path: 'direct-bypass-rules',
    name: 'DirectBypassRules',
    component: () => import('@/views/profile/DirectBypassRulesView.vue'),
  },
  {
    path: 'debug-log',
    name: 'DebugLog',
    component: () => import('@/views/profile/DebugLogView.vue'),
  },
] as const

const legacyProfileRedirects = profileChildRoutes.map((route) => ({
  path: `/${route.path}`,
  redirect: { name: route.name },
}))

function homeRouteName(): 'Connect' | 'Login' {
  return useAuthStore().isAuthenticated ? 'Connect' : 'Login'
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: () => ({ name: homeRouteName() }),
    },
    {
      // 兼容旧深链；按登录态跳转
      path: '/splash',
      redirect: () => ({ name: homeRouteName() }),
    },
    {
      path: '/privacy',
      name: 'Privacy',
      component: () => import('@/views/PrivacyView.vue'),
      meta: { public: true },
    },
    {
      path: '/login',
      name: 'Login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/forgot-password',
      name: 'ForgotPassword',
      component: () => import('@/views/auth/ForgotPasswordView.vue'),
      meta: { public: true },
    },
    {
      path: '/main',
      component: MainShell,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/main/connect' },
        {
          path: 'connect',
          name: 'Connect',
          component: ConnectView,
        },
        {
          path: 'nodes',
          name: 'Nodes',
          component: () => import('@/views/nodes/NodesView.vue'),
        },
        {
          path: 'packages',
          name: 'Packages',
          component: () => import('@/views/packages/PackagesView.vue'),
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/profile/ProfileView.vue'),
        },
        ...profileChildRoutes,
      ],
    },
    ...legacyProfileRedirects,
  ],
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    next({ name: 'Login' })
    return
  }
  if ((to.name === 'Login' || to.name === 'Register') && auth.isAuthenticated) {
    next({ name: 'Connect' })
    return
  }
  next()
})

export default router
