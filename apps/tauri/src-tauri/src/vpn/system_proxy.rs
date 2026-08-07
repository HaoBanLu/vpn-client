const PROXY_SERVER: &str = "127.0.0.1";

/// Windows / 通用：系统代理必须绕过本机，否则 WebView、Vite(dev)、Mihomo API 会经代理回环卡死。
pub fn merge_local_proxy_bypass(existing: Option<&str>) -> String {
    const REQUIRED: &[&str] = &[
        "localhost",
        "127.0.0.1",
        "127.*",
        "::1",
        "<local>",
    ];
    let mut parts: Vec<String> = existing
        .unwrap_or("")
        .split([';', ','])
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.to_string())
        .collect();
    for req in REQUIRED {
        let already = parts.iter().any(|p| p.eq_ignore_ascii_case(req));
        if !already {
            parts.push((*req).to_string());
        }
    }
    parts.join(";")
}

pub fn enable(local_port: u16) -> Result<(), String> {
  #[cfg(target_os = "windows")]
  {
    return windows::enable(local_port);
  }
  #[cfg(target_os = "macos")]
  {
    return macos::enable(local_port);
  }
  #[cfg(target_os = "linux")]
  {
    return linux::enable(local_port);
  }
  #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
  {
    let _ = local_port;
    Ok(())
  }
}

pub fn disable() -> Result<(), String> {
  #[cfg(target_os = "windows")]
  {
    return windows::disable();
  }
  #[cfg(target_os = "macos")]
  {
    return macos::disable();
  }
  #[cfg(target_os = "linux")]
  {
    return linux::disable();
  }
  #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
  {
    Ok(())
  }
}

#[cfg(target_os = "windows")]
mod windows {
  use super::{merge_local_proxy_bypass, PROXY_SERVER};
  use std::sync::Mutex;
  use winreg::enums::*;
  use winreg::RegKey;

  struct SavedProxy {
    enable: u32,
    server: Option<String>,
    override_list: Option<String>,
  }

  static SAVED: Mutex<Option<SavedProxy>> = Mutex::new(None);

  fn internet_settings() -> Result<RegKey, String> {
    RegKey::predef(HKEY_CURRENT_USER)
      .open_subkey_with_flags(
        "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
        KEY_SET_VALUE | KEY_QUERY_VALUE,
      )
      .map_err(|e| format!("open proxy registry: {e}"))
  }

  fn read_u32(settings: &RegKey, name: &str) -> Option<u32> {
    settings.get_value::<u32, _>(name).ok()
  }

  fn read_string(settings: &RegKey, name: &str) -> Option<String> {
    settings.get_value::<String, _>(name).ok()
  }

  pub fn enable(local_port: u16) -> Result<(), String> {
    let settings = internet_settings()?;
    if let Ok(mut guard) = SAVED.lock() {
      if guard.is_none() {
        *guard = Some(SavedProxy {
          enable: read_u32(&settings, "ProxyEnable").unwrap_or(0),
          server: read_string(&settings, "ProxyServer"),
          override_list: read_string(&settings, "ProxyOverride"),
        });
      }
    }

    let override_list = merge_local_proxy_bypass(read_string(&settings, "ProxyOverride").as_deref());
    settings
      .set_value("ProxyEnable", &1u32)
      .map_err(|e| format!("set ProxyEnable: {e}"))?;
    settings
      .set_value("ProxyServer", &format!("{PROXY_SERVER}:{local_port}"))
      .map_err(|e| format!("set ProxyServer: {e}"))?;
    settings
      .set_value("ProxyOverride", &override_list)
      .map_err(|e| format!("set ProxyOverride: {e}"))?;
    notify_proxy_changed();
    Ok(())
  }

