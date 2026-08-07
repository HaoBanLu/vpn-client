use std::net::IpAddr;

/// 检测本机是否存在活跃的非回环 IPv6 地址（泄露自检用）。
#[tauri::command]
pub fn privacy_detect_local_ipv6() -> bool {
    detect_local_ipv6()
}

pub fn detect_local_ipv6() -> bool {
    if_addrs::get_if_addrs()
        .map(|ifaces| {
            ifaces.into_iter().any(|iface| {
                if iface.name.eq_ignore_ascii_case("lo") || iface.name.to_lowercase().contains("loopback")
                {
                    return false;
                }
                match iface.addr.ip() {
                    IpAddr::V6(v6) => {
                        !v6.is_loopback()
                            && !v6.is_unspecified()
                            && !v6.is_multicast()
                            && !v6.is_unicast_link_local()
                    }
                    IpAddr::V4(_) => false,
                }
            })
        })
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detect_local_ipv6_does_not_panic() {
        let _ = detect_local_ipv6();
    }
}
