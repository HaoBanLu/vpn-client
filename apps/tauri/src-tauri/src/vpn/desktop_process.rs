use super::desktop_config::{DEFAULT_DESKTOP_API_PORT, DEFAULT_DESKTOP_MIXED_PORT};
use std::net::TcpListener;
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::time::Duration;

#[cfg(windows)]
pub(crate) const CREATE_NO_WINDOW: u32 = 0x0800_0000;

pub fn spawn_mihomo(binary: &Path, config_path: &Path) -> std::io::Result<Child> {
    let mut command = Command::new(binary);
    command
        .arg("-f")
        .arg(config_path)
        .stdout(Stdio::null())
        .stderr(Stdio::piped());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(CREATE_NO_WINDOW);
    }

    command.spawn()
}

pub fn select_available_proxy_port() -> u16 {
    select_available_port(DEFAULT_DESKTOP_MIXED_PORT, 17891..=17950)
}

pub fn select_available_api_port() -> u16 {
    select_available_port(DEFAULT_DESKTOP_API_PORT, 17894..=17960)
}

fn select_available_port(default: u16, fallback_range: std::ops::RangeInclusive<u16>) -> u16 {
    if default == DEFAULT_DESKTOP_MIXED_PORT {
        release_local_proxy_port(default);
    }
    if is_port_available(default) {
        return default;
    }

    for port in fallback_range {
        if is_port_available(port) {
            return port;
        }
    }

    TcpListener::bind(("127.0.0.1", 0))
        .ok()
        .and_then(|listener| listener.local_addr().ok().map(|addr| addr.port()))
        .unwrap_or(default)
}

fn is_port_available(port: u16) -> bool {
    TcpListener::bind(("127.0.0.1", port)).is_ok()
}

pub fn stop_child(child: &mut Child) {
    let _ = child.kill();
    for _ in 0..30 {
        match child.try_wait() {
            Ok(Some(_)) => return,
            Ok(None) => std::thread::sleep(Duration::from_millis(100)),
            Err(_) => return,
        }
    }
    let _ = child.wait();
}

pub fn release_local_proxy_port(port: u16) {
    #[cfg(windows)]
    windows::kill_listeners_on_port(port);
    #[cfg(not(windows))]
    {
        let _ = port;
    }
}

pub fn cleanup_on_startup() {
    release_local_proxy_port(DEFAULT_DESKTOP_MIXED_PORT);
    let _ = super::system_proxy::disable();
}

pub fn friendly_start_error(raw: &str) -> String {
    if raw.contains("bind:") || raw.contains("address already in use") {
        return "本地代理端口被占用，请断开后重试；若仍失败请重启应用".into();
    }
    if raw.contains("mihomo exited early:") {
        return raw.replacen("mihomo exited early: ", "代理启动失败：", 1);
    }
    raw.to_string()
}

#[cfg(windows)]
mod windows {
    use super::CREATE_NO_WINDOW;
    use std::os::windows::process::CommandExt;
    use std::process::Command;
    use std::time::Duration;

    pub fn kill_listeners_on_port(port: u16) {
        let needle = format!(":{port}");
        let output = match Command::new("netstat")
            .args(["-ano", "-p", "tcp"])
            .creation_flags(CREATE_NO_WINDOW)
            .output()
        {
            Ok(output) => output,
            Err(_) => return,
        };

        let text = String::from_utf8_lossy(&output.stdout);
        let mut pids = Vec::new();
        for line in text.lines() {
            if !line.contains("LISTENING") || !line.contains(&needle) {
                continue;
            }
            let parts: Vec<_> = line.split_whitespace().collect();
            if let Some(pid) = parts.last().and_then(|p| p.parse::<u32>().ok()) {
                if pid > 0 {
                    pids.push(pid);
                }
            }
        }

        pids.sort_unstable();
        pids.dedup();
        let stale_pids: Vec<_> = pids
            .into_iter()
            .filter(|pid| is_mihomo_process(*pid))
            .collect();
        let had_listeners = !stale_pids.is_empty();
        for pid in stale_pids {
            let _ = Command::new("taskkill")
                .args(["/F", "/PID", &pid.to_string()])
                .creation_flags(CREATE_NO_WINDOW)
                .output();
        }
        if had_listeners {
            std::thread::sleep(Duration::from_millis(200));
        }
    }

    fn is_mihomo_process(pid: u32) -> bool {
        let filter = format!("PID eq {pid}");
        let output = match Command::new("tasklist")
            .args(["/FI", &filter, "/FO", "CSV", "/NH"])
            .creation_flags(CREATE_NO_WINDOW)
            .output()
        {
            Ok(output) => output,
            Err(_) => return false,
        };
        String::from_utf8_lossy(&output.stdout)
            .to_ascii_lowercase()
            .contains("mihomo.exe")
    }
}
