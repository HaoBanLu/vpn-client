mod vpn;
mod privacy;

#[cfg(desktop)]
mod tray;

#[cfg(not(desktop))]
mod tray {
    /// 移动端无系统托盘；保留同名命令避免 invoke_handler 分叉。
    pub struct HideOnCloseState(pub std::sync::Mutex<bool>);

    #[tauri::command]
    pub fn tray_set_hide_on_close(_enabled: bool) -> Result<(), String> {
        Ok(())
    }

    #[tauri::command]
    pub fn tray_update_tooltip(_text: String) -> Result<(), String> {
        Ok(())
    }
}

use tauri::Manager;
use vpn::VpnState;

#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
use vpn::desktop::DesktopVpnManager;

fn vpn_plugin<R: tauri::Runtime>() -> tauri::plugin::TauriPlugin<R> {
    tauri::plugin::Builder::<R, ()>::new("vpn")
        .setup(|app, api| {
            #[cfg(target_os = "android")]
            {
                let handle = api.register_android_plugin("com.vpn.kuayun.vpn", "VpnPlugin")?;
                app.manage(vpn::mobile::MobileVpnHandle(handle));
            }
            #[cfg(not(target_os = "android"))]
            {
                let _ = (app, api);
            }
            Ok(())
        })
        .build()
}

/// 前端就绪后显示主窗口（配置里 visible:false，避免启动白闪）。
#[tauri::command]
fn boot_reveal_main(app: tauri::AppHandle) -> Result<(), String> {
    #[cfg(desktop)]
    {
        if let Some(main) = app.get_webview_window("main") {
            let _ = main.set_title("跨云");
            let _ = main.set_skip_taskbar(false);
            let _ = main.center();
            main.show().map_err(|e| e.to_string())?;
            let _ = main.set_focus();
        }
    }
    #[cfg(mobile)]
    {
        let _ = app;
    }
    Ok(())
}

/// reqwest/rustls 在 Android 等目标上不会自动安装 crypto provider，启动前必须显式注册。
fn init_rustls_crypto_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    init_rustls_crypto_provider();

    let mut builder = tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(vpn_plugin())
        .manage(VpnState::default());

    #[cfg(desktop)]
    {
        builder = builder
            .plugin(tauri_plugin_updater::Builder::new().build())
            .plugin(tauri_plugin_process::init())
            .manage(tray::HideOnCloseState(std::sync::Mutex::new(true)));
    }

    builder
        .setup(|app| {
            #[cfg(desktop)]
            {
                // 主窗由 tauri.conf.json 创建（visible:false）；此处只补图标与托盘
                if let Some(main) = app.get_webview_window("main") {
                    let icon = tauri::include_image!("icons/32x32.png");
                    let _ = main.set_icon(icon);
                    let _ = main.hide();
                }
                tray::setup(app.handle())?;
            }
            #[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
            {
                app.manage(DesktopVpnManager::default());
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            boot_reveal_main,
            vpn::vpn_platform_info,
            vpn::vpn_prepare,
            vpn::vpn_connect,
            vpn::vpn_reconnect,
            vpn::vpn_disconnect,
            vpn::vpn_status,
            vpn::vpn_stats,
            vpn::vpn_probe,
            vpn::vpn_heal,
            vpn::tcp_connect_latency,
            vpn::vpn_kill_switch_release,
            vpn::vpn_kill_switch_status,
            vpn::vpn_install_apk_update,
            vpn::vpn_get_pending_apk_update,
            vpn::vpn_try_install_pending_apk,
            vpn::vpn_list_installed_apps,
            vpn::vpn_get_direct_connect_packages,
            vpn::vpn_set_direct_connect_packages,
            vpn::vpn_request_installed_apps_permission,
            vpn::vpn_get_stability_status,
            vpn::vpn_set_boot_auto_connect,
            vpn::vpn_open_vpn_settings,
            vpn::vpn_open_battery_optimization_settings,
            vpn::vpn_open_external_url,
            tray::tray_update_tooltip,
            tray::tray_set_hide_on_close,
            privacy::privacy_detect_local_ipv6,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
