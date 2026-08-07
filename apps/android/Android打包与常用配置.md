# 跨云 Android 打包与常用配置

本文档记录 Android Studio 日常开发、真机安装、Release 打包、接口域名修改等常用操作。

## 打开工程

在 Android Studio 中打开目录：

```text
D:\Code\Go-www\vpn\apps\android
```

不要直接打开 `D:\Code\Go-www\vpn` 根目录，否则 Android Studio 可能无法正确识别 Android Gradle 工程。

## 日常调试包

适合自己开发、真机测试、模拟器测试。

### Android Studio 打包

1. 点击顶部菜单 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
2. 等待构建完成后点击右下角 **locate**。
3. 真机优先安装：

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### Gradle 命令打包

```bash
cd apps/android
./gradlew :app:assembleDebug
```

Debug 输出目录：

```text
app/build/outputs/apk/debug/
```

常用 APK：

- `app-arm64-v8a-debug.apk`：真机推荐，大多数 Android 手机使用。
- `app-armeabi-v7a-debug.apk`：少量旧 32 位设备使用。
- `app-x86_64-debug.apk`：模拟器使用。

## Release 打包

当前版本：**3.15.1**（`versionCode` 46）。Release 已开启代码压缩与资源压缩；**优先使用正式 Release 签名**（见下文）。

### 配置正式签名（首次必做）

```bash
cd apps/android
bash scripts/create-release-keystore.sh
```

脚本会在 `keystore/kuayun-release.keystore` 生成密钥，并把路径/密码写入 `local.properties`（已 git 忽略）。

也可手动配置，复制 `keystore.properties.example` 为 `keystore.properties` 并填写：

```properties
storeFile=keystore/kuayun-release.keystore
storePassword=...
keyAlias=key0
keyPassword=...
```

当前工程已与 `app/release/app-arm64-v8a-release.apk` 使用**同一套 Release 签名**（keystore 在 `keystore/kuayun-release.keystore`，配置在本地 `keystore.properties`，均不提交 git）。

未配置正式签名时，`assembleRelease` **会直接失败**（不再静默回退 debug 签名）；临时内测可加 `-PuseDebugSigning`。

发版后校验签名：

```bash
bash scripts/verify-release-signature.sh
```

**产物目录：**

| 构建方式 | APK 位置 |
|----------|----------|
| Android Studio（Generate Signed APK） | `app/release/` |
| 命令行 `./gradlew :app:assembleRelease` | `app/build/outputs/apk/release/` |

若命令行构建后也需要放到 `app/release/`，执行：

```bash
bash scripts/copy-release-apk.sh
```

### Android Studio 生成签名包

适合发给用户、内测分发、正式发布。

1. 点击顶部菜单 **Build > Generate Signed App Bundle or APK**。
2. 选择 **APK**。
3. 选择已有 Keystore（与 `keystore/kuayun-release.keystore` 一致），或点击 **Create new** 新建。
4. 选择构建类型 **release**。
5. 完成后安装 `arm64-v8a` 包。

Release 输出目录：

```text
app/build/outputs/apk/release/
```

真机推荐文件：

```text
app-arm64-v8a-release.apk
```

命令行构建（已配置正式签名时产物为 Release 签名包）：

```bash
cd apps/android
./gradlew :app:assembleRelease
```

```text
app-arm64-v8a-release.apk
```

真机请安装 **arm64-v8a** 包；模拟器用 **x86_64** 包。

首次构建前请执行 `bash scripts/setup-mihomo-native.sh` 拉取 Mihomo native 库。

**GitHub Tag 发版**：[`app-release.yml`](../../.github/workflows/app-release.yml) 会在 CI 中自动执行上述脚本；全量 arm64 Release 约 **49MB**。勿使用 **v3.14.4** 等未拉 native 的空壳包（~2.5MB）。

### Gradle 命令生成 Release

```bash
cd apps/android
./gradlew :app:assembleRelease
```

## APK 体积说明

项目使用 `mihomo-core` 模块，内置 Mihomo native 库（`libbridge.so` / `libclash.so`）。当前已在 `app/build.gradle.kts` 开启 ABI 拆分：

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("armeabi-v7a", "arm64-v8a", "x86_64")
        isUniversalApk = false
    }
}
```

因此构建后会生成多个 APK。真机通常安装 `arm64-v8a` 包即可；Release 约 **49MB**（已剔除内置 geodata），Debug 略大（含 assets 兜底）。不要安装通用包，否则可能接近 200MB。

### Geodata 与隐私

- **Release**：APK 不含 `geosite.dat` / `geoip.metadb`（`mergeReleaseAssets` 剔除），运行时由 `MihomoGeoAssetManager` 从 CDN 下载。
- **全流量（`route_mode=full`，App 默认）**：**不等待** geodata 即可连接；后台仍会尝试下载（供将来分流）。弱网地区（缅甸/曼谷等）请勿依赖 jsdelivr 首连。
- **分流（`split`）**：仍要求本地 geodata + ruleset 就绪后才连接。
- **Debug**：可选执行 `./gradlew fetchMihomoGeodata` 将 geodata 打入 `app/src/main/assets/mihomo/`，离线调试时使用。
- **隐私门控**：用户注册勾选条款后写入 `privacy_accepted`；仅在此之后才会后台下载 geodata。已登录老用户启动时自动补标记（`ensurePrivacyAcceptedIfLoggedIn`），不增加页面。

## 安装到真机

### Android Studio 直接运行

1. 手机开启 **开发者选项** 和 **USB 调试**。
2. 数据线连接电脑（或选择模拟器）。
3. Android Studio 顶部设备选择目标设备。
4. 点击 **Run**。

**VPN 已连接时出现 `Couldn't terminate previous instance of app`：**

