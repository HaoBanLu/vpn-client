# GitHub Actions Release Secrets 配置说明

> 本文档说明 [App Release workflow](workflows/app-release.yml) 所需的 Repository Secrets。  
> **勿在此文件或 PR 中提交真实凭据。** 仅仓库管理员在 GitHub → Settings → Secrets and variables → Actions 中配置。
>
> **完整发版手册（流程 / 加版本 / 注意事项）**：[`docs/guides/客户端GitHub-Actions发版.md`](../docs/guides/客户端GitHub-Actions发版.md)  
> **本机机密备忘模板**：复制 [`RELEASE_SECRETS.local.example.md`](RELEASE_SECRETS.local.example.md) → `RELEASE_SECRETS.local.md`（已 gitignore）

关联 PRD：[docs/product/客户端CI自动发包产品需求.md](../docs/product/客户端CI自动发包产品需求.md)

---

## 必填 / 建议配置

> 未配置 Apple iOS Secrets 时自动跳过 iPhone IPA，仍可发 Android / Windows / macOS。

### Android 签名

| Secret | 说明 | 生成本机对照 |
|--------|------|----------------|
| `ANDROID_KEYSTORE_BASE64` | Release keystore Base64 | `apps/android/keystore/kuayun-release.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 | `apps/android/keystore.properties` |
| `ANDROID_KEY_ALIAS` | 密钥别名，通常 `key0` | 同上 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 | 同上 |

PowerShell 转 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("apps\android\keystore\kuayun-release.keystore")) | Set-Clipboard
```

> 私有仓若已跟踪 keystore + `keystore.properties`，CI 默认用仓库内文件；Secrets 作备份。强制用 Secret：`ANDROID_FORCE_SECRET_SIGNING=1`。

### Tauri 生产 API

| Secret | 说明 | 示例 |
|--------|------|------|
| `VITE_API_BASE_URL` | 生产 API 根路径（**不含** `/v1`） | `http://192.229.87.112:44080/api` |

须与 Android `releaseAppBaseUrl` 指向同一控制面。

### 桌面应用内更新（强烈建议）

| Secret | 说明 | 本机文件 |
|--------|------|----------|
| `TAURI_SIGNING_PRIVATE_KEY` | updater 私钥**全文** | `apps/tauri/.tauri/updater.key` |
| `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | 私钥密码（非空） | `apps/tauri/.tauri/updater.key.password` |

未配置时 CI 关闭 `createUpdaterArtifacts`，Release **无 `.sig`**，后台无法做应用内更新。  
**禁止**随意 `npm run setup:updater -- -Force`（会换密钥，旧客户端无法校验）。

### Apple iOS 签名（可选）

| Secret | 说明 |
|--------|------|
| `APPLE_CERTIFICATE_BASE64` | Apple Distribution 证书（`.p12`）Base64 |
| `APPLE_CERTIFICATE_PASSWORD` | 导出 p12 时设置的密码 |
| `APPLE_PROVISIONING_PROFILE_APP_BASE64` | 主 App `com.kuayun.vpn.app` 描述文件 Base64 |
| `APPLE_PROVISIONING_PROFILE_TUNNEL_BASE64` | PacketTunnel `com.kuayun.vpn.tunnel` 描述文件 Base64 |
| `APPLE_TEAM_ID` | Apple Developer Team ID（10 位） |
| `KEYCHAIN_PASSWORD` | CI 临时钥匙串密码（任意强随机字符串） |
| `IOS_EXPORT_METHOD` | `app-store`（默认）或 `ad-hoc` |
| `APPLE_SIGNING_IDENTITY` | macOS 桌面 Developer ID；空则 ad-hoc |

---

## 配置检查清单

- [ ] `VITE_API_BASE_URL` 指向可访问的生产 API
- [ ] Android：仓库内 keystore 可用，或 Secrets 已备份
- [ ] 要做桌面应用内更新：`TAURI_SIGNING_*` 已配置，且与 `tauri.conf.json` pubkey 同一对
- [ ] 若要打 iPhone IPA：Apple 描述文件未过期，含 Network Extension
- [ ] 本机备忘已写入 `RELEASE_SECRETS.local.md`（未提交）

---

## Release 产物体积参考（全量包）

| 平台 | 正常体积 | 异常信号 |
|------|----------|----------|
| Android arm64 APK | **~49MB** | **~2.5MB** → 缺 native，不可用 |
| Windows NSIS | **~10–15MB** | **~3.6MB** → 缺 mihomo |
| macOS DMG | **~15–20MB** | 明显偏小 → 检查 `fetch:mihomo` |
| `*.sig` | 有 | 无 → 未配 `TAURI_SIGNING_*` |

---

## 发版命令

```bash
# 1. bump 各端版本号并 merge main（见发版手册 §4）
# 2. 可选本地门禁
cd apps/android && bash scripts/release-gate.sh
cd apps/tauri && npm test

# 3. 打 Tag 触发 CI
git tag v3.14.7
git push origin v3.14.7
```

完成后：GitHub → Releases 核对体积与 `.sig` → 管理后台上传发布。
