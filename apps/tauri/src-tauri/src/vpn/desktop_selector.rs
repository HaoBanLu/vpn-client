//! 对齐 Android ClashSelectorPatcher：连接后把用户选中节点写入 Mihomo selector。

use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::Duration;

const GROUP_PROXY: &str = "Proxy";
const GROUP_MANUAL: &str = "手动选择";
const GROUP_RELAY: &str = "回国专线";
const GROUP_GLOBAL: &str = "GLOBAL";
const GROUP_DIRECT_POOL: &str = "海外直连";

fn is_non_leaf_tag(name: &str) -> bool {
    matches!(
        name.trim(),
        "自动选择" | "手动选择" | "智能选路" | "auto" | "manual" | ""
    )
}

/// 将叶子节点名写入各 selector；Proxy 组只能指向「手动选择」子组。
pub fn apply_node_selection(api_port: u16, node_name: &str) -> bool {
    let node = node_name.trim();
    if is_non_leaf_tag(node) {
        return true;
    }
    let mut ok = false;
    ok = put_selector(api_port, GROUP_MANUAL, node) || ok;
    ok = put_selector(api_port, GROUP_RELAY, node) || ok;
    ok = put_selector(api_port, GROUP_GLOBAL, node) || ok;
    ok = put_selector(api_port, GROUP_PROXY, GROUP_MANUAL) || ok;
    // 海外直连池不含回国线，失败属预期
    let _ = put_selector(api_port, GROUP_DIRECT_POOL, node);
    if !ok {
        eprintln!("[desktop_selector] patchSelector failed for {node}");
    }
    ok
}

fn put_selector(api_port: u16, group: &str, selection: &str) -> bool {
    match put_selector_inner(api_port, group, selection) {
        Ok(()) => {
            eprintln!("[desktop_selector] patchSelector {group} -> {selection}");
            true
        }
        Err(err) => {
            eprintln!("[desktop_selector] patchSelector {group} -> {selection} failed: {err}");
            false
        }
    }
}

fn put_selector_inner(api_port: u16, group: &str, selection: &str) -> Result<(), String> {
    let mut stream = TcpStream::connect(format!("127.0.0.1:{api_port}"))
        .map_err(|e| format!("connect: {e}"))?;
    stream
        .set_read_timeout(Some(Duration::from_millis(1200)))
        .map_err(|e| format!("read timeout: {e}"))?;
    stream
        .set_write_timeout(Some(Duration::from_millis(800)))
        .map_err(|e| format!("write timeout: {e}"))?;

    let path = format!("/proxies/{}", urlencode_path_segment(group));
    let body = serde_json::json!({ "name": selection }).to_string();
    let req = format!(
        "PUT {path} HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream
        .write_all(req.as_bytes())
        .map_err(|e| format!("write: {e}"))?;
    let mut buf = Vec::new();
    stream
        .read_to_end(&mut buf)
        .map_err(|e| format!("read: {e}"))?;
    let text = String::from_utf8_lossy(&buf);
    let status = text.lines().next().unwrap_or("");
    if status.contains("204") || status.contains("200") || status.contains("201") {
        return Ok(());
    }
    // 组不存在时 Mihomo 常返回 400/404，对可选组可忽略
    if status.contains("400") || status.contains("404") {
        return Err(format!("http status: {status}"));
    }
    Err(format!("http status: {status}"))
}

fn urlencode_path_segment(s: &str) -> String {
    let mut out = String::with_capacity(s.len() * 3);
    for b in s.as_bytes() {
        match *b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(*b as char)
            }
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::{is_non_leaf_tag, urlencode_path_segment};

    #[test]
    fn rejects_smart_route_tags() {
        assert!(is_non_leaf_tag("智能选路"));
        assert!(is_non_leaf_tag("手动选择"));
        assert!(!is_non_leaf_tag("新加坡4"));
    }

    #[test]
    fn encodes_chinese_group_path() {
        let enc = urlencode_path_segment("手动选择");
        assert!(enc.contains('%'));
        assert!(!enc.contains('手'));
    }
}
