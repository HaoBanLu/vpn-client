# Mihomo 内核模块

自研 App VPN 隧道使用 [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) 的 `core` Kotlin 绑定 + native `libbridge.so` / `libclash.so`。

## 首次构建 / CI

```bash
cd apps/android
bash scripts/setup-mihomo-native.sh          # 默认 v2.11.30
./gradlew :app:assembleDebug
```

`setup-mihomo-native.sh` 从 CMFA Release APK 提取各 ABI 的 native 库到 `mihomo-core/src/main/jniLibs/`。

## 源码

- Kotlin bridge：`mihomo-core/src/main/java/com/github/kr328/clash/`（源自 CMFA core，GPL-3.0）
- 可选参考：`vendor/cmfa/`（sparse clone，不提交）

## 与 libbox 的区别

| 项 | libbox（已移除） | Mihomo |
|----|------------------|--------|
| 配置 | sing-box JSON | Clash YAML |
| 与订阅 | 双栈生成 | 与 `/subscription/clash` 同源 |
| DNS fake-ip | sing-box 1.10 边界多 | 与 Clash Verge 一致 |
