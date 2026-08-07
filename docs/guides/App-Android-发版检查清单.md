# App Android 发版检查清单

> **2026-08-07**：正式 Android 工程为 **`apps/tauri`**；`apps/android` [已存档](../../apps/android/ARCHIVE.md)。  
> **用途**：Release APK 上线前必跑项。  
> **本机构建**：`cd apps/tauri && npm run tauri:android:build:release`  
> **CI**：Tag `v*` → `app-release.yml`（`build-tauri-android-release.sh`）

---

## 1. 发布前结论

- [ ] **包名** 为 `com.vpn.kuayun`（`tauri.conf.json` → `identifier`）
- [ ] **`APP_VERSION_NAME` / `APP_VERSION_CODE`** 已递增（`apps/tauri/src/lib/app-meta.ts`，并同步 package.json / tauri.conf / Cargo）
- [ ] **Release 签名** Secrets 或存档 keystore 可用
- [ ] **`VITE_API_BASE_URL`** 指向生产控制面
- [ ] **后端已部署** 且客户端 API 兼容

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
| Release APK | 产出签名包；体积合理；含 `libclash.so` / `libbridge.so` |
| 真机冒烟 | 登录 → 选节点 → 连接 → 出口 IP |

---

## 3. Tag 发版

见 [`GitHub自动打包与密钥配置说明.md`](GitHub自动打包与密钥配置说明.md)。

```bash
git tag v1.2.1
git push origin v1.2.1
```

下载 `kuayun-android-*-arm64.apk` → 管理后台 `platform=android` 上传发布。

---

## 4. 能力缺口提醒

Tauri Android 相对历史 Compose 仍可能缺：分应用直连、KS/开机自连/保护等级 UI 等。见 [Tauri-Android功能对齐](../product/Tauri-Android功能对齐.md) §6。
