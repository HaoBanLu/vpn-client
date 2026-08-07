import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { DEFAULT_API_BASE_URL } from './src/lib/api-config'

const host = process.env.TAURI_DEV_HOST

function resolveDevProxyTarget(envValue?: string): string {
  const raw = envValue?.trim() || DEFAULT_API_BASE_URL
  if (raw.startsWith('/')) {
    return DEFAULT_API_BASE_URL.replace(/\/api\/?$/, '')
  }
  return raw.replace(/\/api\/?$/, '')
}

export default defineConfig(({ mode }) => {
  const envFromFile = loadEnv(mode, process.cwd(), '')
  // 以项目 dotenv 为准，覆盖系统残留（如旧 sqginx 域名）
  if (envFromFile.VITE_API_BASE_URL) {
    process.env.VITE_API_BASE_URL = envFromFile.VITE_API_BASE_URL
  }
  const resolvedApi = process.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL
  console.log(`[vite] mode=${mode} VITE_API_BASE_URL=${resolvedApi}`)

  const devApiBase = envFromFile.VITE_API_BASE_URL || ''
  const useDevProxy = mode === 'development' && devApiBase.startsWith('/')

  return {
  plugins: [vue()],
  clearScreen: false,
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@shared': resolve(__dirname, '../../frontend/shared'),
    },
  },
  server: {
    port: 5173,
    strictPort: false,
    host: host || '127.0.0.1',
    // Docker MCP 浏览器经 host.docker.internal 访问时需要放行
    allowedHosts: ['host.docker.internal', 'localhost', '127.0.0.1'],
    hmr: host
      ? {
          protocol: 'ws',
          host,
          port: 1421,
        }
      : undefined,
    watch: {
      ignored: ['**/src-tauri/**'],
    },
    proxy: useDevProxy
      ? {
          '/api': {
            target: resolveDevProxyTarget(envFromFile.VITE_API_BASE_URL),
            changeOrigin: true,
          },
        }
      : undefined,
  },
  envPrefix: ['VITE_', 'TAURI_'],
  build: {
    target: process.env.TAURI_PLATFORM === 'windows' ? 'chrome105' : 'safari13',
    minify: !process.env.TAURI_DEBUG ? 'esbuild' : false,
    sourcemap: !!process.env.TAURI_DEBUG,
    outDir: 'dist',
  },
  }
})