  pub fn disable() -> Result<(), String> {
    let settings = internet_settings()?;
    let saved = SAVED.lock().ok().and_then(|mut g| g.take());
    if let Some(saved) = saved {
      settings
        .set_value("ProxyEnable", &saved.enable)
        .map_err(|e| format!("restore ProxyEnable: {e}"))?;
      if let Some(server) = saved.server {
        settings
          .set_value("ProxyServer", &server)
          .map_err(|e| format!("restore ProxyServer: {e}"))?;
      }
      if let Some(override_list) = saved.override_list {
        settings
          .set_value("ProxyOverride", &override_list)
          .map_err(|e| format!("restore ProxyOverride: {e}"))?;
      }
    } else {
      settings
        .set_value("ProxyEnable", &0u32)
        .map_err(|e| format!("clear ProxyEnable: {e}"))?;
    }
    notify_proxy_changed();
    Ok(())
  }

  fn notify_proxy_changed() {
    use windows_sys::Win32::Networking::WinInet::{
      InternetSetOptionW, INTERNET_OPTION_REFRESH, INTERNET_OPTION_SETTINGS_CHANGED,
    };
    unsafe {
      InternetSetOptionW(
        std::ptr::null(),
        INTERNET_OPTION_SETTINGS_CHANGED,
        std::ptr::null_mut(),
        0,
      );
      InternetSetOptionW(
        std::ptr::null(),
        INTERNET_OPTION_REFRESH,
        std::ptr::null_mut(),
        0,
      );
    }
  }
}

#[cfg(target_os = "macos")]
mod macos {
  use super::PROXY_SERVER;
  use std::process::Command;

  fn network_service() -> Result<String, String> {
    let output = Command::new("networksetup")
      .args(["-listallnetworkservices"])
      .output()
      .map_err(|e| format!("networksetup list: {e}"))?;
    let text = String::from_utf8_lossy(&output.stdout);
    let services = text
      .lines()
      .map(str::trim)
      .filter(|line| !line.starts_with('*') && !line.is_empty())
      .map(str::to_string)
      .collect::<Vec<_>>();

    for service in &services {
      if service_has_ip(service) {
        return Ok(service.clone());
      }
    }

    services
      .into_iter()
      .next()
      .ok_or_else(|| "no network service".into())
  }

  fn service_has_ip(service: &str) -> bool {
    Command::new("networksetup")
      .args(["-getinfo", service])
      .output()
      .ok()
      .map(|output| {
        let text = String::from_utf8_lossy(&output.stdout);
        text.lines().any(|line| {
          line.starts_with("IP address:")
            && !line.contains("IP address: none")
            && !line.trim_end().ends_with(':')
        })
      })
      .unwrap_or(false)
  }

  pub fn enable(local_port: u16) -> Result<(), String> {
    let services = network_services_with_ip()?;
    if services.is_empty() {
      return Err("no network service with IP".into());
    }
    let port = local_port.to_string();
    let mut last_err: Option<String> = None;
    let mut ok = 0usize;
    for service in &services {
      match enable_on_service(service, &port) {
        Ok(()) => ok += 1,
        Err(e) => last_err = Some(format!("{service}: {e}")),
      }
    }
    if ok == 0 {
      return Err(last_err.unwrap_or_else(|| "setwebproxy failed".into()));
    }
    Ok(())
  }

  fn enable_on_service(service: &str, port: &str) -> Result<(), String> {
    let status = Command::new("networksetup")
      .args(["-setwebproxy", service, PROXY_SERVER, port])
      .status()
      .map_err(|e| format!("setwebproxy: {e}"))?;
    if !status.success() {
      return Err("setwebproxy failed".into());
    }
    let status = Command::new("networksetup")
      .args(["-setsecurewebproxy", service, PROXY_SERVER, port])
      .status()
      .map_err(|e| format!("setsecurewebproxy: {e}"))?;
    if !status.success() {
      return Err("setsecurewebproxy failed".into());
    }
    // 对齐 Windows：绕过本机，避免 WebView / Mihomo API 回环
    let _ = Command::new("networksetup")
      .args([
        "-setproxybypassdomains",
        service,
        "localhost",
        "127.0.0.1",
        "127.0.0.0/8",
        "::1",
        "*.local",
      ])
      .status();
    let _ = Command::new("networksetup")
      .args(["-setwebproxystate", service, "on"])
      .status();
    let _ = Command::new("networksetup")
      .args(["-setsecurewebproxystate", service, "on"])
      .status();
    Ok(())
  }

