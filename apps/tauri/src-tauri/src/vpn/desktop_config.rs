use thiserror::Error;

pub const DEFAULT_DESKTOP_MIXED_PORT: u16 = 17890;
pub const DEFAULT_DESKTOP_API_PORT: u16 = 17893;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DesktopConnectionMode {
    /// 系统 HTTP/HTTPS 代理 + Mihomo mixed-port
    Proxy,
    /// Mihomo TUN 全隧道（Windows 需 wintun，可能需要管理员）
    Tun,
}

#[derive(Error, Debug)]
pub enum DesktopConfigError {
    #[error("invalid config: {0}")]
    Invalid(String),
}

/// 将 /client/config 返回的 Clash YAML 转为桌面 Mihomo 可运行配置。
pub fn patch_for_desktop(
    config_yaml: &str,
    mode: DesktopConnectionMode,
    mixed_port: u16,
    api_port: u16,
) -> Result<String, DesktopConfigError> {
    match mode {
        DesktopConnectionMode::Proxy => patch_for_proxy(config_yaml, mixed_port, api_port),
        DesktopConnectionMode::Tun => patch_for_tun(config_yaml, mixed_port, api_port),
    }
}

fn patch_for_proxy(config_yaml: &str, mixed_port: u16, api_port: u16) -> Result<String, DesktopConfigError> {
    let trimmed = config_yaml.trim();
    if trimmed.is_empty() {
        return Err(DesktopConfigError::Invalid("empty config".into()));
    }

    let mut lines: Vec<String> = trimmed.lines().map(String::from).collect();
    lines = remove_yaml_section(&lines, "tun");
    upsert_mixed_port(&mut lines, mixed_port);
    upsert_external_controller(&mut lines, api_port);
    // 桌面本机 API 不鉴权，避免 /connections 被 secret 挡住导致上下行恒为 0
    upsert_yaml_str(&mut lines, "secret", "");
    upsert_yaml_bool(&mut lines, "allow-lan", false);
    upsert_yaml_str(&mut lines, "bind-address", "127.0.0.1");

    Ok(format!("{}\n", lines.join("\n")))
}

fn patch_for_tun(config_yaml: &str, mixed_port: u16, api_port: u16) -> Result<String, DesktopConfigError> {
    let trimmed = config_yaml.trim();
    if trimmed.is_empty() {
        return Err(DesktopConfigError::Invalid("empty config".into()));
    }

    let mut lines: Vec<String> = trimmed.lines().map(String::from).collect();
    upsert_mixed_port(&mut lines, mixed_port);
    upsert_external_controller(&mut lines, api_port);
    upsert_yaml_str(&mut lines, "secret", "");
    upsert_yaml_bool(&mut lines, "allow-lan", false);

    if has_yaml_section(&lines, "tun") {
        lines = upsert_tun_section(&lines);
    } else {
        let tun_block = build_tun_section();
        lines.insert(0, tun_block);
    }

    Ok(format!("{}\n", lines.join("\n")))
}

fn build_tun_section() -> String {
    let stack = if cfg!(target_os = "windows") {
        "gvisor"
    } else {
        "system"
    };
    format!(
        "tun:\n  enable: true\n  stack: {stack}\n  auto-route: true\n  strict-route: true\n  dns-hijack:\n    - any:53\n"
    )
}

fn upsert_tun_section(lines: &[String]) -> Vec<String> {
    let mut out = Vec::new();
    let mut skipping = false;
    let mut skip_indent: Option<usize> = None;
    let mut inserted = false;

    for line in lines {
        if !skipping {
            if line_key(line) == Some("tun") {
                if !inserted {
                    out.push("tun:".into());
                    out.push("  enable: true".into());
                    let stack = if cfg!(target_os = "windows") {
                        "gvisor"
                    } else {
                        "system"
                    };
                    out.push(format!("  stack: {stack}"));
                    out.push("  auto-route: true".into());
                    out.push("  strict-route: true".into());
                    out.push("  dns-hijack:".into());
                    out.push("    - any:53".into());
                    inserted = true;
                }
                skipping = true;
                skip_indent = Some(leading_spaces(line));
                continue;
            }
            out.push(line.clone());
            continue;
        }

        if line.trim().is_empty() {
            continue;
        }
        let indent = leading_spaces(line);
        if indent <= skip_indent.unwrap_or(0) && line_key(line).is_some() {
            skipping = false;
            skip_indent = None;
            out.push(line.clone());
        }
    }

    if !inserted {
        out.insert(0, build_tun_section());
    }
    out
}

