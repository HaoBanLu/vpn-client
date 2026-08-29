const puppeteer = require('puppeteer-core')
const { execSync } = require('child_process')
const path = require('path')

const ADB = process.env.ADB || 'C:\\Users\\luban\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
const SHOT_DIR = path.join(__dirname)

// Unicode escapes avoid PowerShell encoding corruption when copied
const TEXT_PROTECTED = '\u5df2\u4fdd\u62a4'
const TEXT_OFFLINE = '\u7f51\u7edc\u5df2\u65ad\u5f00'
const TEXT_AUTO_RECONNECT = '\u6062\u590d\u540e\u5c06\u81ea\u52a8\u91cd\u8fde'

function adb(cmd) {
  return execSync(`"${ADB}" ${cmd}`, { encoding: 'utf8' }).trim()
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

async function routeTo(page, routePath) {
  await page.evaluate((p) => {
    window.history.pushState({}, '', p)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, routePath)
  await sleep(2000)
}

async function heroTitle(page) {
  return page.evaluate(() => document.querySelector('.connect-hero__title')?.textContent?.trim() || '')
}

async function connectPageText(page) {
  return page.evaluate(() => document.querySelector('.connect-page')?.innerText || '')
}

async function snap(page, name) {
  await page.screenshot({ path: path.join(SHOT_DIR, `_${name}.png`), fullPage: true })
  console.log('shot', name)
}

async function ensureProtected(page) {
  adb('shell cmd connectivity airplane-mode disable')
  adb('shell svc wifi enable')
  await routeTo(page, '/main/connect')
  let title = await heroTitle(page)
  if (title.includes(TEXT_PROTECTED)) return true

  await routeTo(page, '/main/nodes')
  const btn = await page.$('.ky-node-row__action')
  if (btn) await btn.click()
  for (const y of [1320, 1450, 1200]) {
    adb(`shell input tap 540 ${y}`)
    await sleep(700)
  }
  for (let i = 0; i < 45; i++) {
    await routeTo(page, '/main/connect')
    title = await heroTitle(page)
    if (title.includes(TEXT_PROTECTED)) return true
    await sleep(2000)
  }
  return false
}

function disableNetwork() {
  adb('shell svc wifi disable')
  adb('shell svc data disable')
  adb('shell cmd connectivity airplane-mode enable')
}

function enableNetwork() {
  adb('shell cmd connectivity airplane-mode disable')
  adb('shell svc wifi enable')
  adb('shell svc data enable')
}

;(async () => {
  const appPid = adb('shell pidof com.vpn.kuayun')
  if (!appPid) throw new Error('app not running')
  try {
    adb('forward --remove tcp:9222')
  } catch {
    /* ignore */
  }
  adb(`forward tcp:9222 localabstract:webview_devtools_remote_${appPid}`)
  adb('logcat -c')

  const browser = await puppeteer.connect({ browserURL: 'http://127.0.0.1:9222', defaultViewport: null })
  const page = (await browser.pages())[0]

  const connected = await ensureProtected(page)
  await routeTo(page, '/main/connect')
  const beforeTitle = await heroTitle(page)
  const beforeText = await connectPageText(page)
  console.log('connected:', connected, 'hero:', beforeTitle)
  console.log('before_snippet:', beforeText.replace(/\s+/g, ' ').slice(0, 200))
  await snap(page, 'g1_connected')

  disableNetwork()
  await sleep(6000)
  const offlineTitle = await heroTitle(page)
  const offlineText = await connectPageText(page)
  const offlineOk = offlineTitle.includes(TEXT_OFFLINE) || offlineText.includes(TEXT_AUTO_RECONNECT)
  const rateZero = /0\.0\s*KB\/s/.test(offlineText)
  console.log('offline_hero:', offlineTitle)
  console.log('offline_ui_ok:', offlineOk, 'rate_zero_ok:', rateZero)
  console.log('offline_snippet:', offlineText.replace(/\s+/g, ' ').slice(0, 250))
  await snap(page, 'g2_offline')

  enableNetwork()
  await sleep(22000)
  await routeTo(page, '/main/connect')
  const recoveredTitle = await heroTitle(page)
  const recoveredText = await connectPageText(page)
  const recoveredOk =
    recoveredTitle.includes(TEXT_PROTECTED) ||
    recoveredTitle.includes('\u8fde\u63a5\u4e0d\u7a33\u5b9a') ||
    recoveredTitle.includes('\u6b63\u5728\u6062\u590d')
  console.log('recovered_hero:', recoveredTitle)
  console.log('recovered_ok:', recoveredOk)
  console.log('recovered_snippet:', recoveredText.replace(/\s+/g, ' ').slice(0, 250))
  await snap(page, 'g3_recovered')

  console.log('vpn_tunnel_logs:')
  console.log(
    execSync(`"${ADB}" logcat -d -s VpnTunnelService:I VpnTunnelService:W VpnTunnelService:E`, {
      encoding: 'utf8',
    }).trim(),
  )

  browser.disconnect()
  const pass = connected && offlineOk && rateZero && recoveredOk
  console.log('OVERALL_PASS:', pass)
  process.exit(pass ? 0 : 1)
})().catch((err) => {
  console.error(err)
  process.exit(1)
})
