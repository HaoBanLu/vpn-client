use tauri::{
    menu::{Menu, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    AppHandle, Emitter, Manager, Runtime,
};
use tauri::WindowEvent;

pub const TRAY_ID: &str = "main-tray";

pub fn setup<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<()> {
    let show_i = MenuItem::with_id(app, "tray-show", "显示窗口", true, None::<&str>)?;
    let disconnect_i = MenuItem::with_id(app, "tray-disconnect", "断开连接", true, None::<&str>)?;
    let quit_i = MenuItem::with_id(app, "tray-quit", "退出跨云", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show_i, &disconnect_i, &quit_i])?;

    let icon = app.default_window_icon().cloned().expect("default window icon");

    TrayIconBuilder::with_id(TRAY_ID)
        .icon(icon)
        .tooltip("跨云 · 未连接")
        .menu(&menu)
        .on_menu_event(|app, event| match event.id.as_ref() {
            "tray-show" => show_main_window(app),
            "tray-disconnect" => {
                let _ = app.emit("tray://disconnect", ());
            }
            "tray-quit" => {
                app.exit(0);
            }
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                show_main_window(tray.app_handle());
            }
        })
        .build(app)?;

    if let Some(window) = app.get_webview_window("main") {
        let app_handle = app.clone();
        window.on_window_event(move |event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                if should_hide_on_close(&app_handle) {
                    api.prevent_close();
                    if let Some(win) = app_handle.get_webview_window("main") {
                        let _ = win.hide();
                    }
                }
            }
        });
    }

    Ok(())
}

fn should_hide_on_close<R: Runtime>(app: &AppHandle<R>) -> bool {
    app.try_state::<HideOnCloseState>()
        .map(|s| s.0.lock().map(|v| *v).unwrap_or(true))
        .unwrap_or(true)
}

fn show_main_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}

pub struct HideOnCloseState(pub std::sync::Mutex<bool>);

#[tauri::command]
pub fn tray_set_hide_on_close(enabled: bool, state: tauri::State<'_, HideOnCloseState>) -> Result<(), String> {
    let mut guard = state.0.lock().map_err(|e| e.to_string())?;
    *guard = enabled;
    Ok(())
}

#[tauri::command]
pub fn tray_update_tooltip(app: AppHandle, text: String) -> Result<(), String> {
    let tray = app.tray_by_id(TRAY_ID).ok_or_else(|| "tray not found".to_string())?;
    tray.set_tooltip(Some(text))
        .map_err(|e| e.to_string())
}
