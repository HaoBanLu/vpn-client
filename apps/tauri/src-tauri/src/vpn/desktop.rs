use super::desktop_config::DesktopConnectionMode;
use super::desktop_mode::{engage_kill_switch, parse_connection_mode, patch_config, release_kill_switch};
use super::desktop_process::{
    cleanup_on_startup, friendly_start_error, release_local_proxy_port, select_available_api_port,
    select_available_proxy_port, spawn_mihomo, stop_child,
};
use super::state::{VpnConnectOptions, VpnConnectionState, VpnConnectionStatus, VpnDisconnectOptions};
use super::system_proxy;
use std::fs;
use std::io::{Read, Write};
use std::path::PathBuf;
use std::process::Child;
use std::sync::Mutex;
use std::time::Duration;
use tauri::{AppHandle, Manager, Runtime};
use thiserror::Error;

#[derive(Error, Debug)]
pub enum DesktopVpnError {
    #[error("{0}")]
    BinaryNotFound(String),
    #[error("连接配置无效：{0}")]
    ConfigPatch(String),
    #[error("无法保存连接配置：{0}")]
    ConfigWrite(String),
    #[error("{0}")]
    StartFailed(String),
    #[error("系统代理设置失败：{0}")]
    ProxyFailed(String),
    #[error("Kill Switch：{0}")]
    KillSwitch(String),
}

pub struct DesktopVpnManager {
    child: Mutex<Option<Child>>,
    config_path: Mutex<Option<PathBuf>>,
    proxy_port: Mutex<Option<u16>>,
    api_port: Mutex<Option<u16>>,
    connection_mode: Mutex<DesktopConnectionMode>,
    kill_switch_engaged: Mutex<bool>,
    /// 连接成功时 Mihomo 累计流量基线，用于「本次会话」统计。
    traffic_baseline: Mutex<Option<(u64, u64)>>,
    /// 最近一次成功拉取的会话流量（upload, download），API 瞬时失败时回退，避免 UI 变 0。
    last_session_traffic: Mutex<Option<(u64, u64)>>,
}

impl Default for DesktopVpnManager {
    fn default() -> Self {
        cleanup_on_startup();
        let _ = release_kill_switch();
        Self {
            child: Mutex::new(None),
            config_path: Mutex::new(None),
            proxy_port: Mutex::new(None),
            api_port: Mutex::new(None),
            connection_mode: Mutex::new(DesktopConnectionMode::Proxy),
            kill_switch_engaged: Mutex::new(false),
            traffic_baseline: Mutex::new(None),
            last_session_traffic: Mutex::new(None),
        }
    }
}

