use thiserror::Error;

pub const DEFAULT_DESKTOP_MIXED_PORT: u16 = 17890;
const RULE_BLOCK: &str = "KuayunVPN_KillSwitch_Block";

#[derive(Error, Debug)]
pub enum KillSwitchError {
    #[error("Kill Switch 需要管理员权限：{0}")]
    Permission(String),
    #[error("Kill Switch 操作失败：{0}")]
    Command(String),
}

/// 启用 Kill Switch：阻断出站流量（Windows 防火墙 / macOS pf）。
pub fn engage() -> Result<(), KillSwitchError> {
    #[cfg(target_os = "windows")]
    {
        return windows::engage();
    }
    #[cfg(target_os = "macos")]
    {
        return macos::engage();
    }
    #[cfg(target_os = "linux")]
    {
        return linux::engage();
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        Err(KillSwitchError::Command(
            "当前平台 Kill Switch 尚未实现，仅 Windows / macOS / Linux 可用".into(),
        ))
    }
}

/// 释放 Kill Switch 规则。
pub fn release() -> Result<(), KillSwitchError> {
    #[cfg(target_os = "windows")]
    {
        return windows::release();
    }
    #[cfg(target_os = "macos")]
    {
        return macos::release();
    }
    #[cfg(target_os = "linux")]
    {
        return linux::release();
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        Ok(())
    }
}

pub fn is_engaged() -> bool {
    #[cfg(target_os = "windows")]
    {
        return windows::is_engaged();
    }
    #[cfg(target_os = "macos")]
    {
        return macos::is_engaged();
    }
    #[cfg(target_os = "linux")]
    {
        return linux::is_engaged();
    }
    #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
    {
        false
    }
}

#[cfg(target_os = "windows")]
mod windows {
    use super::{KillSwitchError, RULE_BLOCK};
    use crate::vpn::desktop_process::CREATE_NO_WINDOW;
    use std::os::windows::process::CommandExt;
    use std::process::Command;

    fn netsh_command() -> Command {
        let mut command = Command::new("netsh");
        command.creation_flags(CREATE_NO_WINDOW);
        command
    }

    fn run_netsh(args: &[&str]) -> Result<(), KillSwitchError> {
        let output = netsh_command()
            .args(args)
            .output()
            .map_err(|e| KillSwitchError::Command(e.to_string()))?;
        if output.status.success() {
            Ok(())
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stdout = String::from_utf8_lossy(&output.stdout);
            let detail = format!("{stderr}{stdout}").trim().to_string();
            if detail.contains("拒绝访问") || detail.to_lowercase().contains("access is denied") {
                return Err(KillSwitchError::Permission(detail));
            }
            Err(KillSwitchError::Command(if detail.is_empty() {
                "netsh failed".into()
            } else {
                detail
            }))
        }
    }

    pub fn engage() -> Result<(), KillSwitchError> {
        let _ = release();
        run_netsh(&[
            "advfirewall",
            "firewall",
            "add",
            "rule",
            &format!("name={RULE_BLOCK}"),
            "dir=out",
            "action=block",
            "enable=yes",
            "profile=any",
        ])
    }

    pub fn release() -> Result<(), KillSwitchError> {
        let _ = run_netsh(&[
            "advfirewall",
            "firewall",
            "delete",
            "rule",
            &format!("name={RULE_BLOCK}"),
        ]);
        Ok(())
    }

    pub fn is_engaged() -> bool {
        netsh_command()
            .args([
                "advfirewall",
                "firewall",
                "show",
                "rule",
                &format!("name={RULE_BLOCK}"),
            ])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
    }
}

#[cfg(target_os = "macos")]
mod macos {
    use super::KillSwitchError;
    use std::fs;
    use std::process::Command;

    const MARKER_PATH: &str = "/tmp/kuayun_killswitch.active";
    const RULES_PATH: &str = "/tmp/kuayun_killswitch.pf.conf";

