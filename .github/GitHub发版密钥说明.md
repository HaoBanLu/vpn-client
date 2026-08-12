# GitHub 发版密钥说明

> 本文说明 Tag 自动打包（[`app-release.yml`](workflows/app-release.yml)）需要配置的 **Repository Secrets**。  
> **勿在此文件或 PR 中提交真实凭据。** 只在 GitHub → Settings → Secrets and variables → Actions 中填写。  
>
> **完整手册（流程 / 打包 / 后台）**：[`docs/guides/GitHub自动打包与密钥配置说明.md`](../docs/guides/GitHub自动打包与密钥配置说明.md)  
> **本机备忘模板**：复制 [`GitHub发版密钥本机备忘模板.md`](GitHub发版密钥本机备忘模板.md) → `GitHub发版密钥本机备忘.md`（已 gitignore）

关联 PRD：[客户端CI自动发包产品需求.md](../docs/product/客户端CI自动发包产品需求.md)

---

## 你已配置过的 7 项（常用）

| Secret | 中文含义 | 说明 | 本机对照 |
|--------|----------|------|----------|
| `ANDROID_KEYSTORE_BASE64` | Android 签名证书 Base64 | Release keystore 整文件转 Base64 | `apps/android/keystore/kuayun-release.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 | 打开证书库 | `apps/android/keystore.properties` → `storePassword` |
| `ANDROID_KEY_ALIAS` | 密钥别名 | 通常 `key0` | 同上 → `keyAlias` |
| `ANDROID_KEY_PASSWORD` | 密钥密码 | 使用该钥匙的密码 | 同上 → `keyPassword` |
| `VITE_API_BASE_URL` | 正式包 API 根路径 | **不含** `/v1`；打进 Win/Mac/**Android(Tauri)** | 例：`http://192.229.87.112:44080/api` |
| `TAURI_SIGNING_PRIVATE_KEY` | 桌面更新私钥全文 | 生成 `.sig`，应用内更新验签 | `apps/tauri/.tauri/updater.key` |
| `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | 更新私钥密码 | 解开 updater 私钥 | `apps/tauri/.tauri/updater.key.password` |

PowerShell 转 keystore Base64（在仓库根目录）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("apps\android\keystore\kuayun-release.keystore")) | Set-Clipboard
```

> 私有仓若已跟踪 keystore + `keystore.properties`，CI 可优先用仓库内文件。  
> 若只跟踪了 `.keystore`（`keystore.properties` 在 `.gitignore`），**至少**配置 `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`；`ANDROID_KEYSTORE_BASE64` 可选作备份。  
> 强制整包覆盖：环境变量 `ANDROID_FORCE_SECRET_SIGNING=1`。

`VITE_API_BASE_URL` 须与 Android `releaseAppBaseUrl` 指向同一控制面（Android 地址在 gradle，不是本 Secret）。

未配置 `TAURI_SIGNING_*` 时 CI 关闭 `createUpdaterArtifacts`，Release **无 `.sig`**，后台无法做应用内更新。  
**禁止**随意 `npm run setup:updater -- -Force`（会换密钥，旧客户端无法校验）。

---

## Apple iOS 签名（可选）

| Secret | 说明 |
|--------|------|
| `APPLE_CERTIFICATE_BASE64` | Apple Distribution 证书（`.p12`）Base64 |
| `APPLE_CERTIFICATE_PASSWORD` | 导出 p12 时设置的密码 |
| `APPLE_PROVISIONING_PROFILE_APP_BASE64` | 主 App `com.vpn.kuayun.app` 描述文件 Base64 |
| `APPLE_PROVISIONING_PROFILE_TUNNEL_BASE64` | PacketTunnel `com.vpn.kuayun.tunnel` 描述文件 Base64 |
| `APPLE_TEAM_ID` | Apple Developer Team ID（10 位） |
| `KEYCHAIN_PASSWORD` | CI 临时钥匙串密码（任意强随机字符串） |
| `IOS_EXPORT_METHOD` | `app-store`（默认）或 `ad-hoc` |
| `APPLE_SIGNING_IDENTITY` | macOS 桌面 Developer ID；空则 ad-hoc |

未配置时自动跳过 iPhone IPA，仍可发 Android / Windows / macOS。

---

## 配置检查清单

- [ ] 以上 7 项已配到 **vpn-client** 仓库 Secrets（不要只留在旧 `vpn` 仓）
- [ ] `VITE_API_BASE_URL` 指向可访问的生产 API
- [ ] Android：仓库内 keystore 可用，或 Secrets 已备份
- [ ] 要做桌面应用内更新：`TAURI_SIGNING_*` 与 `tauri.conf.json` pubkey 同一对
- [ ] 若要打 iPhone IPA：Apple 描述文件未过期，含 Network Extension
- [ ] 本机备忘已写入 `GitHub发版密钥本机备忘.md`（未提交）

---

## Release 产物体积参考

| 平台 | 正常体积 | 异常信号 |
|------|----------|----------|
| Android arm64 APK | **~49MB** | **~2.5MB** → 缺 native，不可用 |
| Windows NSIS | **~10–15MB** | **~3.6MB** → 缺 mihomo |
| macOS DMG | **~15–20MB** | 明显偏小 → 检查 `fetch:mihomo` |
| `*.sig` | 有 | 无 → 未配 `TAURI_SIGNING_*` |

---

## 发版命令

```bash
# 见完整手册 §0 / §5 / §10
git tag v1.2.8
git push origin v1.2.8
```

完成后：GitHub → Releases 核对体积与 `.sig` → 管理后台上传发布。
