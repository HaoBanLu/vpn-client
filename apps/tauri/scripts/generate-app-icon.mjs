/**
 * 将 assets/app-icon.svg（官方跨云云朵 vector）栅格化为 PNG，
 * 再交给 `tauri icon` / Android mipmap 同步。
 * 禁止用椭圆手绘近似云朵。
 */
import { mkdirSync, readFileSync, writeFileSync, copyFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Resvg } from '@resvg/resvg-js'
import { spawnSync } from 'node:child_process'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = join(__dirname, '..')
const svgPath = join(root, 'assets', 'app-icon.svg')
const pngPath = join(root, 'assets', 'app-icon.png')
const splashPath = join(root, 'public', 'splash-logo.png')

const svg = readFileSync(svgPath)
const resvg = new Resvg(svg, {
  fitTo: { mode: 'width', value: 1024 },
  background: 'rgba(0,0,0,0)',
})
writeFileSync(pngPath, resvg.render().asPng())
console.log('wrote', pngPath)

const icon = spawnSync('npx', ['tauri', 'icon', pngPath], {
  cwd: root,
  stdio: 'inherit',
  shell: true,
})
if (icon.status !== 0) {
  process.exit(icon.status || 1)
}

copyFileSync(join(root, 'src-tauri', 'icons', '128x128.png'), splashPath)
console.log('synced', splashPath)

function resizePng(size) {
  const r = new Resvg(svg, {
    fitTo: { mode: 'width', value: size },
    background: 'rgba(0,0,0,0)',
  })
  return r.render().asPng()
}

const androidRoot = join(root, 'src-tauri', 'icons', 'android')
const legacy = {
  'mipmap-mdpi': 48,
  'mipmap-hdpi': 72,
  'mipmap-xhdpi': 96,
  'mipmap-xxhdpi': 144,
  'mipmap-xxxhdpi': 192,
}
const fg = {
  'mipmap-mdpi': 108,
  'mipmap-hdpi': 162,
  'mipmap-xhdpi': 216,
  'mipmap-xxhdpi': 324,
  'mipmap-xxxhdpi': 432,
}

for (const [dir, size] of Object.entries(legacy)) {
  const folder = join(androidRoot, dir)
  mkdirSync(folder, { recursive: true })
  const buf = resizePng(size)
  writeFileSync(join(folder, 'ic_launcher.png'), buf)
  writeFileSync(join(folder, 'ic_launcher_round.png'), buf)
}
for (const [dir, size] of Object.entries(fg)) {
  const folder = join(androidRoot, dir)
  mkdirSync(folder, { recursive: true })
  writeFileSync(join(folder, 'ic_launcher_foreground.png'), resizePng(size))
}

const valuesDir = join(androidRoot, 'values')
mkdirSync(valuesDir, { recursive: true })
writeFileSync(
  join(valuesDir, 'ic_launcher_background.xml'),
  `<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="ic_launcher_background">#1B4DFF</color>
</resources>
`,
)

console.log('Android mipmap synced from official SVG')
