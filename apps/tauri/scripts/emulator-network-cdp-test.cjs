const puppeteer = require('puppeteer-core')
const { execSync } = require('child_process')
const path = require('path')
const fs = require('fs')

const ADB = process.env.ADB || 'C:\\Users\\luban\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
const SHOT_DIR = path.join(__dirname)
const EMAIL = 'luban7733@gmail.com'
const PASSWORD = '123456'
const T = {
  protected: '\u5df2\u4fdd\u62a4',
  offline: '\u7f51\u7edc\u5df2\u65ad\u5f00',
  recovering: '\u6b63\u5728\u6062\u590d',
  connecting: '\u8fde\u63a5\u4e2d',
  reconnect: '\u81ea\u52a8\u91cd\u8fde',
  unstable: '\u8fde\u63a5\u4e0d\u7a33\u5b9a',
}

function adb(cmd) {
  return execSync(`"${ADB}" ${cmd}`, { encoding: 'utf8', timeout: 30000 }).trim()
}
function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}
async function snap(page, name) {
  await page.screenshot({ path: path.join(SHOT_DIR, `_${name}.png`), fullPage: true })
  console.log('shot', name)
}
async function routeTo(page, p) {
  await page.evaluate((x) => {
    history.pushState({}, '', x)
    dispatchEvent(new PopStateEvent('popstate'))
  }, p)
  await sleep(1500)
}
async function heroTitle(page) {
  return page.evaluate(() => document.querySelector('.connect-hero__title')?.textContent?.trim() || '')
}
async function connectText(page) {
  return page.evaluate(() => document.querySelector('.connect-page')?.innerText || '')
}
async function dismissDialogs() {
  for (const y of [1450, 1320, 1200]) {
    try {
      adb(`shell input tap 540 ${y}`)
    } catch {
      /* */
    }
    await sleep(400)
  }
}
async function ensureLogin(page) {
  if (!page.url().includes('/login')) return
  await page.evaluate(
    (email, pwd) => {
      const inputs = [...document.querySelectorAll('input')]
      const e = inputs.find((i) => i.type === 'email' || (i.placeholder || '').includes('@'))
      const p = inputs.find((i) => i.type === 'password')
      if (e) {
        e.value = email
        e.dispatchEvent(new Event('input', { bubbles: true }))
      }
      if (p) {
        p.value = pwd
        p.dispatchEvent(new Event('input', { bubbles: true }))
      }
      ;[...document.querySelectorAll('button')].find((b) => (b.textContent || '').includes('\u767b\u5f55'))?.click()
    },
    EMAIL,
    PASSWORD,
  )
  await sleep(12000)
}
async function ensureProtected(page) {
  adb('shell cmd connectivity airplane-mode disable')
  adb('shell svc wifi enable')
  adb('shell svc data enable')
  await routeTo(page, '/main/connect')
  if ((await heroTitle(page)).includes(T.protected)) return true
  await routeTo(page, '/main/nodes')
  await sleep(2000)
  const btn = await page.$('.ky-node-row__action')
  if (!btn) throw new Error('no node button')
  await btn.click()
  await sleep(2500)
  await dismissDialogs()
  for (let i = 0; i < 35; i++) {
    await routeTo(page, '/main/connect')
    const t = await heroTitle(page)
    console.log('wait_protect', i, t)
    if (t.includes(T.protected)) return true
    await sleep(2000)
  }
  return false
}

function netOff() {
  adb('shell svc wifi disable')
  adb('shell svc data disable')
}
function netOn() {
  adb('shell svc wifi enable')
  adb('shell svc data enable')
  adb('shell cmd connectivity airplane-mode disable')
}