impl DesktopVpnManager {
    pub fn connect<R: Runtime>(
        &self,
        app: &AppHandle<R>,
        options: VpnConnectOptions,
    ) -> Result<(), DesktopVpnError> {
        let mode = parse_connection_mode(options.connection_mode.as_deref());
        if let Ok(mut mode_guard) = self.connection_mode.lock() {
            *mode_guard = mode;
        }

        let mut guard = self
            .child
            .lock()
            .map_err(|e| DesktopVpnError::StartFailed(e.to_string()))?;
        self.stop_inner(&mut guard, false)?;

        let binary = resolve_mihomo_binary(app)?;
        let proxy_port = select_available_proxy_port();
        let api_port = select_available_api_port();
        let patched = patch_config(&options.config_json, mode, proxy_port, api_port)
            .map_err(|e| DesktopVpnError::ConfigPatch(e.to_string()))?;
        let config_path = write_runtime_config(app, &patched)?;

        let mut child = spawn_mihomo(&binary, &config_path)
            .map_err(|e| DesktopVpnError::StartFailed(e.to_string()))?;

        std::thread::sleep(Duration::from_millis(500));
        match child.try_wait() {
            Ok(Some(_)) => {
                let detail = read_child_stderr(&mut child);
                let msg = if mode == DesktopConnectionMode::Tun {
                    format!(
                        "mihomo TUN 启动失败: {detail}。TUN 模式可能需要管理员权限或 wintun 驱动，可在「连接与隐私」切回系统代理模式"
                    )
                } else {
                    format!("mihomo exited early: {detail}")
                };
                return Err(DesktopVpnError::StartFailed(friendly_start_error(&msg)));
            }
            Ok(None) => {}
            Err(e) => return Err(DesktopVpnError::StartFailed(e.to_string())),
        }

        if mode == DesktopConnectionMode::Proxy {
            if let Err(e) = system_proxy::enable(proxy_port) {
                stop_child(&mut child);
                return Err(DesktopVpnError::ProxyFailed(e));
            }
        }

        // 对齐 Android / Clash Verge：selector 写入后即 Connected。
        // 不以百度/QQ 等外网探针作为连接成败条件（探针失败≠隧道未建立）。
        if let Some(name) = options.node_name.as_deref() {
            let _ = super::desktop_selector::apply_node_selection(api_port, name);
        }

        if let Ok(mut path_guard) = self.config_path.lock() {
            *path_guard = Some(config_path);
        }
        if let Ok(mut port_guard) = self.proxy_port.lock() {
            *port_guard = Some(proxy_port);
        }
        if let Ok(mut api_guard) = self.api_port.lock() {
            *api_guard = Some(api_port);
        }
        *guard = Some(child);
        let _ = self.set_kill_switch_engaged(false);
        self.capture_traffic_baseline(api_port);

        if let Some(state) = app.try_state::<super::VpnState>() {
            state.update_status(
                app,
                VpnConnectionStatus {
                    state: VpnConnectionState::Connected,
                    error: None,
                    node_name: options.node_name,
                    system_vpn_active: false,
                },
            );
        }
        Ok(())
    }

    pub fn api_port(&self) -> Option<u16> {
        self.api_port.lock().ok().and_then(|g| *g)
    }

    fn capture_traffic_baseline(&self, api_port: u16) {
        if let Ok(mut last) = self.last_session_traffic.lock() {
            *last = None;
        }
        // Mihomo API 刚起来时可能短暂不可用，短重试避免基线缺失
        for attempt in 0..4 {
            if let Some((upload, download)) =
                super::desktop_traffic::fetch_connection_totals(api_port)
            {
                if let Ok(mut baseline) = self.traffic_baseline.lock() {
                    *baseline = Some((upload, download));
                }
                return;
            }
            if attempt + 1 < 4 {
                std::thread::sleep(Duration::from_millis(120));
            }
        }
        eprintln!("[desktop] traffic baseline unavailable after retries (api_port={api_port})");
    }

    /// 相对本次连接起点的会话流量（upload, download）。
    pub fn session_traffic(&self) -> Option<(u64, u64)> {
        let port = self.api_port()?;
        match super::desktop_traffic::fetch_connection_totals(port) {
            Some((upload, download)) => {
                let baseline = self
                    .traffic_baseline
                    .lock()
                    .ok()
                    .and_then(|g| *g)
                    .unwrap_or((0, 0));
                let session = (
                    upload.saturating_sub(baseline.0),
                    download.saturating_sub(baseline.1),
                );
                if let Ok(mut last) = self.last_session_traffic.lock() {
                    *last = Some(session);
                }
                Some(session)
            }
            None => self
                .last_session_traffic
                .lock()
                .ok()
                .and_then(|g| *g),
        }
    }

    pub fn probe(&self) -> Result<super::state::VpnProbeResult, String> {
        // 先取端口再释放锁，避免探测期间卡住 disconnect / stats
        let port = {
            let guard = self.child.lock().map_err(|e| e.to_string())?;
            if guard.is_none() {
                return Err("VPN 未连接".into());
            }
            self.proxy_port
                .lock()
                .map_err(|e| e.to_string())?
                .ok_or_else(|| "代理端口未知".to_string())?
        };
        let api_port = self.api_port.lock().ok().and_then(|g| *g);
        Ok(super::desktop_probe::probe_through_proxy(port, api_port))
    }

