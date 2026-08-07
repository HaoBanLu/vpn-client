use super::state::VpnProbeResult;
use std::process::{Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

const DEFAULT_PROBE_TIMEOUT_SEC: u64 = 5;
const STAGE_GAP_MS: u64 = 200;
const SETTLE_MS: u64 = 400;
const PROBE_ATTEMPTS: u32 = 2;
const PROBE_RETRY_DELAY_MS: u64 = 600;
/// 整次探测硬上限，避免 UI「无响应」感（此前 curl+PowerShell×多 URL×3 次可达数分钟）。
const TOTAL_BUDGET: Duration = Duration::from_secs(12);

const BASIC_URLS: &[&str] = &["https://www.baidu.com", "https://www.qq.com"];
const OVERSEAS_URLS: &[&str] = &[
    "https://www.gstatic.com/generate_204",
    "https://cp.cloudflare.com/generate_204",
];

/// 经 Mihomo mixed-port 做健康探测，对齐 Android MihomoLocalProbe（HTTP HEAD + 重试）。
pub fn probe_through_proxy(proxy_port: u16, api_port: Option<u16>) -> VpnProbeResult {
    thread::sleep(Duration::from_millis(SETTLE_MS));
    let started = Instant::now();
    let stage_timeout = DEFAULT_PROBE_TIMEOUT_SEC;
    let basic_ok = probe_any_with_retry(proxy_port, BASIC_URLS, stage_timeout, started);
    if !basic_ok {
        // Mihomo API 仍存活时，再试海外探测，减少偶发抖动导致的误判。
        if remaining_budget(started).is_some() && api_port.map(mihomo_api_alive).unwrap_or(false) {
            let overseas_ok = probe_any_with_retry(proxy_port, OVERSEAS_URLS, stage_timeout, started);
            if overseas_ok {
                return VpnProbeResult {
                    basic_ok: true,
                    overseas_ok: true,
                    slow: false,
                    latency_ms: Some(started.elapsed().as_millis() as u64),
                };
            }
        }
        return VpnProbeResult {
            basic_ok: false,
            overseas_ok: false,
            slow: false,
            latency_ms: None,
        };
    }
    if remaining_budget(started).is_none() {
        return VpnProbeResult {
            basic_ok: true,
            overseas_ok: false,
            slow: true,
            latency_ms: Some(started.elapsed().as_millis() as u64),
        };
    }
    thread::sleep(Duration::from_millis(STAGE_GAP_MS));
    let overseas_ok = probe_any_with_retry(proxy_port, OVERSEAS_URLS, stage_timeout, started);
    VpnProbeResult {
        basic_ok: true,
        overseas_ok,
        slow: !overseas_ok,
        latency_ms: Some(started.elapsed().as_millis() as u64),
    }
}

fn remaining_budget(started: Instant) -> Option<Duration> {
    TOTAL_BUDGET.checked_sub(started.elapsed())
}

fn probe_any_with_retry(port: u16, urls: &[&str], timeout_sec: u64, started: Instant) -> bool {
    for attempt in 0..PROBE_ATTEMPTS {
        if remaining_budget(started).is_none() {
            return false;
        }
        if probe_any(port, urls, timeout_sec) {
            return true;
        }
        if attempt + 1 < PROBE_ATTEMPTS {
            if remaining_budget(started).is_none() {
                return false;
            }
            thread::sleep(Duration::from_millis(PROBE_RETRY_DELAY_MS));
        }
    }
    false
}

fn probe_any(port: u16, urls: &[&str], timeout_sec: u64) -> bool {
    for url in urls {
        if probe_url(port, url, timeout_sec) {
            return true;
        }
    }
    false
}

fn mihomo_api_alive(api_port: u16) -> bool {
    let url = format!("http://127.0.0.1:{api_port}/version");
    probe_url_direct(&url, 2)
}

fn is_success_http_code(code: &str) -> bool {
    matches!(code.trim(), "200" | "204" | "301" | "302" | "307" | "308")
}

#[cfg(windows)]
fn probe_url(port: u16, url: &str, timeout_sec: u64) -> bool {
    use super::desktop_process::CREATE_NO_WINDOW;

    let proxy = format!("http://127.0.0.1:{port}");
    // Win10+ 自带 curl：优先只用 curl，避免 PowerShell 刷屏且拖到数十秒。
    if curl_head_through_proxy(&proxy, url, timeout_sec, CREATE_NO_WINDOW) {
        return true;
    }
    // curl 不可用时才退回 PowerShell（静默 stderr）
    if !curl_available(CREATE_NO_WINDOW) {
        return powershell_head_through_proxy(&proxy, url, timeout_sec.min(4), CREATE_NO_WINDOW);
    }
    false
}

#[cfg(windows)]
fn curl_available(creation_flags: u32) -> bool {
    use std::os::windows::process::CommandExt;
    Command::new("curl.exe")
        .args(["--version"])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .creation_flags(creation_flags)
        .status()
        .map(|s| s.success())
        .unwrap_or(false)
}

#[cfg(windows)]
fn probe_url_direct(url: &str, timeout_sec: u64) -> bool {
    use super::desktop_process::CREATE_NO_WINDOW;
    use std::os::windows::process::CommandExt;

    Command::new("curl.exe")
        .args([
            "-sS",
            "-o",
            "NUL",
            "-w",
            "%{http_code}",
            "--max-time",
            &timeout_sec.to_string(),
            url,
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .map(|output| {
            let code = String::from_utf8_lossy(&output.stdout);
            output.status.success() && is_success_http_code(&code)
        })
        .unwrap_or(false)
}

#[cfg(windows)]
fn curl_head_through_proxy(
    proxy: &str,
    url: &str,
    timeout_sec: u64,
    creation_flags: u32,
) -> bool {
    use std::os::windows::process::CommandExt;

    Command::new("curl.exe")
        .args([
            "-sS",
            "-o",
            "NUL",
            "-w",
            "%{http_code}",
            "-I",
            "-L",
            "--max-time",
            &timeout_sec.to_string(),
            "-x",
            proxy,
            url,
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .creation_flags(creation_flags)
        .output()
        .map(|output| {
            let code = String::from_utf8_lossy(&output.stdout);
            output.status.success() && is_success_http_code(&code)
        })
        .unwrap_or(false)
}

#[cfg(windows)]
fn powershell_head_through_proxy(
    proxy: &str,
    url: &str,
    timeout_sec: u64,
    creation_flags: u32,
) -> bool {
    use std::os::windows::process::CommandExt;

    let script = format!(
        r#"$ErrorActionPreference='SilentlyContinue';try {{ $r=Invoke-WebRequest -Uri '{url}' -Proxy '{proxy}' -UseBasicParsing -TimeoutSec {timeout_sec} -Method Head -MaximumRedirection 5; if (($r.StatusCode -ge 200 -and $r.StatusCode -lt 400) -or $r.StatusCode -eq 204) {{ exit 0 }} }} catch {{ }}; exit 1"#
    );
    Command::new("powershell")
        .args(["-NoProfile", "-NonInteractive", "-Command", &script])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .creation_flags(creation_flags)
        .status()
        .map(|status| status.success())
        .unwrap_or(false)
}

#[cfg(not(windows))]
fn probe_url(port: u16, url: &str, timeout_sec: u64) -> bool {
    if let Ok(output) = Command::new("curl")
        .args([
            "-sS",
            "-o",
            "/dev/null",
            "-w",
            "%{http_code}",
            "-I",
            "-L",
            "--max-time",
            &timeout_sec.to_string(),
            "-x",
            &format!("http://127.0.0.1:{port}"),
            url,
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .output()
    {
        let code = String::from_utf8_lossy(&output.stdout);
        return output.status.success() && is_success_http_code(&code);
    }
    false
}

#[cfg(not(windows))]
fn probe_url_direct(url: &str, timeout_sec: u64) -> bool {
    Command::new("curl")
        .args([
            "-sS",
            "-o",
            "/dev/null",
            "-w",
            "%{http_code}",
            "--max-time",
            &timeout_sec.to_string(),
            url,
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .output()
        .map(|output| {
            let code = String::from_utf8_lossy(&output.stdout);
            output.status.success() && is_success_http_code(&code)
        })
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::TOTAL_BUDGET;
    use std::time::Duration;

    #[test]
    fn probe_budget_is_bounded() {
        assert!(TOTAL_BUDGET <= Duration::from_secs(15));
        assert!(TOTAL_BUDGET >= Duration::from_secs(8));
    }
}
