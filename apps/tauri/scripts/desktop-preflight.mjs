#!/usr/bin/env node
/**
 * 桌面 MVP 发版前检查：Mihomo 二进制、关键配置、前端单测入口。
 * 用法：cd apps/tauri && node scripts/desktop-preflight.mjs
 */
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const binDir = join(root, 'src-tauri', 'resources', 'bin')
const platform = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'macos' : 'linux'
const exeName =
  process.platform === 'win32'
    ? 'mihomo.exe'
    : process.platform === 'darwin'
      ? 'mihomo'
      : 'mihomo'
const mihomoPath = join(binDir, exeName)

let failed = false

function ok(msg) {
  console.log(`✓ ${msg}`)
}

function fail(msg) {
  console.error(`✗ ${msg}`)
  failed = true
}

const nodeModules = join(root, 'node_modules')
const vitestBin = join(nodeModules, 'vitest', 'vitest.mjs')

if (!existsSync(nodeModules)) {
  fail('未安装依赖：请在 apps/tauri 目录执行 npm install（或 npm run setup）')
} else {
  ok('node_modules 已安装')
}

if (existsSync(mihomoPath)) {
  ok(`Mihomo 二进制: ${exeName}`)
} else {
  fail(`缺少 Mihomo 二进制: ${mihomoPath}（运行 npm run fetch:mihomo）`)
}

const settingsPath = join(root, 'src', 'lib', 'vpn', 'desktop-settings.ts')
if (existsSync(settingsPath)) {
  ok('desktop-settings.ts 存在')
} else {
  fail('缺少 desktop-settings.ts')
}

console.log(`\n平台: ${platform}`)
console.log('建议下一步: npm run fetch:mihomo && npm run tauri:win:dev')
console.log('验收: 登录 → 连接 → 确认系统代理与出口 IP\n')

const test = existsSync(vitestBin)
  ? spawnSync(process.execPath, [vitestBin, 'run', 'src/lib/vpn/session-throughput.test.ts'], {
      cwd: root,
      stdio: 'inherit',
    })
  : { status: 1 }

if (!existsSync(vitestBin)) {
  fail('vitest 未安装（先执行 npm install）')
} else if (test.status !== 0) {
  fail('session-throughput 单测未通过（或 npm 不可用）')
} else {
  ok('session-throughput 单测通过')
}

process.exit(failed ? 1 : 0)
