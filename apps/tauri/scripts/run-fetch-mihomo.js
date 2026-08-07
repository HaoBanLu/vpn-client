/**
 * 跨平台下载桌面 mihomo：Windows → PowerShell，macOS/Linux → fetch-mihomo.sh
 */
import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.dirname(fileURLToPath(import.meta.url))
const tauriRoot = path.join(root, '..')

if (process.platform === 'win32') {
  execSync(
    'powershell -ExecutionPolicy Bypass -File scripts/fetch-mihomo.ps1',
    { cwd: tauriRoot, stdio: 'inherit' },
  )
} else {
  execSync('bash scripts/fetch-mihomo.sh', { cwd: tauriRoot, stdio: 'inherit' })
}
