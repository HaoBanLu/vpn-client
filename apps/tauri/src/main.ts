import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { revealAppWindow } from '@/lib/desktop/window-boot'
import { bootstrapAppDebugFromStorage } from '@/lib/debug/app-debug-log'
import './style.css'
import './styles/ky-ui.css'

bootstrapAppDebugFromStorage()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')

async function revealUi() {
  await router.isReady()
  await nextTick()
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  })
  await revealAppWindow()
}

void revealUi()

// 兜底：避免主窗一直隐藏
window.setTimeout(() => {
  void revealAppWindow()
}, 12000)
