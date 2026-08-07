# GitHub 发版密钥本机备忘（模板）

> **用法**：复制本文件为同目录下的 `GitHub发版密钥本机备忘.md`，填入真实值。  
> **`GitHub发版密钥本机备忘.md` 已 gitignore，禁止提交。**  
> 正式说明见 [`docs/guides/GitHub自动打包与密钥配置说明.md`](../docs/guides/GitHub自动打包与密钥配置说明.md)。

仓库：本客户端仓 `vpn-client`（勿再配到控制面 `vpn` 仓发版）  
配置入口：GitHub → Settings → Secrets and variables → Actions

---

## 已配置 Secret 核对（打勾）

- [ ] `ANDROID_KEYSTORE_BASE64`
- [ ] `ANDROID_KEYSTORE_PASSWORD`
- [ ] `ANDROID_KEY_ALIAS`
- [ ] `ANDROID_KEY_PASSWORD`
- [ ] `VITE_API_BASE_URL`
- [ ] `TAURI_SIGNING_PRIVATE_KEY`
- [ ] `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`
- [ ] Apple iOS 全套（可选，未配则跳过 IPA）

---

## 值备忘（仅写在本机副本，勿提交）

### Android

```text
本机 keystore: apps/android/keystore/kuayun-release.keystore
本机 properties: apps/android/keystore.properties

ANDROID_KEYSTORE_PASSWORD=
ANDROID_KEY_ALIAS=
ANDROID_KEY_PASSWORD=
# ANDROID_KEYSTORE_BASE64=（太长可写「见本机 keystore，用 PowerShell 转 Base64」）
```

### 桌面 API

```text
VITE_API_BASE_URL=http://192.229.87.112:44080/api
# 注意：不含 /v1；须与 Android releaseAppBaseUrl 同控制面
```

### Tauri Updater

```text
私钥文件: apps/tauri/.tauri/updater.key
密码文件: apps/tauri/.tauri/updater.key.password
公钥文件: apps/tauri/.tauri/updater.key.pub
# 公钥已写入 apps/tauri/src-tauri/tauri.conf.json → plugins.updater.pubkey
# 禁止 setup:updater -Force 换密钥

TAURI_SIGNING_PRIVATE_KEY=（粘贴 updater.key 全文，或写「见本机文件」）
TAURI_SIGNING_PRIVATE_KEY_PASSWORD=
```

### Apple（可选）

```text
APPLE_TEAM_ID=
APPLE_CERTIFICATE_PASSWORD=
KEYCHAIN_PASSWORD=
IOS_EXPORT_METHOD=app-store
```

---

## 当前客户端版本（发版前改）

| 端 | versionName | versionCode |
|----|-------------|-------------|
| Android | 1.2 | 120 |
| Win/Mac | 1.2 | 120 |
| 最近 Tag | （发版时填写） | — |

---

## 复制到剪贴板（PowerShell，在仓库根目录）

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\apps\android\keystore\kuayun-release.keystore")) | Set-Clipboard
Get-Content "$PWD\apps\tauri\.tauri\updater.key" -Raw | Set-Clipboard
Get-Content "$PWD\apps\tauri\.tauri\updater.key.password" -Raw | Set-Clipboard
```