跨云 VPN 前台服务会让旧进程难以被 Studio 结束。任选其一：

| 方式 | 操作 |
|------|------|
| **改 Run 配置（推荐）** | Run → Edit Configurations → app → 取消勾选 **Terminate previous instance** /「启动前终止旧实例」 |
| **先断 VPN 再 Run** | App 内点「断开」→ 再点 Run |
| **Gradle 安装（已内置 force-stop）** | 终端：`cd apps/android` → `.\gradlew installDebug` |
| **一键脚本（Windows）** | `powershell -ExecutionPolicy Bypass -File scripts/android-redeploy.ps1` |
| **手动 adb** | `adb shell am force-stop com.vpn.member` 后再 Run |

模拟器仍失败时：**Cold Boot** 模拟器（Device Manager → 下拉 → Cold Boot Now），再执行上述步骤。

### adb 安装

Debug 包：

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Release 签名包：

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## 修改版本号

版本号在 `app/build.gradle.kts` 的 `defaultConfig` 中配置：

```kotlin
defaultConfig {
    versionCode = 46
    versionName = "3.15.1"
}
```

| 字段 | 含义 | 示例 |
|------|------|------|
| `versionCode` | 整数，每次发版必须递增；应用商店、热更新接口依赖此值 | `43` → `44` → `45` |
| `versionName` | 展示给用户的版本字符串 | `3.12`、`3.14` |

修改后重新打包即可。App 内「关于」页、登录设备上报的 `app_version` 均读取 `BuildConfig.VERSION_NAME`，无需改其他文件。

**发版建议**：每次对外发布至少递增 `versionCode`；`versionName` 按语义化版本维护（如 `1.0.0`）。

## 修改 App 名称

桌面图标下方显示的名称来自 `app/src/main/res/values/strings.xml`：

```xml
<string name="app_name">跨云</string>
```

`AndroidManifest.xml` 通过 `android:label="@string/app_name"` 引用该值。

仅改桌面名称时，改 `strings.xml` 后重新安装即可。

**注意**：部分页面文案仍硬编码了「跨云」，若需全局换品牌名，还需搜索并修改以下文件中的文案（按需）：

| 文件 | 说明 |
|------|------|
| `ui/screens/SplashScreen.kt` | 启动页标题 |
| `ui/components/KuayunComponents.kt` | 品牌头、云图标描述 |
| `ui/screens/AuthScreens.kt` | 登录/注册副标题 |
| `ui/screens/AboutScreen.kt` | 关于页标题与介绍 |
| `ui/screens/ProfileScreen.kt` | 「关于跨云」入口 |
| `vpn/VpnTunnelService.kt` | VPN 通知栏标题 |
| `update/AppUpdateInstaller.kt` | 更新安装对话框标题 |

## 修改包名（applicationId）

包名决定 APK 在设备上的唯一身份，也影响应用商店上架与覆盖安装。

当前配置在 `app/build.gradle.kts`：

```kotlin
android {
    namespace = "com.vpn.member"
    defaultConfig {
        applicationId = "com.vpn.member"
    }
}
```

- **`applicationId`**：安装到手机上的包名，改后会被系统视为**新应用**（无法覆盖安装旧包，需先卸载）。
- **`namespace`**：R 类与 BuildConfig 的命名空间，通常与 `applicationId` 保持一致。

**推荐改法（Android Studio）**：

1. 在 Project 视图展开 `app/src/main/java/com/vpn/member`。
2. 右键 `member` 包 → **Refactor > Rename**。
3. 选择 **Rename package**，输入新包名（如 `com.example.vpn`）。
4. 勾选 **Search in comments and strings** 按需处理。
5. 同步修改 `build.gradle.kts` 中的 `applicationId` 与 `namespace`。
6. 检查 `AndroidManifest.xml`、ProGuard 规则（`proguard-rules.pro` 中的 `com.vpn.member.**`）是否需同步。

改包名涉及面较广，改完后务必完整编译并真机验证登录、VPN、热更新等核心流程。

## 更换 Logo / 启动图标

### 桌面图标（Launcher Icon）

`AndroidManifest.xml` 引用：

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

当前使用 **Adaptive Icon**（Android 8.0+），定义在：

```text
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```

图标由两部分组成：