    /// 断网/网卡恢复时的轻量自愈：进程仍在则重刷系统代理（对齐 Android rebind underlying）。
    /// 禁止只判断 mihomo 存活就算恢复；调用方须再用 [probe] 验证用户路径。
    pub fn heal(&self) -> Result<(), String> {
        let guard = self.child.lock().map_err(|e| e.to_string())?;
        if guard.is_none() {
            return Err("VPN 未连接".into());
        }
        let mode = self
            .connection_mode
            .lock()
            .map(|g| *g)
            .unwrap_or(DesktopConnectionMode::Proxy);
        if mode != DesktopConnectionMode::Proxy {
            return Ok(());
        }
        let port = self
            .proxy_port
            .lock()
            .map_err(|e| e.to_string())?
            .ok_or_else(|| "代理端口未知".to_string())?;
        drop(guard);
        system_proxy::enable(port).map_err(|e| format!("重刷系统代理失败: {e}"))
    }

    pub fn sync_status<R: Runtime>(
        &self,
        app: &AppHandle<R>,
        state: &super::VpnState,
        kill_switch_enabled: bool,
    ) -> Result<VpnConnectionStatus, String> {
        let mut guard = self.child.lock().map_err(|e| e.to_string())?;
        if let Some(child) = guard.as_mut() {
            match child.try_wait() {
                Ok(Some(_)) => {
                    let detail = read_child_stderr(child);
                    *guard = None;
                    self.cleanup_runtime_artifacts(false);
                    if kill_switch_enabled {
                        let _ = self.engage_kill_switch_internal();
                    }
                    let status = VpnConnectionStatus {
                        state: VpnConnectionState::Failed,
                        error: Some(friendly_start_error(&format!(
                            "mihomo exited unexpectedly: {detail}"
                        ))),
                        node_name: None,
                        system_vpn_active: false,
                    };
                    state.update_status(app, status.clone());
                    return Ok(status);
                }
                Ok(None) => {}
                Err(e) => {
                    let status = VpnConnectionStatus {
                        state: VpnConnectionState::Failed,
                        error: Some(e.to_string()),
                        node_name: None,
                        system_vpn_active: false,
                    };
                    state.update_status(app, status.clone());
                    return Ok(status);
                }
            }
        }
        Ok(state.snapshot_status())
    }

    pub fn disconnect<R: Runtime>(
        &self,
        app: &AppHandle<R>,
        options: VpnDisconnectOptions,
    ) -> Result<(), DesktopVpnError> {
        let user_initiated = options.user_initiated;
        let kill_switch_enabled = options.kill_switch_enabled;

        if !user_initiated && kill_switch_enabled {
            self.engage_kill_switch_internal()
                .map_err(|e| DesktopVpnError::KillSwitch(e.to_string()))?;
        } else {
            let _ = self.release_kill_switch_internal();
        }

        let mut guard = self
            .child
            .lock()
            .map_err(|e| DesktopVpnError::StartFailed(e.to_string()))?;
        self.stop_inner(&mut guard, user_initiated)?;

        if let Some(state) = app.try_state::<super::VpnState>() {
            state.update_status(
                app,
                VpnConnectionStatus {
                    state: VpnConnectionState::Disconnected,
                    error: None,
                    node_name: None,
                    system_vpn_active: false,
                },
            );
        }
        let _ = app;
        Ok(())
    }

    fn engage_kill_switch_internal(&self) -> Result<(), super::desktop_mode::KillSwitchFacadeError> {
        engage_kill_switch()?;
        let _ = self.set_kill_switch_engaged(true);
        Ok(())
    }

    fn release_kill_switch_internal(&self) -> Result<(), super::desktop_mode::KillSwitchFacadeError> {
        release_kill_switch()?;
        let _ = self.set_kill_switch_engaged(false);
        Ok(())
    }