  pub fn disable() -> Result<(), String> {
    let services = network_services_with_ip().unwrap_or_else(|_| {
      network_service().map(|s| vec![s]).unwrap_or_default()
    });
    for service in services {
      let _ = Command::new("networksetup")
        .args(["-setwebproxystate", &service, "off"])
        .status();
      let _ = Command::new("networksetup")
        .args(["-setsecurewebproxystate", &service, "off"])
        .status();
    }
    Ok(())
  }

  fn network_services_with_ip() -> Result<Vec<String>, String> {
    let output = Command::new("networksetup")
      .args(["-listallnetworkservices"])
      .output()
      .map_err(|e| format!("networksetup list: {e}"))?;
    let text = String::from_utf8_lossy(&output.stdout);
    let services = text
      .lines()
      .map(str::trim)
      .filter(|line| !line.starts_with('*') && !line.is_empty())
      .filter(|service| service_has_ip(service))
      .map(str::to_string)
      .collect::<Vec<_>>();
    Ok(services)
  }
}

#[cfg(target_os = "linux")]
mod linux {
  use super::PROXY_SERVER;
  use std::process::Command;

  pub fn enable(local_port: u16) -> Result<(), String> {
    if !has_gsettings() {
      return Err("Linux 系统代理暂仅支持 GNOME/gsettings，请手动设置 HTTP/HTTPS 代理为 127.0.0.1:端口或使用桌面环境代理设置".into());
    }
    let port = local_port.to_string();
    let host = format!("'{PROXY_SERVER}'");
    run_gsettings(&["set", "org.gnome.system.proxy", "mode", "'manual'"])?;
    run_gsettings(&["set", "org.gnome.system.proxy.http", "host", &host])?;
    run_gsettings(&["set", "org.gnome.system.proxy.http", "port", &port])?;
    run_gsettings(&["set", "org.gnome.system.proxy.https", "host", &host])?;
    run_gsettings(&["set", "org.gnome.system.proxy.https", "port", &port])?;
    // 绕过本机，避免桌面壳/API 经代理回环
    let _ = run_gsettings(&[
      "set",
      "org.gnome.system.proxy",
      "ignore-hosts",
      "['localhost','127.0.0.1','::1']",
    ]);
    Ok(())
  }

  pub fn disable() -> Result<(), String> {
    if has_gsettings() {
      let _ = run_gsettings(&["set", "org.gnome.system.proxy", "mode", "'none'"]);
    }
    Ok(())
  }

  fn has_gsettings() -> bool {
    Command::new("gsettings")
      .arg("--version")
      .status()
      .map(|status| status.success())
      .unwrap_or(false)
  }

  fn run_gsettings(args: &[&str]) -> Result<(), String> {
    let status = Command::new("gsettings")
      .args(args)
      .status()
      .map_err(|e| format!("gsettings: {e}"))?;
    if status.success() {
      Ok(())
    } else {
      Err("gsettings failed".into())
    }
  }
}

#[cfg(test)]
mod tests {
  use super::merge_local_proxy_bypass;

  #[test]
  fn merge_adds_required_localhost_bypass() {
    let merged = merge_local_proxy_bypass(None);
    assert!(merged.to_ascii_lowercase().contains("localhost"));
    assert!(merged.contains("127.0.0.1"));
    assert!(merged.contains("<local>"));
  }

  #[test]
  fn merge_keeps_existing_and_is_idempotent() {
    let first = merge_local_proxy_bypass(Some("example.com;10.*"));
    assert!(first.contains("example.com"));
    assert!(first.contains("10.*"));
    assert!(first.contains("localhost"));
    let second = merge_local_proxy_bypass(Some(&first));
    assert_eq!(
      first.split(';').count(),
      second.split(';').count(),
      "重复合并不应无限追加"
    );
  }
}
