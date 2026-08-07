# App Android 发版检查清单

> **2026-08-08**：正式 Android 工程为 **`apps/tauri`**；`apps/android` [已存档](../../apps/android/ARCHIVE.md)。  
> **用途**：Release APK 上线前必跑项。  
> **本机构建**：`cd apps/tauri && npm run tauri:android:build:release`  
> **CI**：Tag `v*` → `app-release.yml`（`build-tauri-android-release.sh`）  
> **铁律与踩坑**：见 [`GitHub自动打包与密钥配置说明.md`](GitHub自动打包与密钥配置说明.md) §0 / §9

---

## 1. 发布前结论

- [ ] **包名** 为 `com.vpn.kuayun`（`tauri.conf.json` → `identifier`）
- [ ] **版本为完整 semver**（如 `1.2.8`，勿写 `1.2`）
- [ ] **`APP_VERSION_NAME` / `APP_VERSION_CODE`** 已递增，并同步 `package.json` / `tauri.conf` / `Cargo.toml`
- [ ] **`bundle.android.minSdkVersion` ≥ 26**（对齐 `mihomo-core`）
- [ ] **Release 签名**：密码三项 Secrets 已配（入库 keystore 可无 BASE64）
- [ ] **`VITE_API_BASE_URL`** 指向生产控制面（不含 `/v1`）
- [ ] **后端已部署** 且客户端 API 兼容
- [ ] 存档 `apps/android/mihomo-core/**/libclash.so` 仍在仓库（或 CI 能拉到）

---

## 2. 本地门禁

```bash
cd apps/tauri
npm test
npm run tauri:android:build:release
```

| 步骤 | 通过标准 |
|------|----------|
| 单元测试 | `npm test` 全绿 |
| Release APK | 产出签名包；体积 **≥ ~20MB（正常 ~49MB）**；含 `lib/arm64-v8a/libclash.so` / `libbridge.so` |
| 真机冒烟 | 登录 → 选节点 → 连接 → 出口 IP |

---

## 3. Tag 发版

见 [`GitHub自动打包与密钥配置说明.md`](GitHub自动打包与密钥配置说明.md)。

```bash
git tag v1.2.8
git push origin v1.2.8
```

核对 Actions **App Release** 全绿后：下载 `kuayun-android-*-arm64.apk` → 管理后台 `platform=android` 上传发布。

> CI 中间产物可能是 `app-universal-release-unsigned.apk`；脚本会收集并重签为 `kuayun-android-…-arm64.apk`。

---

## 4. 能力缺口提醒

Tauri Android 相对历史 Compose 仍可能缺：分应用直连、KS/开机自连/保护等级 UI 等。见 [Tauri-Android功能对齐](../product/Tauri-Android功能对齐.md) §6。