| 资源 | 路径 | 作用 |
|------|------|------|
| 背景 | `res/drawable/ic_launcher_background.xml` | 蓝青渐变底色 |
| 前景 | `res/drawable/ic_kuayun_cloud.xml` | 云朵矢量图 |

**推荐：用 Android Studio 生成全套尺寸**

1. 右键 `app/src/main/res` → **New > Image Asset**。
2. **Icon Type** 选 **Launcher Icons (Adaptive and Legacy)**。
3. **Foreground Layer** 上传 PNG/SVG，或选 **Clip Art / Image**。
4. **Background Layer** 选颜色或图片。
5. **Name** 保持 `ic_launcher`，点击 **Next > Finish**。

工具会自动生成 `mipmap-mdpi` ~ `mipmap-xxxhdpi` 等各密度资源。

### App 内品牌云图标

页面内使用的云朵图标（启动页、品牌头、连接页等）来自：

```text
res/drawable/ic_kuayun_cloud.xml       # 大图标 / Adaptive Icon 前景
res/drawable/ic_kuayun_cloud_small.xml # 组件内小图标
```

替换方式：

- **改矢量**：直接编辑上述 XML 中的 `pathData`，或替换为新的 vector drawable。
- **改位图**：将 PNG 放入 `res/drawable-*dpi/`，并在 `KuayunComponents.kt` 的 `KuayunCloudIcon` 中改用 `painterResource(R.drawable.your_icon)`。

### 主题色（可选）

若换 Logo 同时想调整 App 主色，可修改 `ui/theme/KuayunTheme.kt` 中的 `KuayunBlue`、`KuayunCyan` 等颜色常量，以及 `ic_launcher_background.xml` 的渐变色，保持视觉一致。

## 修改接口域名

Release 线上域名在 `app/build.gradle.kts`：

```kotlin
val releaseAppBaseUrl = "https://vpn.eodkko.xyz/"
```

**Debug 包连本机 API** 已独立配置，无需改 Release 地址。完整步骤见 **[本地API调试联调.md](本地API调试联调.md)**。

简要说明：

| 构建类型 | API 根地址 |
|----------|------------|
| **debug** | `local.properties` → `debug.api.base`；未配置时模拟器默认 `http://10.0.2.2:48080/` |
| **release** | `https://vpn.eodkko.xyz/` |

一键配置本机地址：

```bash
bash scripts/dev/android-local-api.sh
cd apps/android && ./gradlew :app:installDebug
```

### 历史写法（仍可用，不推荐）

手动改 `releaseAppBaseUrl` 或 Debug 的 `debug.api.base` 即可，不必同时改两处。

```kotlin
// 模拟器
debug.api.base=http://10.0.2.2:48080/

// 真机
debug.api.base=http://192.168.1.10:48080/
```

注意：Retrofit 的 `baseUrl` 必须保留结尾 `/`。

### Release 使用 HTTP（非 HTTPS）线上 IP

Android 9+ 默认禁止明文 HTTP。Release 包仅对 `app/src/main/res/xml/network_security_config.xml` 白名单内的主机放行。

当前已放行 `192.229.87.112`（与 `releaseAppBaseUrl` 对应）。**更换线上 IP 时需同时改两处**：

1. `app/build.gradle.kts` → `releaseAppBaseUrl`
2. `network_security_config.xml` → 增加对应 `<domain>`

否则登录会报「系统拦截了 HTTP 明文请求」或「无法连接服务器（http://…）」。

## 常见问题

### R8 报 `com.google.errorprone.annotations.*` 缺失

这是 `androidx.security:security-crypto` 间接依赖 Tink 时出现的编译期注解告警。项目已在 `proguard-rules.pro` 中加入：

```proguard
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
```

如果之后升级依赖又出现类似 R8 报错，优先查看：

```text
app/build/outputs/mapping/release/missing_rules.txt
```

并把 AGP 生成的必要规则合并到 `proguard-rules.pro`。

### 修改域名后没有生效

请确认重新构建并安装了新的 APK。Android Studio 有时会复用旧安装包，必要时先卸载手机上的旧 App 再安装。

### Android Studio 能登录，打包 APK 后登录失败

常见原因是 **构建类型不同**：

| 方式 | 构建类型 | 说明 |
|------|----------|------|
| Android Studio 点 Run | Debug | 不混淆代码 |
| Build APK / assembleRelease | Release | 开启 R8 混淆 |

如果 Release 包登录失败但账号密码正确，通常是 R8 混淆导致 Gson 无法解析登录响应。项目已在 `proguard-rules.pro` 保留 `com.vpn.member.data.api.**` 等规则。

排查建议：

1. 先卸载手机上的旧 App，再安装新打的 `arm64-v8a` 包。
2. 日常自测优先装 Debug 包：`app-arm64-v8a-debug.apk`。
3. 如果 Release 仍失败，看登录页错误提示是否变为「网络异常」或「无法连接服务器」。

### 真机无法访问本地后端

确认手机和电脑在同一局域网，并使用电脑局域网 IP。后端服务也需要监听 `0.0.0.0` 或局域网地址，不能只监听 `127.0.0.1`。