fn has_yaml_section(lines: &[String], section: &str) -> bool {
    lines.iter().any(|l| line_key(l) == Some(section))
}

fn upsert_mixed_port(lines: &mut Vec<String>, mixed_port: u16) {
    let mixed_line = format!("mixed-port: {mixed_port}");
    if let Some(idx) = lines.iter().position(|l| line_key(l) == Some("mixed-port")) {
        lines[idx] = mixed_line;
    } else {
        lines.insert(0, mixed_line);
    }
}

fn upsert_external_controller(lines: &mut Vec<String>, api_port: u16) {
    let line = format!("external-controller: 127.0.0.1:{api_port}");
    if let Some(idx) = lines.iter().position(|l| line_key(l) == Some("external-controller")) {
        lines[idx] = line;
    } else {
        lines.insert(1, line);
    }
}

fn line_key(line: &str) -> Option<&str> {
    let trimmed = line.trim();
    if trimmed.is_empty() || trimmed.starts_with('#') {
        return None;
    }
    trimmed.split(':').next().map(str::trim)
}

fn remove_yaml_section(lines: &[String], section: &str) -> Vec<String> {
    let mut out = Vec::with_capacity(lines.len());
    let mut skipping = false;
    let mut skip_indent: Option<usize> = None;

    for line in lines {
        let trimmed = line.trim();
        if !skipping {
            if line_key(line) == Some(section) {
                skipping = true;
                skip_indent = Some(leading_spaces(line));
                continue;
            }
            out.push(line.clone());
            continue;
        }

        if trimmed.is_empty() {
            continue;
        }

        let indent = leading_spaces(line);
        if indent <= skip_indent.unwrap_or(0) && line_key(line).is_some() {
            skipping = false;
            skip_indent = None;
            out.push(line.clone());
        }
    }

    out
}

fn leading_spaces(line: &str) -> usize {
    line.len() - line.trim_start().len()
}

fn upsert_yaml_bool(lines: &mut Vec<String>, key: &str, value: bool) {
    let line = format!("{key}: {value}");
    if let Some(idx) = lines.iter().position(|l| line_key(l) == Some(key)) {
        lines[idx] = line;
    } else {
        lines.insert(1, line);
    }
}

fn upsert_yaml_str(lines: &mut Vec<String>, key: &str, value: &str) {
    let line = format!("{key}: \"{value}\"");
    if let Some(idx) = lines.iter().position(|l| line_key(l) == Some(key)) {
        lines[idx] = line;
    } else {
        lines.insert(2, line);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn proxy_mode_removes_tun() {
        let raw = r#"mixed-port: 7890
tun:
  enable: true
dns:
  enable: true
"#;
        let patched = patch_for_desktop(raw, DesktopConnectionMode::Proxy, DEFAULT_DESKTOP_MIXED_PORT, DEFAULT_DESKTOP_API_PORT)
            .expect("patch");
        assert!(!patched.contains("tun:"));
        assert!(patched.contains("bind-address"));
        assert!(patched.contains("external-controller"));
        assert!(patched.contains("secret: \"\""));
    }

    #[test]
    fn tun_mode_keeps_or_inserts_tun() {
        let raw = "mode: rule\nproxies: []\n";
        let patched = patch_for_desktop(
            raw,
            DesktopConnectionMode::Tun,
            DEFAULT_DESKTOP_MIXED_PORT,
            DEFAULT_DESKTOP_API_PORT,
        )
        .expect("patch");
        assert!(patched.contains("tun:"));
        assert!(patched.contains("enable: true"));
        assert!(patched.contains("auto-route: true"));
    }
}
