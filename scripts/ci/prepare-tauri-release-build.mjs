#!/usr/bin/env node
/**
 * CI 发版前调整 tauri.conf.json：
 * 1) 未配置 TAURI_SIGNING_PRIVATE_KEY 时关闭 createUpdaterArtifacts
 * 2) 未配置 Apple 桌面签名时，设置 macOS signingIdentity="-"（ad-hoc），避免 CI codesign 失败
 */
import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2] ?? 'apps/tauri'
const confPath = path.join(root, 'src-tauri/tauri.conf.json')
const hasUpdaterKey = Boolean(String(process.env.TAURI_SIGNING_PRIVATE_KEY ?? '').trim())
const hasAppleIdentity = Boolean(String(process.env.APPLE_SIGNING_IDENTITY ?? '').trim()) &&
  String(process.env.APPLE_SIGNING_IDENTITY).trim() !== '-'

let conf = JSON.parse(fs.readFileSync(confPath, 'utf8'))
let changed = false

if (!hasUpdaterKey && conf.bundle?.createUpdaterArtifacts === true) {
  conf.bundle.createUpdaterArtifacts = false
  changed = true
  console.log('TAURI_SIGNING_PRIVATE_KEY missing: disabled createUpdaterArtifacts')
} else if (hasUpdaterKey) {
  console.log('TAURI_SIGNING_PRIVATE_KEY present: keep createUpdaterArtifacts')
}

conf.bundle = conf.bundle || {}
conf.bundle.macOS = conf.bundle.macOS || {}
if (!hasAppleIdentity) {
  if (conf.bundle.macOS.signingIdentity !== '-') {
    conf.bundle.macOS.signingIdentity = '-'
    changed = true
    console.log('No Apple desktop signing identity: set macOS.signingIdentity="-" (ad-hoc)')
  }
} else {
  console.log(`APPLE_SIGNING_IDENTITY=${process.env.APPLE_SIGNING_IDENTITY}`)
}

if (changed) {
  fs.writeFileSync(confPath, `${JSON.stringify(conf, null, 4)}\n`)
  console.log(`Updated ${confPath}`)
} else {
  console.log('No tauri.conf.json changes needed')
}
