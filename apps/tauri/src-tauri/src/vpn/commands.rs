use super::state::{
    VpnConnectOptions, VpnConnectionState, VpnConnectionStatus, VpnDisconnectOptions, VpnPlatformInfo,
    VpnProbeResult, VpnSessionStats, VpnState,
};
use tauri::{AppHandle, Manager, Runtime, State};

#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
use super::desktop::{DesktopVpnError, DesktopVpnManager};

#[cfg(mobile)]
use super::mobile::MobileVpnHandle;

#[cfg(target_os = "android")]
fn parse_android_plugin_status(value: serde_json::Value) -> Result<VpnConnectionStatus, String> {
    let state_str = value
        .get("state")
        .and_then(|v| v.as_str())
        .unwrap_or("disconnected");
    let state = match state_str {
        "connected" => VpnConnectionState::Connected,
        "connecting" => VpnConnectionState::Connecting,
        "failed" => VpnConnectionState::Failed,
        _ => VpnConnectionState::Disconnected,
    };
    let error = match value.get("error") {
        Some(serde_json::Value::String(s)) if !s.is_empty() => Some(s.clone()),
        _ => None,
    };
    let system_vpn_active = value
        .get("systemVpnActive")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);
    Ok(VpnConnectionStatus {
        state,
        error,
        node_name: None,
        system_vpn_active,
    })
}

#[cfg(target_os = "android")]
async fn sync_android_vpn_status<R: Runtime>(
    app: &AppHandle<R>,
    state: &State<'_, VpnState>,
) -> Result<VpnConnectionStatus, String> {
    let value = app
        .state::<MobileVpnHandle<R>>()
        .0
        .run_mobile_plugin::<serde_json::Value>("getStatus", ())
        .map_err(|e| e.to_string())?;
    let status = parse_android_plugin_status(value)?;
    state.update_status(app, status.clone());
    Ok(status)
}

#[tauri::command]
pub fn vpn_platform_info() -> VpnPlatformInfo {
    #[cfg(target_os = "android")]
    {
        VpnPlatformInfo {
            platform: "android".into(),
            vpn_supported: true,
            implementation: "android-vpn".into(),
            notes: None,
        }
    }
    #[cfg(target_os = "ios")]
    {
        VpnPlatformInfo {
            platform: "ios".into(),
            vpn_supported: false,
            implementation: "network-extension".into(),
            notes: Some("Spike stub only; requires PacketTunnelProvider entitlement".into()),
        }
    }
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        let platform = if cfg!(target_os = "windows") {
            "windows"
        } else if cfg!(target_os = "macos") {
            "macos"
        } else {
            "linux"
        };
        VpnPlatformInfo {
            platform: platform.into(),
            vpn_supported: true,
            implementation: "desktop-vpn".into(),
            notes: None,
        }
    }
    #[cfg(not(any(
        target_os = "android",
        target_os = "ios",
        target_os = "windows",
        target_os = "macos",
        target_os = "linux"
    )))]
    {
        VpnPlatformInfo {
            platform: "unknown".into(),
            vpn_supported: false,
            implementation: "none".into(),
            notes: None,
        }
    }
}

#[tauri::command]
pub async fn vpn_prepare<R: Runtime>(app: AppHandle<R>) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<bool>("prepare", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(target_os = "ios")]
    {
        let _ = app;
        return Ok(false);
    }
    #[cfg(not(any(target_os = "android", target_os = "ios")))]
    {
        let _ = app;
        Ok(true)
    }
}

#[tauri::command]
pub async fn vpn_connect<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, VpnState>,
    options: VpnConnectOptions,
) -> Result<(), String> {
    if options.config_json.trim().is_empty() {
        return Err("config is empty".into());
    }

    state.update_status(
        &app,
        VpnConnectionStatus {
            state: VpnConnectionState::Connecting,
            error: None,
            node_name: options.node_name.clone(),
            system_vpn_active: false,
        },
    );

    #[cfg(target_os = "android")]
    {
        let result = app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<serde_json::Value>("connect", &options)
            .map_err(|e| e.to_string());
        if result.is_err() {
            state.update_status(
                &app,
                VpnConnectionStatus {
                    state: VpnConnectionState::Failed,
                    error: result.as_ref().err().cloned(),
                    node_name: options.node_name,
                    system_vpn_active: false,
                },
            );
        }
        return result.map(|_| ());
    }

    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        let app_handle = app.clone();
        let connect_options = options.clone();
        let result = tauri::async_runtime::spawn_blocking(move || {
            let manager = app_handle.state::<DesktopVpnManager>();
            manager.connect(&app_handle, connect_options)
        })
        .await
        .map_err(|e| e.to_string())?;
        match result {
            Ok(()) => Ok(()),
            Err(e) => {
                state.update_status(
                    &app,
                    VpnConnectionStatus {
                        state: VpnConnectionState::Failed,
                        error: Some(e.to_string()),
                        node_name: options.node_name,
                    system_vpn_active: false,
                    },
                );
                Err(e.to_string())
            }
        }
    }

    #[cfg(target_os = "ios")]
    {
        let _ = (app, state, options);
        Err("iOS VPN spike: Network Extension not implemented".into())
    }

    #[cfg(not(any(
        target_os = "android",
        target_os = "ios",
        target_os = "windows",
        target_os = "macos",
        target_os = "linux"
    )))]
    {
        Err("VPN not supported on this platform".into())
    }
}

