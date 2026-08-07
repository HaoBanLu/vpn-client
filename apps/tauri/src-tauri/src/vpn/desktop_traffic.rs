use std::io::{Read, Write};
use std::net::TcpStream;
use std::time::Duration;

/// 从 Mihomo external-controller 拉取会话累计流量（与 Clash `/connections` 一致）。
pub fn fetch_connection_totals(api_port: u16) -> Option<(u64, u64)> {
    match fetch_connection_totals_inner(api_port) {
        Ok(v) => Some(v),
        Err(err) => {
            eprintln!("[desktop_traffic] fetch /connections failed (port={api_port}): {err}");
            None
        }
    }
}

/// 从 Mihomo `/traffic` 流读取一帧瞬时速率（bytes/s）。比累计差更贴近「当前上下行」。
pub fn fetch_instant_rates(api_port: u16) -> Option<(u64, u64)> {
    match fetch_instant_rates_inner(api_port) {
        Ok(v) => Some(v),
        Err(err) => {
            eprintln!("[desktop_traffic] fetch /traffic failed (port={api_port}): {err}");
            None
        }
    }
}

fn fetch_connection_totals_inner(api_port: u16) -> Result<(u64, u64), String> {
    let body = http_get_body(api_port, "/connections")?;
    let value: serde_json::Value =
        serde_json::from_str(&body).map_err(|e| format!("json: {e}; body={}", truncate(&body, 120)))?;
    let upload = json_u64(&value, "uploadTotal").ok_or_else(|| "missing uploadTotal".to_string())?;
    let download =
        json_u64(&value, "downloadTotal").ok_or_else(|| "missing downloadTotal".to_string())?;
    Ok((upload, download))
}

fn fetch_instant_rates_inner(api_port: u16) -> Result<(u64, u64), String> {
    let mut stream = TcpStream::connect(format!("127.0.0.1:{api_port}"))
        .map_err(|e| format!("connect: {e}"))?;
    stream
        .set_read_timeout(Some(Duration::from_millis(1200)))
        .map_err(|e| format!("read timeout: {e}"))?;
    stream
        .set_write_timeout(Some(Duration::from_millis(800)))
        .map_err(|e| format!("write timeout: {e}"))?;
    let req = "GET /traffic HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
    stream
        .write_all(req.as_bytes())
        .map_err(|e| format!("write: {e}"))?;

    let mut buf = Vec::new();
    let mut tmp = [0u8; 1024];
    // /traffic 为持续流，读到首个完整 JSON 行即可
    loop {
        match stream.read(&mut tmp) {
            Ok(0) => break,
            Ok(n) => {
                buf.extend_from_slice(&tmp[..n]);
                if let Some(line) = first_json_line(&buf) {
                    return parse_traffic_line(line);
                }
                if buf.len() > 64 * 1024 {
                    return Err("traffic stream too large without json line".into());
                }
            }
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut =>
            {
                if let Some(line) = first_json_line(&buf) {
                    return parse_traffic_line(line);
                }
                return Err(format!("read timeout: {e}"));
            }
            Err(e) => return Err(format!("read: {e}")),
        }
    }
    Err("empty traffic stream".into())
}

fn parse_traffic_line(line: &str) -> Result<(u64, u64), String> {
    let value: serde_json::Value = serde_json::from_str(line)
        .map_err(|e| format!("traffic json: {e}; line={}", truncate(line, 80)))?;
    let up = json_u64(&value, "up").ok_or_else(|| "missing up".to_string())?;
    let down = json_u64(&value, "down").ok_or_else(|| "missing down".to_string())?;
    Ok((up, down))
}

fn http_get_body(api_port: u16, path: &str) -> Result<String, String> {
    let mut stream = TcpStream::connect(format!("127.0.0.1:{api_port}"))
        .map_err(|e| format!("connect: {e}"))?;
    stream
        .set_read_timeout(Some(Duration::from_millis(1500)))
        .map_err(|e| format!("read timeout: {e}"))?;
    stream
        .set_write_timeout(Some(Duration::from_millis(800)))
        .map_err(|e| format!("write timeout: {e}"))?;
    let req = format!("GET {path} HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
    stream
        .write_all(req.as_bytes())
        .map_err(|e| format!("write: {e}"))?;
    let mut buf = Vec::new();
    stream
        .read_to_end(&mut buf)
        .map_err(|e| format!("read: {e}"))?;
    parse_http_body(&buf)
}

/// 从 HTTP 响应缓冲中取出 body 的第一行 JSON（忽略 chunk 头数字行）。
fn first_json_line(raw: &[u8]) -> Option<&str> {
    let text = std::str::from_utf8(raw).ok()?;
    let body = match text.split_once("\r\n\r\n") {
        Some((_, b)) => b,
        None => text,
    };
    for line in body.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with('{') && trimmed.contains('}') {
            return Some(trimmed);
        }
    }
    None
}

fn json_u64(value: &serde_json::Value, key: &str) -> Option<u64> {
    let v = value.get(key)?;
    if let Some(n) = v.as_u64() {
        return Some(n);
    }
    if let Some(n) = v.as_i64() {
        return Some(n.max(0) as u64);
    }
    if let Some(f) = v.as_f64() {
        if f.is_finite() && f >= 0.0 {
            return Some(f as u64);
        }
    }
    None
}

fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        return s.to_string();
    }
    s.chars().take(max).collect::<String>() + "…"
}

