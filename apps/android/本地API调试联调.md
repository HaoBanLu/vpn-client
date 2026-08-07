# Android 连本地 API 调试联调

> **用途**：真机 / 模拟器用 **Debug 包** 对接本机 `docker compose` API（默认 `48080`），验证对标快帆功能（占线、设备、出口 IP 等）。  
> **Release 包不受影响**，仍走线上 `https://vpn.eodkko.xyz/`。

---

## 1. 一分钟上手

```bash
# 项目根目录
docker compose up -d postgres redis api
bash scripts/dev/migrate-local.sh          # 首次或 schema 变更后
bash scripts/dev/android-local-api.sh    # 自动写 debug.api.base + 检查 API

cd apps/android
./gradlew :app:installDebug              # 安装到已连接真机/模拟器
```

| 场景 | API 地址 | 配置方式 |
|------|----------|----------|
| **Android 模拟器** | `http://10.0.2.2:48080/` | 默认即可，无需 `local.properties` |
| **真机（同 Wi-Fi）** | `http://<电脑局域网IP>:48080/` | `local.properties` 里 `debug.api.base=...` |
| **Release 包** | `https://vpn.eodkko.xyz/` | 不读 `local.properties` |

---

## 2. 配置说明

### 2.1 `local.properties`（推荐）

复制示例并改 IP：

```bash
cp apps/android/local.properties.example apps/android/local.properties
```

```properties
# 真机：改成你电脑的 WLAN IPv4（不要用 127.0.0.1）
debug.api.base=http://192.168.100.108:48080/
```

查 IP（Windows）：

```bash
ipconfig
# 选「无线局域网适配器 WLAN」下的 IPv4，一般是 192.168.x.x
```

或用脚本自动写入：

```bash
bash scripts/dev/android-local-api.sh
```

### 2.2 命令行临时覆盖（不改文件）

```bash
cd apps/android
./gradlew :app:installDebug -PdebugApiBase=http://192.168.100.108:48080/
```

### 2.3 构建产物中的地址

Debug 包编译进 `BuildConfig.API_BASE_URL`，可在 Logcat 搜 `OkHttp` 或断点 `ApiClient` 确认。

优先级：**`-PdebugApiBase` > `local.properties` > 模拟器默认 `10.0.2.2`**

---

## 3. 本机后端前置条件

1. **API 监听 `0.0.0.0:48080`**（`docker-compose.yml` 已映射，真机可访问）
2. **数据库已迁移**（含 `proxy_line_leases`、`user_client_preferences` 等）  
   `bash scripts/dev/migrate-local.sh`
3. **API 镜像为最新代码**（改过 Go 后需重建）  
   `docker compose build api && docker compose up -d api`
4. **健康检查**  
   `curl http://127.0.0.1:48080/health`

### 测试账号（本机库）

| 邮箱 | 密码 | 说明 |
|------|------|------|
| `aa@gmail.com` | `123456` | 可用；需后台有 VIP 套餐才能占线/连接 |
| `test1@example.com` | `test123456` | 种子用户，通常已有订阅 |
| `admin@vpn.local` | `admin123456` | 管理后台 |

无套餐时：管理后台 `http://localhost:44080` → 用户管理 → 设置套餐。

在线节点少时：后台给测试用户绑定节点 **「广州」**（本机常见 online 节点）。

---

## 4. 安装 Debug 包

```bash
cd apps/android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

或 Android Studio：**Run**（默认 Debug）。

**务必装 Debug 包**；Release 仍指向线上域名。

---

## 5. 功能验收清单（对标快帆）

登录 `aa@gmail.com` 后按顺序测：

| # | 功能 | 操作 | 预期 |
|---|------|------|------|
| 1 | 连接页顶栏 | 连接 Tab | UID、VIP、到期、多IP 标签 |
| 2 | 连接页详情 | 选节点「广州」→ 连接 | 测速、场景标签、出口 IP（连上后） |
| 3 | 占线/释放 | 连接后点「释放线路」 | 成功提示；再连会重新 acquire |
| 4 | IP 设置 | 顶栏菜单 → IP设置 | 多IP/单IP 切换成功 |
| 5 | 我的设备 | 菜单 → 查看设备 | 配额、踢其他设备（需两设备登录） |
| 6 | 心跳 | 连接保持 1 分钟 | 管理后台用户详情可见 VPN/出口 IP |

### API 快速回归（不装 App）

```bash
MEMBER_EMAIL=aa@gmail.com MEMBER_PASS=123456 bash scripts/dev/test-kuaifan-api.sh
```

---

## 6. 常见问题

### 登录一直转圈 / 网络异常

- 手机浏览器打开 `http://<电脑IP>:48080/health`，应看到 `{"status":"ok",...}`
- 打不开：检查 **同一 Wi-Fi**、电脑防火墙是否放行 **48080**
- 确认装的是 **Debug** 包且 `debug.api.base` 正确

### 模拟器能连、真机不能

- 真机 **不能** 用 `10.0.2.2`，必须改成电脑 **局域网 IP**
- 重新 `installDebug` 后再试

### 占线失败「暂无有效套餐」

后台给用户分配套餐，或 SQL/API 分配后重登。

### 占线失败「线路配额已满」

套餐 `devices` 为 0 时配额按 1 计；后台把 VIP 套餐 **设备数** 改为 ≥2。

### 连接失败 / 无可用节点

- 会员节点列表需有 **online** 节点（如「广州」）
- 后台 **会员绑定节点** 不要绑死到 offline 节点

### 改了 Go 接口仍 404

本机 API 容器可能是旧二进制：

```bash
docker compose build api && docker compose up -d api
bash scripts/dev/migrate-local.sh
```

---

## 7. 给自动化 / AI 联调用的约定

便于脚本或 Agent 在同一台开发机上回归：

```bash
# 1. 环境
export API_BASE=http://127.0.0.1:48080/api/v1
export MEMBER_EMAIL=aa@gmail.com MEMBER_PASS=123456

# 2. API 冒烟
bash scripts/dev/test-kuaifan-api.sh

# 3. 写 Android 调试地址并装包
bash scripts/dev/android-local-api.sh
cd apps/android && ./gradlew :app:installDebug

# 4. adb 日志（可选）
adb logcat -s OkHttp VpnMemberApp AppDebugLogger
```

Logcat 过滤连接/占线：`adb logcat | rg -i "connect|acquire|release|ExitIp|probe"`

---

## 8. 相关文件

| 文件 | 作用 |
|------|------|
| `app/build.gradle.kts` | Debug/Release 分域名 |
| `apps/android/local.properties` | 真机 API 地址（git 忽略） |
| `app/src/debug/res/xml/network_security_config.xml` | Debug 允许 HTTP |
| `scripts/dev/android-local-api.sh` | 写配置 + 检查 API |
| `scripts/dev/test-kuaifan-api.sh` | 对标功能 API 冒烟 |
| [Android打包与常用配置.md](Android打包与常用配置.md) | 打包、签名、线上域名 |
