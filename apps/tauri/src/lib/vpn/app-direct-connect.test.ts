import { describe, expect, it } from 'vitest'
import { filterInstalledApps, type InstalledAppInfo } from './app-direct-connect'

const apps: InstalledAppInfo[] = [
  { packageName: 'com.example.bank', label: '示例银行' },
  { packageName: 'com.android.chrome', label: 'Chrome' },
  { packageName: 'com.qihoo.magic', label: '分身大师' },
]

describe('filterInstalledApps', () => {
  it('空查询返回全部', () => {
    expect(filterInstalledApps(apps, '  ')).toHaveLength(3)
  })

  it('按应用名过滤', () => {
    expect(filterInstalledApps(apps, '银行').map((a) => a.packageName)).toEqual([
      'com.example.bank',
    ])
  })

  it('按包名过滤', () => {
    expect(filterInstalledApps(apps, 'qihoo').map((a) => a.packageName)).toEqual([
      'com.qihoo.magic',
    ])
  })
})