/// 解析 HTTP/1.1 响应体：支持 Content-Length 与 Transfer-Encoding: chunked。
fn parse_http_body(raw: &[u8]) -> Result<String, String> {
    let text = std::str::from_utf8(raw).map_err(|e| format!("utf8: {e}"))?;
    let (header_part, body_part) = text
        .split_once("\r\n\r\n")
        .ok_or_else(|| "missing header/body separator".to_string())?;
    let mut status_line = None;
    let mut content_length: Option<usize> = None;
    let mut chunked = false;
    for (i, line) in header_part.lines().enumerate() {
        if i == 0 {
            status_line = Some(line.trim());
            continue;
        }
        let lower = line.to_ascii_lowercase();
        if let Some(rest) = lower.strip_prefix("content-length:") {
            content_length = rest.trim().parse().ok();
        } else if let Some(rest) = lower.strip_prefix("transfer-encoding:") {
            if rest.contains("chunked") {
                chunked = true;
            }
        }
    }
    let status = status_line.unwrap_or("");
    if !status.contains("200") {
        return Err(format!("http status: {status}"));
    }
    if chunked {
        return decode_chunked_body(body_part);
    }
    if let Some(len) = content_length {
        let bytes = body_part.as_bytes();
        if bytes.len() < len {
            return Err(format!(
                "content-length {len} but body only {} bytes",
                bytes.len()
            ));
        }
        return std::str::from_utf8(&bytes[..len])
            .map(|s| s.to_string())
            .map_err(|e| format!("body utf8: {e}"));
    }
    Ok(body_part.trim().to_string())
}

fn decode_chunked_body(body: &str) -> Result<String, String> {
    let bytes = body.as_bytes();
    let mut out = Vec::new();
    let mut i = 0;
    while i < bytes.len() {
        while i + 1 < bytes.len() && bytes[i] == b'\r' && bytes[i + 1] == b'\n' {
            i += 2;
        }
        let size_start = i;
        while i < bytes.len() && bytes[i] != b'\r' {
            i += 1;
        }
        if i + 1 >= bytes.len() || bytes[i] != b'\r' || bytes[i + 1] != b'\n' {
            return Err("chunk size line missing CRLF".to_string());
        }
        let size_line = std::str::from_utf8(&bytes[size_start..i])
            .map_err(|e| format!("chunk size utf8: {e}"))?
            .trim();
        let size_hex = size_line.split(';').next().unwrap_or("").trim();
        let size = usize::from_str_radix(size_hex, 16)
            .map_err(|e| format!("chunk size hex '{size_hex}': {e}"))?;
        i += 2;
        if size == 0 {
            break;
        }
        if i + size > bytes.len() {
            return Err(format!(
                "chunk size {size} exceeds remaining {}",
                bytes.len().saturating_sub(i)
            ));
        }
        out.extend_from_slice(&bytes[i..i + size]);
        i += size;
        if i + 1 < bytes.len() && bytes[i] == b'\r' && bytes[i + 1] == b'\n' {
            i += 2;
        }
    }
    String::from_utf8(out).map_err(|e| format!("chunk body utf8: {e}"))
}

#[cfg(test)]
mod tests {
    use super::{decode_chunked_body, first_json_line, parse_http_body};

    #[test]
    fn parses_content_length_body() {
        let json = "{\"uploadTotal\":1,\"downloadTotal\":2}";
        let raw = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\n\r\n{}",
            json.len(),
            json
        );
        let body = parse_http_body(raw.as_bytes()).expect("body");
        assert_eq!(body, json);
    }

    #[test]
    fn parses_chunked_body() {
        let json = "{\"uploadTotal\":10,\"downloadTotal\":20}";
        let chunk = format!(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n{:x}\r\n{}\r\n0\r\n\r\n",
            json.len(),
            json
        );
        let body = parse_http_body(chunk.as_bytes()).expect("chunked body");
        assert_eq!(body, json);
        let value: serde_json::Value = serde_json::from_str(&body).unwrap();
        assert_eq!(value["uploadTotal"], 10);
        assert_eq!(value["downloadTotal"], 20);
    }

    #[test]
    fn decode_chunked_multi_chunks() {
        let body = "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n";
        assert_eq!(decode_chunked_body(body).unwrap(), "hello world");
    }

    #[test]
    fn rejects_non_200() {
        let raw = b"HTTP/1.1 404 Not Found\r\nContent-Length: 2\r\n\r\n{}";
        assert!(parse_http_body(raw).is_err());
    }

    #[test]
    fn rejects_invalid_json_payload_shape() {
        let raw = b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nnotjs";
        let body = parse_http_body(raw).expect("body");
        assert_eq!(body, "notjs");
        assert!(serde_json::from_str::<serde_json::Value>(&body).is_err());
    }

    #[test]
    fn json_u64_accepts_float_totals() {
        let value: serde_json::Value =
            serde_json::from_str(r#"{"uploadTotal":12.0,"downloadTotal":34}"#).unwrap();
        assert_eq!(super::json_u64(&value, "uploadTotal"), Some(12));
        assert_eq!(super::json_u64(&value, "downloadTotal"), Some(34));
    }

    #[test]
    fn first_json_line_from_traffic_stream() {
        let raw = b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"up\":10,\"down\":20}\n{\"up\":1,\"down\":2}\n";
        assert_eq!(first_json_line(raw), Some(r#"{"up":10,"down":20}"#));
    }
}
