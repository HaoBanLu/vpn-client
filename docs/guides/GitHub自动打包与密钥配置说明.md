# GitHub 自动打包与密钥配置说明

> **仓库**：`vpn-client`（客户端独立仓；控制面 `vpn` 仓不跑客户端发版）  
> **用途**：Tag 触发自动打包、GitHub Secrets 逐项说明、版本怎么加、产物核对、后台发布  
> **最后更新**：2026-08-07  
> **Workflow**：[`app-release.yml`](../../.github/workflows/app-release.yml)  
> **密钥短表**：[`.github/GitHub发版密钥说明.md`](../../.github/GitHub发版密钥说明.md)  
> **本机备忘模板**：复制 [`.github/GitHub发版密钥本机备忘模板.md`](../../.github/GitHub发版密钥本机备忘模板.md) → `.github/GitHub发版密钥本机备忘.md`（已 gitignore，勿提交）  
> **PRD**：[客户端CI自动发包产品需求.md](../product/客户端CI自动发包产品需求.md)

> 旧文件名已废弃删除；请以本手册与 [`.github/GitHub发版密钥说明.md`](../../.github/GitHub发版密钥说明.md) 为准。

---

## 1. 一句话结论

**不会每次 push 就打包。** 只有把 **`v*` Tag 推到本仓库 GitHub**，才会跑 **App Release**，产出：

- Android 全量 APK  
- Windows NSIS 安装包  
- macOS DMG  
- （可选）iPhone IPA —— 需配齐 Apple 相关 Secrets  

并挂到 GitHub Release。再由人下载后上传到管理后台发布。

```text
改代码 → bump 各端版本号 → push main → git tag vX.Y.Z → push tag
  → Actions 打包 → Releases 下载 → 管理后台上传发布
```

---

## 2. 流程总览

```mermaid
flowchart LR
  bump[bump源码版本号] --> pushMain[push_main]
  pushMain --> tag[git_tag_v]
  tag --> gha[app_release_yml]
  gha --> android[Android_APK]
  gha --> win[Windows_NSIS]
  gha --> mac[macOS_DMG]
  gha --> ios[iOS_IPA可选]
  android --> rel[GitHub_Release]
  win --> rel
  mac --> rel
  ios --> rel
  rel --> admin[管理后台上传]
  admin --> client[客户端检查更新]
```

| 阶段 | 做什么 | 谁做 |
|------|--------|------|
| 开发 | PR / push → `tauri-ci`（测试，**不发版**）；`android-ci` 已存档停用 | CI |
| 改版本 | 改 `apps/tauri` `APP_VERSION_*`（三处 + Cargo）并 commit | 人 |
| 触发发版 | `git tag` + `git push origin <tag>` | 人 |
| 自动打包 | Actions：Android（Tauri）+ Win + Mac + 可选 iOS | CI |
| 运营发布 | 下载附件 → 后台上传 → 发布 | 人 |

---

## 3. 当前版本对照（以源码为准）

> Tag（如 `v1.2`）只是发版列车号；用户看到的版本以各端源码为准。下表按 **2026-08-07** 核对。

| 端 | 用户可见版本 | 版本码 | 改哪里 |
|----|--------------|--------|--------|
| **Android / Windows / macOS / Linux** | `1.2` | `120` | **统一**：`apps/tauri/package.json`、`src-tauri/tauri.conf.json`、`src/lib/app-meta.ts`（`APP_VERSION_NAME` / `APP_VERSION_CODE`）；`Cargo.toml` 一并 |
| **iPhone** | `1.2` | `120` | `apps/tauri/platforms/ios/project.yml` |
| **发版 Tag** | `v1.2` | — | 仅 Git Tag |

> **`apps/android` 已存档**：不再改其 `build.gradle.kts` 发版。CI Android APK 来自 `apps/tauri`。

---

## 4. GitHub Secrets（你配置过的那些）

配置位置：**本仓库** GitHub → Settings → Secrets and variables → Actions → Repository secrets。

**安全**：真实密码 / 私钥 / Base64 **不要写进已跟踪文件**。本机备忘只写到 gitignore 的 `GitHub发版密钥本机备忘.md`。

### 4.1 你已在用的 7 项（必看）