#[tauri::command]
pub async fn vpn_reconnect<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, VpnState>,
    options: VpnConnectOptions,
) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        if options.config_json.trim().is_empty() {
            return Err("config is empty".into());
        }
        state.update_status(
            &app,
            VpnConnectionStatus {
                state: VpnConnectionState::Connecting,
                error: None,
                node_name: options.node_name.clone(),
            system_vpn_active: false,
            },
        );
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<serde_json::Value>("reconnect", &options)
            .map_err(|e| e.to_string())
            .map(|_| ());
    }

    #[cfg(not(target_os = "android"))]
    {
        if let Err(e) = vpn_disconnect(
            app.clone(),
            state.clone(),
            Some(VpnDisconnectOptions {
                user_initiated: true,
                kill_switch_enabled: false,
            }),
        )
        .await
        {
            return Err(e);
        }
        std::thread::sleep(std::time::Duration::from_millis(300));
        vpn_connect(app, state, options).await
    }
}

#[tauri::command]
pub async fn vpn_disconnect<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, VpnState>,
    #[allow(unused_variables)] options: Option<VpnDisconnectOptions>,
) -> Result<(), String> {
    let opts = options.unwrap_or_default();

    #[cfg(target_os = "android")]
    {
        app.state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<serde_json::Value>("disconnect", ())
            .map_err(|e| e.to_string())?;
    }

    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        let manager = app.state::<DesktopVpnManager>();
        manager
            .disconnect(&app, opts.clone())
            .map_err(|e: DesktopVpnError| e.to_string())?;
    }

    if opts.user_initiated {
        state.update_status(
            &app,
            VpnConnectionStatus {
                state: VpnConnectionState::Disconnected,
                error: None,
                node_name: None,
                system_vpn_active: false,
            },
        );
    }
    Ok(())
}

#[tauri::command]
pub async fn vpn_status<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, VpnState>,
    kill_switch_enabled: Option<bool>,
) -> Result<VpnConnectionStatus, String> {
    #[cfg(target_os = "android")]
    {
        return sync_android_vpn_status(&app, &state).await;
    }
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        let manager = app.state::<DesktopVpnManager>();
        return manager.sync_status(&app, &state, kill_switch_enabled.unwrap_or(false));
    }
    #[cfg(not(any(
        target_os = "android",
        target_os = "windows",
        target_os = "macos",
        target_os = "linux"
    )))]
    {
        let _ = app;
        Ok(state.snapshot_status())
    }
}

#[tauri::command]
pub async fn vpn_stats<R: Runtime>(app: AppHandle<R>) -> VpnSessionStats {
    let mut stats = app.state::<VpnState>().snapshot_stats();
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        use super::state::VpnConnectionState;
        let connected =
            app.state::<VpnState>().snapshot_status().state == VpnConnectionState::Connected;
        if connected {
            let app_handle = app.clone();
            let traffic = tauri::async_runtime::spawn_blocking(move || {
                let manager = app_handle.state::<DesktopVpnManager>();
                let session = manager.session_traffic();
                let rates = manager
                    .api_port()
                    .and_then(super::desktop_traffic::fetch_instant_rates);
                (session, rates)
            })
            .await
            .ok();
            if let Some((session, rates)) = traffic {
                if let Some((upload, download)) = session {
                    stats.upload_bytes = upload;
                    stats.download_bytes = download;
                }
                if let Some((up, down)) = rates {
                    stats.upload_bps = up;
                    stats.download_bps = down;
                }
            }
        }
    }
    #[cfg(target_os = "android")]
    {
        use super::state::VpnConnectionState;
        let connected =
            app.state::<VpnState>().snapshot_status().state == VpnConnectionState::Connected;
        if connected {
            if let Ok(value) = app
                .state::<MobileVpnHandle<R>>()
                .0
                .run_mobile_plugin::<serde_json::Value>("getStats", ())
            {
                fn json_u64(value: &serde_json::Value, key: &str) -> Option<u64> {
                    value.get(key).and_then(|x| {
                        x.as_u64()
                            .or_else(|| x.as_i64().map(|v| v.max(0) as u64))
                            .or_else(|| x.as_f64().map(|v| v.max(0.0) as u64))
                    })
                }
                if let Some(v) = json_u64(&value, "uploadBytes") {
                    stats.upload_bytes = v;
                }
                if let Some(v) = json_u64(&value, "downloadBytes") {
                    stats.download_bytes = v;
                }
                // 优先用原生会话时长（VpnSessionStatsTracker），避免与 Rust 计时漂移
                if let Some(v) = json_u64(&value, "durationMs") {
                    stats.duration_ms = v;
                }
                if let Some(v) = json_u64(&value, "uploadBps") {
                    stats.upload_bps = v;
                }
                if let Some(v) = json_u64(&value, "downloadBps") {
                    stats.download_bps = v;
                }
            }
        }
    }
    stats
}