    fn set_kill_switch_engaged(&self, engaged: bool) -> Result<(), String> {
        let mut guard = self.kill_switch_engaged.lock().map_err(|e| e.to_string())?;
        *guard = engaged;
        Ok(())
    }

    fn stop_inner(&self, guard: &mut Option<Child>, _user_initiated: bool) -> Result<(), DesktopVpnError> {
        if let Some(mut child) = guard.take() {
            stop_child(&mut child);
        }
        self.cleanup_runtime_artifacts(true);
        Ok(())
    }

    fn cleanup_runtime_artifacts(&self, disable_proxy: bool) {
        if disable_proxy {
            let _ = system_proxy::disable();
        }
        if let Ok(mut port_guard) = self.proxy_port.lock() {
            if let Some(port) = port_guard.take() {
                release_local_proxy_port(port);
            }
        }
        if let Ok(mut api_guard) = self.api_port.lock() {
            *api_guard = None;
        }
        if let Ok(mut baseline) = self.traffic_baseline.lock() {
            *baseline = None;
        }
        if let Ok(mut last) = self.last_session_traffic.lock() {
            *last = None;
        }
        if let Ok(mut path_guard) = self.config_path.lock() {
            if let Some(path) = path_guard.take() {
                let _ = fs::remove_file(path);
            }
        }
    }
}

fn read_child_stderr(child: &mut Child) -> String {
    let mut buf = String::new();
    if let Some(mut stderr) = child.stderr.take() {
        let _ = stderr.read_to_string(&mut buf);
    }
    let trimmed = strip_ansi(&buf);
    if trimmed.is_empty() {
        format!("exit code {:?}", child.wait().ok().and_then(|s| s.code()))
    } else {
        trimmed
    }
}

fn strip_ansi(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    let mut chars = input.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch == '\u{1b}' {
            while let Some(&next) = chars.peek() {
                chars.next();
                if next.is_ascii_alphabetic() {
                    break;
                }
            }
            continue;
        }
        out.push(ch);
    }
    out.lines()
        .map(str::trim)
        .filter(|l| !l.is_empty())
        .collect::<Vec<_>>()
        .join("; ")
}

fn app_data_dir<R: Runtime>(app: &AppHandle<R>) -> PathBuf {
    app.path()
        .app_data_dir()
        .unwrap_or_else(|_| PathBuf::from("."))
}

fn write_runtime_config<R: Runtime>(
    app: &AppHandle<R>,
    config_yaml: &str,
) -> Result<PathBuf, DesktopVpnError> {
    let dir = app_data_dir(app).join("vpn");
    fs::create_dir_all(&dir).map_err(|e| DesktopVpnError::ConfigWrite(e.to_string()))?;
    let path = dir.join("runtime-config.yaml");
    let mut file =
        fs::File::create(&path).map_err(|e| DesktopVpnError::ConfigWrite(e.to_string()))?;
    file.write_all(config_yaml.as_bytes())
        .map_err(|e| DesktopVpnError::ConfigWrite(e.to_string()))?;
    Ok(path)
}

fn resolve_mihomo_binary<R: Runtime>(app: &AppHandle<R>) -> Result<PathBuf, DesktopVpnError> {
    let resource_dir = app
        .path()
        .resource_dir()
        .unwrap_or_else(|_| PathBuf::from("."));

    let candidates = mihomo_candidate_names()
        .into_iter()
        .map(|name| resource_dir.join("bin").join(name))
        .collect::<Vec<_>>();

    for path in candidates {
        if path.exists() {
            return Ok(path);
        }
    }

    if let Ok(path) = which::which("mihomo") {
        return Ok(path);
    }

    Err(DesktopVpnError::BinaryNotFound(
        "Mihomo 代理组件未安装，请重新安装客户端或执行 npm run fetch:mihomo".into(),
    ))
}

fn mihomo_candidate_names() -> Vec<String> {
    #[cfg(target_os = "windows")]
    {
        vec!["mihomo.exe".into()]
    }
    #[cfg(not(target_os = "windows"))]
    {
        vec!["mihomo".into()]
    }
}