    fn rules_content() -> &'static str {
        r#"# KuayunVPN Kill Switch
block drop out all
pass on lo0
"#
    }

    fn run_pfctl(args: &[&str]) -> Result<(), KillSwitchError> {
        let output = Command::new("/sbin/pfctl")
            .args(args)
            .output()
            .map_err(|e| KillSwitchError::Command(e.to_string()))?;
        parse_pfctl_output(output)
    }

    fn parse_pfctl_output(output: std::process::Output) -> Result<(), KillSwitchError> {
        if output.status.success() {
            Ok(())
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stdout = String::from_utf8_lossy(&output.stdout);
            let detail = format!("{stderr}{stdout}").trim().to_string();
            let lower = detail.to_lowercase();
            if lower.contains("permission denied")
                || lower.contains("must be root")
                || lower.contains("not authorized")
            {
                return Err(KillSwitchError::Permission(if detail.is_empty() {
                    "需要管理员权限执行 pfctl".into()
                } else {
                    detail
                }));
            }
            Err(KillSwitchError::Command(if detail.is_empty() {
                "pfctl failed".into()
            } else {
                detail
            }))
        }
    }

    /// 无 root 时通过 AppleScript 弹出 macOS 授权对话框执行 pfctl。
    fn run_pfctl_elevated(shell_cmd: &str) -> Result<(), KillSwitchError> {
        let escaped = shell_cmd.replace('\\', "\\\\").replace('"', "\\\"");
        let script = format!("do shell script \"{escaped}\" with administrator privileges");
        let output = Command::new("/usr/bin/osascript")
            .args(["-e", &script])
            .output()
            .map_err(|e| KillSwitchError::Command(e.to_string()))?;
        if output.status.success() {
            Ok(())
        } else {
            let detail = String::from_utf8_lossy(&output.stderr)
                .trim()
                .to_string();
            if detail.contains("User canceled") || detail.contains("用户取消") {
                return Err(KillSwitchError::Permission(
                    "已取消管理员授权，Kill Switch 未启用".into(),
                ));
            }
            Err(KillSwitchError::Command(if detail.is_empty() {
                "osascript pfctl failed".into()
            } else {
                detail
            }))
        }
    }

    fn engage_pf_rules() -> Result<(), KillSwitchError> {
        match run_pfctl(&["-f", RULES_PATH, "-e"]) {
            Ok(()) => Ok(()),
            Err(KillSwitchError::Permission(_)) => {
                run_pfctl_elevated(&format!("/sbin/pfctl -f {RULES_PATH} -e"))
            }
            Err(err) => Err(err),
        }
    }

    fn release_pf_rules() -> Result<(), KillSwitchError> {
        match run_pfctl(&["-f", "/etc/pf.conf"]) {
            Ok(()) => Ok(()),
            Err(KillSwitchError::Permission(_)) => {
                run_pfctl_elevated("/sbin/pfctl -f /etc/pf.conf")
            }
            Err(err) => Err(err),
        }
    }

    pub fn engage() -> Result<(), KillSwitchError> {
        let _ = release();
        fs::write(RULES_PATH, rules_content())
            .map_err(|e| KillSwitchError::Command(format!("写入 pf 规则失败: {e}")))?;
        engage_pf_rules()?;
        let _ = fs::write(MARKER_PATH, "1");
        Ok(())
    }

    pub fn release() -> Result<(), KillSwitchError> {
        let _ = release_pf_rules();
        let _ = fs::remove_file(MARKER_PATH);
        Ok(())
    }

    pub fn is_engaged() -> bool {
        if !std::path::Path::new(MARKER_PATH).exists() {
            return false;
        }
        Command::new("/sbin/pfctl")
            .args(["-sr"])
            .output()
            .map(|o| {
                let rules = String::from_utf8_lossy(&o.stdout);
                rules.contains("block drop out all")
            })
            .unwrap_or(true)
    }
}

#[cfg(target_os = "linux")]
mod linux {
    use super::KillSwitchError;
    use std::fs;
    use std::process::Command;

    const MARKER_PATH: &str = "/tmp/kuayun_killswitch.active";
    const CHAIN: &str = "KuayunVPN_KS";

    fn run_iptables(args: &[&str]) -> Result<(), KillSwitchError> {
        let output = Command::new("iptables")
            .args(args)
            .output()
            .map_err(|e| KillSwitchError::Command(e.to_string()))?;
        if output.status.success() {
            Ok(())
        } else {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stdout = String::from_utf8_lossy(&output.stdout);
            let detail = format!("{stderr}{stdout}").trim().to_string();
            let lower = detail.to_lowercase();
            if lower.contains("permission denied") || lower.contains("must be root") {
                return Err(KillSwitchError::Permission(if detail.is_empty() {
                    "需要 root 权限执行 iptables".into()
                } else {
                    detail
                }));
            }
            Err(KillSwitchError::Command(if detail.is_empty() {
                "iptables failed".into()
            } else {
                detail
            }))
        }
    }

    pub fn engage() -> Result<(), KillSwitchError> {
        let _ = release();
        let _ = run_iptables(&["-N", CHAIN]);
        run_iptables(&["-F", CHAIN])?;
        run_iptables(&["-A", CHAIN, "-o", "lo", "-j", "ACCEPT"])?;
        run_iptables(&["-A", CHAIN, "-j", "DROP"])?;
        run_iptables(&["-I", "OUTPUT", "1", "-j", CHAIN])?;
        let _ = fs::write(MARKER_PATH, "1");
        Ok(())
    }

    pub fn release() -> Result<(), KillSwitchError> {
        let _ = run_iptables(&["-D", "OUTPUT", "-j", CHAIN]);
        let _ = run_iptables(&["-F", CHAIN]);
        let _ = run_iptables(&["-X", CHAIN]);
        let _ = fs::remove_file(MARKER_PATH);
        Ok(())
    }

    pub fn is_engaged() -> bool {
        if !std::path::Path::new(MARKER_PATH).exists() {
            return false;
        }
        Command::new("iptables")
            .args(["-L", CHAIN, "-n"])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn release_is_idempotent_on_unsupported_platforms() {
        #[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
        assert!(release().is_ok());
    }
}