#[tauri::command]
pub async fn vpn_probe<R: Runtime>(app: AppHandle<R>) -> Result<VpnProbeResult, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<VpnProbeResult>("probe", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        let app_handle = app.clone();
        return tauri::async_runtime::spawn_blocking(move || {
            app_handle.state::<DesktopVpnManager>().probe()
        })
        .await
        .map_err(|e| e.to_string())?;
    }
    #[cfg(not(any(
        target_os = "android",
        target_os = "windows",
        target_os = "macos",
        target_os = "linux"
    )))]
    {
        let _ = app;
        Err("当前平台暂不支持 VPN 网络探测".into())
    }
}

/// 断网/网卡恢复轻量自愈：重刷系统代理；调用方须再 probe 验证用户路径。
#[tauri::command]
pub async fn vpn_heal<R: Runtime>(app: AppHandle<R>) -> Result<(), String> {
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        let app_handle = app.clone();
        return tauri::async_runtime::spawn_blocking(move || {
            app_handle.state::<DesktopVpnManager>().heal()
        })
        .await
        .map_err(|e| e.to_string())?;
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        let _ = app;
        Ok(())
    }
}

#[tauri::command]
pub fn tcp_connect_latency(host: String, port: u16, timeout_ms: u64) -> Result<Option<u64>, String> {
    use std::net::{SocketAddr, TcpStream};
    use std::time::{Duration, Instant};

    let addr: SocketAddr = format!("{host}:{port}")
        .parse()
        .map_err(|e| format!("invalid endpoint: {e}"))?;
    let timeout = Duration::from_millis(timeout_ms.max(1));
    let start = Instant::now();
    match TcpStream::connect_timeout(&addr, timeout) {
        Ok(_) => Ok(Some(start.elapsed().as_millis() as u64)),
        Err(_) => Ok(None),
    }
}

#[tauri::command]
pub fn vpn_kill_switch_release() -> Result<(), String> {
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        return super::desktop_mode::release_kill_switch().map_err(|e| e.to_string());
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        Ok(())
    }
}

#[tauri::command]
pub fn vpn_kill_switch_status() -> Result<bool, String> {
    #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
    {
        Ok(super::kill_switch::is_engaged())
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        Ok(false)
    }
}

#[derive(serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ApkUpdateOptions {
    pub url: String,
    pub version_label: Option<String>,
    pub version_code: Option<i32>,
}

/// Android：经 VpnPlugin 下载并调起系统安装；其它平台返回错误由前端回退外链。
#[tauri::command]
pub async fn vpn_install_apk_update<R: Runtime>(
    app: AppHandle<R>,
    options: ApkUpdateOptions,
) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        #[derive(serde::Serialize)]
        #[serde(rename_all = "camelCase")]
        struct Args {
            url: String,
            version_label: String,
            version_code: i32,
        }
        let args = Args {
            url: options.url,
            version_label: options
                .version_label
                .unwrap_or_else(|| "latest".into()),
            version_code: options.version_code.unwrap_or(0),
        };
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<serde_json::Value>("installApkUpdate", &args)
            .map(|_| ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, options);
        Err("当前平台请使用桌面 updater 或外链下载".into())
    }
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct PendingApkUpdateInfo {
    pub version_label: String,
    pub version_code: i32,
    #[serde(default)]
    pub needs_install_permission: bool,
}

#[derive(serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PendingApkUpdateResponse {
    pub pending: Option<PendingApkUpdateInfo>,
}

#[derive(serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TryInstallPendingApkResponse {
    pub result: String,
}

/// Android：读取待安装 APK 状态。
#[tauri::command]
pub async fn vpn_get_pending_apk_update<R: Runtime>(
    app: AppHandle<R>,
) -> Result<PendingApkUpdateResponse, String> {
    #[cfg(target_os = "android")]
    {
        let value = app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<serde_json::Value>("getPendingApkUpdate", ())
            .map_err(|e| e.to_string())?;
        let pending = value.get("pending").and_then(|p| {
            if p.is_null() {
                None
            } else {
                serde_json::from_value(p.clone()).ok()
            }
        });
        return Ok(PendingApkUpdateResponse { pending });
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(PendingApkUpdateResponse { pending: None })
    }
}

