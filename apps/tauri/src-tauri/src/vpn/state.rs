use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use tauri::{AppHandle, Emitter, Runtime};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum VpnConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Failed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnConnectionStatus {
    pub state: VpnConnectionState,
    pub error: Option<String>,
    pub node_name: Option<String>,
}

impl Default for VpnConnectionStatus {
    fn default() -> Self {
        Self {
            state: VpnConnectionState::Disconnected,
            error: None,
            node_name: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnSessionStats {
    pub upload_bytes: u64,
    pub download_bytes: u64,
    pub duration_ms: u64,
    /// Mihomo `/traffic` 瞬时上传 bytes/s；无数据时为 0。
    #[serde(default)]
    pub upload_bps: u64,
    /// Mihomo `/traffic` 瞬时下载 bytes/s；无数据时为 0。
    #[serde(default)]
    pub download_bps: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnConnectOptions {
    pub config_json: String,
    pub node_name: Option<String>,
    /// 桌面端：`proxy`（系统代理）或 `tun`（全隧道）
    #[serde(default)]
    pub connection_mode: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnDisconnectOptions {
    #[serde(default = "default_true")]
    pub user_initiated: bool,
    #[serde(default)]
    pub kill_switch_enabled: bool,
}

fn default_true() -> bool {
    true
}

impl Default for VpnDisconnectOptions {
    fn default() -> Self {
        Self {
            user_initiated: true,
            kill_switch_enabled: false,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnProbeResult {
    pub basic_ok: bool,
    pub overseas_ok: bool,
    #[serde(default)]
    pub slow: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub latency_ms: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VpnPlatformInfo {
    pub platform: String,
    pub vpn_supported: bool,
    pub implementation: String,
    pub notes: Option<String>,
}

pub struct VpnState {
    inner: Mutex<VpnConnectionStatus>,
    stats: Mutex<VpnSessionStats>,
    connected_at_ms: Mutex<Option<u128>>,
}

impl Default for VpnState {
    fn default() -> Self {
        Self {
            inner: Mutex::new(VpnConnectionStatus::default()),
            stats: Mutex::new(VpnSessionStats {
                upload_bytes: 0,
                download_bytes: 0,
                duration_ms: 0,
                upload_bps: 0,
                download_bps: 0,
            }),
            connected_at_ms: Mutex::new(None),
        }
    }
}

impl VpnState {
    pub fn update_status<R: Runtime>(&self, app: &AppHandle<R>, status: VpnConnectionStatus) {
        let prev = self
            .inner
            .lock()
            .map(|g| g.state.clone())
            .unwrap_or(VpnConnectionState::Disconnected);
        if let Ok(mut guard) = self.inner.lock() {
            *guard = status.clone();
        }
        // 仅在「进入 Connected」时记起点；Android 每秒 sync 会反复推 Connected，不可重置。
        match status.state {
            VpnConnectionState::Connected => {
                if prev != VpnConnectionState::Connected {
                    if let Ok(mut at) = self.connected_at_ms.lock() {
                        *at = Some(
                            std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .map(|d| d.as_millis())
                                .unwrap_or(0),
                        );
                    }
                }
            }
            VpnConnectionState::Connecting => {
                // 已连接后切节点/重连：清空会话计时，等再次 Connected
                if prev == VpnConnectionState::Connected {
                    if let Ok(mut at) = self.connected_at_ms.lock() {
                        *at = None;
                    }
                }
            }
            VpnConnectionState::Disconnected | VpnConnectionState::Failed => {
                if let Ok(mut at) = self.connected_at_ms.lock() {
                    *at = None;
                }
            }
        }
        let _ = app.emit("vpn://status", status);
    }

    pub fn snapshot_status(&self) -> VpnConnectionStatus {
        self.inner.lock().map(|g| g.clone()).unwrap_or_default()
    }

    pub fn snapshot_stats(&self) -> VpnSessionStats {
        let duration_ms = self
            .connected_at_ms
            .lock()
            .ok()
            .and_then(|at| *at)
            .map(|start| {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_millis())
                    .unwrap_or(0);
                now.saturating_sub(start) as u64
            })
            .unwrap_or(0);
        let mut stats = self.stats.lock().map(|g| g.clone()).unwrap_or(VpnSessionStats {
            upload_bytes: 0,
            download_bytes: 0,
            duration_ms: 0,
            upload_bps: 0,
            download_bps: 0,
        });
        stats.duration_ms = duration_ms;
        stats
    }

}
