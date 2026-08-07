use super::desktop_config::{DesktopConnectionMode, DesktopConfigError, patch_for_desktop};
use thiserror::Error;

#[derive(Error, Debug)]
pub enum KillSwitchFacadeError {
    #[error("{0}")]
    Inner(String),
}

impl From<super::kill_switch::KillSwitchError> for KillSwitchFacadeError {
    fn from(value: super::kill_switch::KillSwitchError) -> Self {
        KillSwitchFacadeError::Inner(value.to_string())
    }
}

pub fn engage_kill_switch() -> Result<(), KillSwitchFacadeError> {
    super::kill_switch::engage().map_err(Into::into)
}

pub fn release_kill_switch() -> Result<(), KillSwitchFacadeError> {
    super::kill_switch::release().map_err(Into::into)
}

pub fn parse_connection_mode(raw: Option<&str>) -> DesktopConnectionMode {
    match raw.unwrap_or("proxy").to_ascii_lowercase().as_str() {
        "tun" | "global" | "full" => DesktopConnectionMode::Tun,
        _ => DesktopConnectionMode::Proxy,
    }
}

pub fn patch_config(
    config_yaml: &str,
    mode: DesktopConnectionMode,
    mixed_port: u16,
    api_port: u16,
) -> Result<String, DesktopConfigError> {
    patch_for_desktop(config_yaml, mode, mixed_port, api_port)
}