/// Android：尝试调起待安装 APK 的系统安装器。
#[tauri::command]
pub async fn vpn_try_install_pending_apk<R: Runtime>(
    app: AppHandle<R>,
) -> Result<TryInstallPendingApkResponse, String> {
    #[cfg(target_os = "android")]
    {
        let value = app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<serde_json::Value>("tryInstallPendingApk", ())
            .map_err(|e| e.to_string())?;
        let result = value
            .get("result")
            .and_then(|v| v.as_str())
            .unwrap_or("failed")
            .to_string();
        return Ok(TryInstallPendingApkResponse { result });
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(TryInstallPendingApkResponse {
            result: "no_pending".into(),
        })
    }
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct InstalledAppInfo {
    pub package_name: String,
    pub label: String,
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ListInstalledAppsResult {
    pub apps: Vec<InstalledAppInfo>,
    pub selected_packages: Vec<String>,
    pub needs_permission: bool,
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DirectConnectPackagesResult {
    pub packages: Vec<String>,
    pub count: i32,
}

/// Android：枚举已安装应用（应用直连勾选）。
#[tauri::command]
pub async fn vpn_list_installed_apps<R: Runtime>(
    app: AppHandle<R>,
) -> Result<ListInstalledAppsResult, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<ListInstalledAppsResult>("listInstalledApps", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Err("应用直连仅 Android 支持".into())
    }
}

#[tauri::command]
pub async fn vpn_get_direct_connect_packages<R: Runtime>(
    app: AppHandle<R>,
) -> Result<DirectConnectPackagesResult, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<DirectConnectPackagesResult>("getDirectConnectPackages", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(DirectConnectPackagesResult {
            packages: vec![],
            count: 0,
        })
    }
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SetDirectConnectPackagesOptions {
    pub packages: Vec<String>,
}

#[tauri::command]
pub async fn vpn_set_direct_connect_packages<R: Runtime>(
    app: AppHandle<R>,
    options: SetDirectConnectPackagesOptions,
) -> Result<DirectConnectPackagesResult, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<DirectConnectPackagesResult>("setDirectConnectPackages", &options)
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, options);
        Err("应用直连仅 Android 支持".into())
    }
}

#[tauri::command]
pub async fn vpn_request_installed_apps_permission<R: Runtime>(
    app: AppHandle<R>,
) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<bool>("requestInstalledAppsPermission", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Ok(false)
    }
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AndroidStabilityStatus {
    pub always_on_configured: bool,
    pub lockdown_configured: bool,
    pub battery_optimization_ignored: bool,
    pub boot_auto_connect_enabled: bool,
    pub hardening_done_count: i32,
    pub hardening_total: i32,
}

#[tauri::command]
pub async fn vpn_get_stability_status<R: Runtime>(
    app: AppHandle<R>,
) -> Result<AndroidStabilityStatus, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<AndroidStabilityStatus>("getStabilityStatus", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Err("仅 Android 支持".into())
    }
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SetBootAutoConnectOptions {
    pub enabled: bool,
}

#[tauri::command]
pub async fn vpn_set_boot_auto_connect<R: Runtime>(
    app: AppHandle<R>,
    options: SetBootAutoConnectOptions,
) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        #[derive(serde::Deserialize)]
        #[serde(rename_all = "camelCase")]
        struct Ret {
            boot_auto_connect_enabled: bool,
        }
        let ret = app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<Ret>("setBootAutoConnect", &options)
            .map_err(|e| e.to_string())?;
        return Ok(ret.boot_auto_connect_enabled);
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, options);
        Err("仅 Android 支持".into())
    }
}

#[tauri::command]
pub async fn vpn_open_vpn_settings<R: Runtime>(app: AppHandle<R>) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<bool>("openVpnSettings", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Err("仅 Android 支持".into())
    }
}

#[tauri::command]
pub async fn vpn_open_battery_optimization_settings<R: Runtime>(
    app: AppHandle<R>,
) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<bool>("openBatteryOptimizationSettings", ())
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = app;
        Err("仅 Android 支持".into())
    }
}

#[derive(serde::Serialize, serde::Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct OpenExternalUrlOptions {
    pub url: String,
}

#[tauri::command]
pub async fn vpn_open_external_url<R: Runtime>(
    app: AppHandle<R>,
    options: OpenExternalUrlOptions,
) -> Result<bool, String> {
    #[cfg(target_os = "android")]
    {
        return app
            .state::<MobileVpnHandle<R>>()
            .0
            .run_mobile_plugin::<bool>("openExternalUrl", &options)
            .map_err(|e| e.to_string());
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (app, options);
        Err("仅 Android 支持外部打开".into())
    }
}