| Secret 名 | 中文含义 | 干什么用 | 本机对照 |
|-----------|----------|----------|----------|
| `ANDROID_KEYSTORE_BASE64` | Android 签名证书（Base64） | CI 还原 `.keystore`，给正式 APK 签名 | 存档路径 `apps/android/keystore/kuayun-release.keystore`（仍可被 CI 读取） |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 打开密码 | 解开证书库 | `keystore.properties` → `storePassword` |
| `ANDROID_KEY_ALIAS` | 密钥别名 | 指定用哪一把钥匙（常为 `key0`） | `keystore.properties` → `keyAlias` |
| `ANDROID_KEY_PASSWORD` | 密钥密码 | 使用该钥匙时的密码 | `keystore.properties` → `keyPassword` |
| `VITE_API_BASE_URL` | 桌面正式包 API 根地址 | 打进 Win/Mac 安装包的控制面地址（**不含 `/v1`**） | 例：`http://192.229.87.112:44080/api` |
| `TAURI_SIGNING_PRIVATE_KEY` | 桌面更新私钥全文 | 给安装包生成 `.sig`，应用内更新验签 | `apps/tauri/.tauri/updater.key` |
| `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | 更新私钥密码 | 解开 updater 私钥 | `apps/tauri/.tauri/updater.key.password` |

说明：

- **Android 四项**：正式 APK 签名（产物来自 **`apps/tauri`**）。私有仓若仍跟踪存档目录 keystore，CI 可优先用仓库内文件；Secrets 建议仍配作备份。
- **`VITE_API_BASE_URL`**：打进 **桌面 + Tauri Android** 正式包的控制面地址（**不含 `/v1`**）。
- **Tauri 两项**：不配也能出桌面安装包，但 **没有 `.sig`**，管理后台做不了应用内更新。**禁止**随意 `setup:updater -Force` 换密钥。

### 4.2 可选：Apple / iOS（未配则跳过 IPA）

| Secret | 用途 |
|--------|------|
| `APPLE_CERTIFICATE_BASE64` | Distribution 证书 `.p12` 的 Base64 |
| `APPLE_CERTIFICATE_PASSWORD` | 导出 p12 时的密码 |
| `APPLE_PROVISIONING_PROFILE_APP_BASE64` | 主 App 描述文件 |
| `APPLE_PROVISIONING_PROFILE_TUNNEL_BASE64` | PacketTunnel 描述文件 |
| `APPLE_TEAM_ID` | Team ID（10 位） |
| `KEYCHAIN_PASSWORD` | CI 临时钥匙串密码（自设） |
| `IOS_EXPORT_METHOD` | `app-store`（默认）或 `ad-hoc` |
| `APPLE_SIGNING_IDENTITY` | macOS 桌面 Developer ID；空则 ad-hoc |

未配齐时 **不阻塞** Android / Windows / macOS。

### 4.3 把本机文件拷进 Secrets（PowerShell）

```powershell
# 在 vpn-client 仓库根目录执行

# Android keystore → ANDROID_KEYSTORE_BASE64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\apps\android\keystore\kuayun-release.keystore")) | Set-Clipboard

# Tauri updater 私钥 → TAURI_SIGNING_PRIVATE_KEY
Get-Content "$PWD\apps\tauri\.tauri\updater.key" -Raw | Set-Clipboard