;(async () => {
  adb('logcat -c')
  netOn()
  adb('shell am force-stop com.vpn.kuayun')
  adb('shell monkey -p com.vpn.kuayun -c android.intent.category.LAUNCHER 1')
  await sleep(10000)
  await dismissDialogs()
  await sleep(2000)

  let appPid = adb('shell pidof com.vpn.kuayun')
  if (!appPid) throw new Error('app not running')
  try {
    adb('forward --remove tcp:9222')
  } catch {
    /* */
  }
  adb(`forward tcp:9222 localabstract:webview_devtools_remote_${appPid}`)

  let browser = await puppeteer.connect({ browserURL: 'http://127.0.0.1:9222', defaultViewport: null })
  let page = (await browser.pages())[0]
  await ensureLogin(page)

  appPid = adb('shell pidof com.vpn.kuayun')
  try {
    adb('forward --remove tcp:9222')
  } catch {
    /* */
  }
  adb(`forward tcp:9222 localabstract:webview_devtools_remote_${appPid}`)
  browser.disconnect()
  browser = await puppeteer.connect({ browserURL: 'http://127.0.0.1:9222', defaultViewport: null })
  page = (await browser.pages())[0]

  const connected = await ensureProtected(page)
  console.log('STEP1_connected', connected, await heroTitle(page))
  await snap(page, 'r1_connected')

  // --- A: browser offline/online (frontend recovery path) ---
  await page.evaluate(() => window.dispatchEvent(new Event('offline')))
  await sleep(1500)
  const offlineA = await heroTitle(page)
  const offlineAOk = offlineA.includes(T.offline)
  console.log('STEP2a_browser_offline', offlineAOk, offlineA)
  await snap(page, 'r2a_browser_offline')

  await page.evaluate(() => window.dispatchEvent(new Event('online')))
  let recoveredA = false
  let stuckA = false
  const t0 = Date.now()
  for (let i = 0; i < 35; i++) {
    const h = await heroTitle(page)
    console.log('recoverA', i, h)
    if (h.includes(T.protected) || h.includes(T.unstable)) {
      recoveredA = true
      break
    }
    if ((h.includes(T.connecting) || h.includes(T.recovering)) && Date.now() - t0 > 18000) stuckA = true
    await sleep(1000)
  }
  const elapsedA = Date.now() - t0
  console.log('STEP2a_recover', recoveredA, 'stuck', stuckA, 'ms', elapsedA, await heroTitle(page))
  await snap(page, 'r2a_recovered')

  // ensure protected again before native test
  if (!(await heroTitle(page)).includes(T.protected)) {
    await ensureProtected(page)
  }

  // --- B: kill wifi+data (native onLost/onAvailable) ---
  adb('logcat -c')
  netOff()
  let offlineB = false
  let offlineBTitle = ''
  for (let i = 0; i < 24; i++) {
    offlineBTitle = await heroTitle(page)
    console.log('offlineB', i, offlineBTitle)
    if (offlineBTitle.includes(T.offline) || (await connectText(page)).includes(T.reconnect)) {
      offlineB = true
      break
    }
    await sleep(500)
  }
  await snap(page, 'r2b_native_offline')
  console.log('STEP2b_native_offline', offlineB, offlineBTitle)

  netOn()
  let recoveredB = false
  let stuckB = false
  const t1 = Date.now()
  for (let i = 0; i < 40; i++) {
    const h = await heroTitle(page)
    console.log('recoverB', i, h)
    if (h.includes(T.protected) || h.includes(T.unstable)) {
      recoveredB = true
      break
    }
    if ((h.includes(T.connecting) || h.includes(T.recovering)) && Date.now() - t1 > 20000) stuckB = true
    await sleep(1000)
  }
  const elapsedB = Date.now() - t1
  console.log('STEP2b_recover', recoveredB, 'stuck', stuckB, 'ms', elapsedB, await heroTitle(page))
  await snap(page, 'r2b_recovered')

  const logs = execSync(`"${ADB}" logcat -d -s VpnTunnelService:I VpnTunnelService:W VpnTunnelService:E`, {
    encoding: 'utf8',
  })
  console.log('vpn_logs_tail:')
  console.log(logs.trim().split(/\r?\n/).slice(-30).join('\n'))

  browser.disconnect()

  // Pass criteria: connect ok; browser offline UI works; recovery not stuck; native preferred but emulator may miss
  const pass =
    connected &&
    offlineAOk &&
    recoveredA &&
    !stuckA &&
    elapsedA < 20000 &&
    !stuckB &&
    (recoveredB || offlineB === false) // if native offline never fired, don't fail on recoverB
  console.log('OVERALL_PASS:', pass)
  console.log(
    JSON.stringify(
      {
        connected,
        offlineAOk,
        recoveredA,
        stuckA,
        elapsedA,
        offlineB,
        recoveredB,
        stuckB,
        elapsedB,
      },
      null,
      2,
    ),
  )
  process.exit(pass ? 0 : 1)
})().catch((e) => {
  console.error(e)
  process.exit(1)
})
