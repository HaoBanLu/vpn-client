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
#[cfg(desktop)]
use tauri::{WebviewUrl, WebviewWindowBuilder};
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

/// 启动完成：先显示主窗口并抢焦点，再关闭 splash，减少切换空隙闪桌面。
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
        // 主窗已显示后再关 splash（splash 为 alwaysOnTop，顺序可避免露桌面）
        if let Some(splash) = app.get_webview_window("splash") {
            let _ = splash.close();
        }
    }
    #[cfg(mobile)]
    {
        let _ = app;
    }
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(vpn_plugin())
        .manage(VpnState::default());

    #[cfg(desktop)]
    {
        builder = builder
            .plugin(tauri_plugin_updater::Builder::new().build())
            .manage(tray::HideOnCloseState(std::sync::Mutex::new(true)));
    }

    builder
        .setup(|app| {
            #[cfg(desktop)]
            {
                // 主窗口延后创建且默认不可见：配置里只放 splash，避免启动时黑窗闪现
                let main = WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                    .title("跨云")
                    // 侧栏(~200) + 主内容(~640) + 边距；比 1200×800 更紧凑，避免两侧大块留白
                    .inner_size(980.0, 680.0)
                    .min_inner_size(860.0, 600.0)
                    .resizable(true)
                    .maximizable(false)
                    .visible(false)
                    .focused(false)
                    .skip_taskbar(true)
                    .build()?;

                let icon = tauri::include_image!("icons/32x32.png");
                let _ = main.set_icon(icon);
                let _ = main.hide();

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
            tray::tray_update_tooltip,
            tray::tray_set_hide_on_close,
            privacy::privacy_detect_local_ipv6,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
