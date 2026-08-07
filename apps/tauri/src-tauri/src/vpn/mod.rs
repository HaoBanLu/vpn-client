mod commands;
mod state;

#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
pub mod desktop;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod desktop_config;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod desktop_mode;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod kill_switch;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod desktop_process;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod desktop_probe;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod desktop_selector;
#[cfg(any(target_os = "windows", target_os = "macos", target_os = "linux"))]
mod desktop_traffic;
mod system_proxy;

#[cfg(mobile)]
pub mod mobile;

pub use commands::*;
pub use state::VpnState;
