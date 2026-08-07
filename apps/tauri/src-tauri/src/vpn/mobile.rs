use tauri::{plugin::PluginHandle, Runtime};

#[cfg(mobile)]
pub struct MobileVpnHandle<R: Runtime>(pub PluginHandle<R>);