# Tauri updater 密码 → TAURI_SIGNING_PRIVATE_KEY_PASSWORD
Get-Content "$PWD\apps\tauri\.tauri\updater.key.password" -Raw | Set-Clipboard
```

密码 / 别名从 `apps/android/keystore.properties` 手抄到 Secrets。

---

## 5. 如何发一个新版本

### 5.1 发 Android（与桌面同一版本源）

1. 改 `apps/tauri` 版本三处 + `APP_VERSION_CODE` **必须 +1**（见 §3）  
2. 确认 `VITE_API_BASE_URL` Secret 指向生产控制面  
3. commit → push main → 打 Tag（§5.4）  
4. 下载 `kuayun-android-*-arm64.apk` → 后台 `platform=android` 上传  

> 勿再改 `apps/android`（已存档）。本机也可：`cd apps/tauri && npm run tauri:android:build:release`

### 5.2 只发 Windows / macOS

1. 三处版本改成同一组（如 `1.2` / `120` → `1.2.1` / `121`）：  
   `package.json`、`tauri.conf.json`、`app-meta.ts`（`Cargo.toml` 一并）  
2. `APP_VERSION_CODE` **只增不减**  
3. commit → push → Tag  
4. 下载 `.exe` / `.dmg`，有应用内更新再带上对应 `.sig`  

### 5.3 两端一起发

两端都 bump 后打一次 Tag，Actions 并行构建。

### 5.4 打 Tag

```bash
git pull origin main
git tag v1.2.1
git push origin v1.2.1
```

| 规则 | 说明 |
|------|------|
| 格式 | 必须以 `v` 开头 |
| 触发 | **push tag**；只改代码不打 Tag = 不发版 |
| 进度 | GitHub → Actions → **App Release** |
| 下包 | GitHub → Releases → 对应 Tag |

---

## 6. 打出来的包长什么样

| 附件 | 正常体积 | 含义 |
|------|----------|------|
| `kuayun-android-{name}-{code}-arm64.apk` | **~49MB** | 全量（含 libclash / libbridge） |
| `kuayun-windows-{ver}-x64-setup.exe` | **~10–15MB** | 含 mihomo |
| `kuayun-macos-{ver}.dmg` | **~15–20MB** | 含 mihomo |
| `*.sig` | 很小 | Updater 签名（需 `TAURI_SIGNING_*`） |
| `kuayun-ios-*.ipa` | — | 仅 Apple Secrets 齐全时 |

异常：Android ~2.5MB、Windows ~3.6MB → 缺内核，**不要分发**。

---

## 7. 管理后台发布

### Android

- 平台 `android`；版本名/码与 APK 一致；上传 APK（无 Updater 签名字段）

### Windows / macOS（应用内更新）

| 字段 | 填什么 |
|------|--------|
| 平台 | `windows` / `macos` |
| 版本名 / 版本码 | 与包一致；码须大于用户当前 |
| 安装包 | `.exe` / `.dmg` |
| Updater 签名 | 对应 **`.sig` 全文**（不是 SHA256） |

自测：

```text
GET {API}/api/v1/client/version/tauri-manifest?target=windows-x86_64&current_version=1.1.0
GET {API}/api/v1/client/version/tauri-manifest?target=darwin-aarch64&current_version=1.1.0
```

应返回带 `signature` + `url` 的 JSON。

---

## 8. 注意事项

1. **Tag 才发包**，push main 不会出正式 Release。  
2. **版本码只增不减**；同平台后台不可重复。  
3. **桌面/Android 版本以 `apps/tauri` 三处为准，必须一致**。  
4. CI 已自动拉 mihomo / native（含存档目录 `mihomo-core`）；勿用瘦包当默认渠道。  
5. Updater 密钥一对一生；丢私钥 = 无法再签同系列更新。  
6. macOS 无 Developer ID 时为 ad-hoc，用户可能要在「隐私与安全性」允许。  
7. **`VITE_API_BASE_URL`** 影响桌面与 Tauri Android 正式包；改后必须重新打 Tag。  
8. Secrets 配在 **vpn-client** 仓。  
9. **`apps/android` 已存档**，勿再按其 `build.gradle.kts` 发版。

---

## 9. 推荐命令

```bash
# 1. 已 bump apps/tauri 版本并 push 到 main
# 2. 可选本地门禁
cd apps/tauri && npm test && npm run preflight:desktop

# 3. 打 Tag
cd ../..
git tag v1.2.1
git push origin v1.2.1

# 4. Actions / Releases 核对体积与 .sig → 后台上传
```

---

## 10. 相关文件

| 文件 | 说明 |
|------|------|
| `.github/workflows/app-release.yml` | Tag 发版（Android=Tauri + Win + Mac + 可选 iOS） |
| `.github/workflows/tauri-ci.yml` | PR 门禁（不发版） |
| `.github/workflows/android-ci.yml` | **已存档**（仅手动提醒） |
| `.github/GitHub发版密钥说明.md` | Secrets 短说明 |
| `.github/GitHub发版密钥本机备忘模板.md` | 本机备忘模板 |
| `scripts/ci/build-tauri-android-release.sh` | Tag CI 打 Tauri Android APK |
| `scripts/ci/setup-android-signing.sh` | Android 签名注入（读存档 keystore 路径） |
| `scripts/ci/prepare-tauri-release-build.mjs` | 无 updater 密钥时关签名产物 |
| [`apps/android/ARCHIVE.md`](../../apps/android/ARCHIVE.md) | Android Compose 存档说明 |
| [`apps/tauri/跨云客户端打包说明.md`](../../apps/tauri/跨云客户端打包说明.md) | 本机桌面 / Android / Updater |
| [`App-Android-发版检查清单.md`](App-Android-发版检查清单.md) | Android 上架前核对（已改指向 Tauri） |
