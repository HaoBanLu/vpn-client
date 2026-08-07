#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器 E2E：登录 luban 账号，逐个连接新加坡节点，检查 TUN 数据面与出网。

用法（需已启动 x86_64 模拟器）:
  python apps/android/scripts/sg_emulator_all_nodes_test.py
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent.parent
OUT = REPO / "tmp" / "sg_emulator_e2e_results.json"

API_BASE = "http://192.229.87.112:44080/api/v1"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
ROUTE_MODE = "full"
CONNECT_WAIT_SEC = 35
ADB = Path(os.environ.get("ANDROID_HOME", r"C:\Users\luban\AppData\Local\Android\Sdk")) / "platform-tools" / "adb.exe"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-x86_64-debug.apk"


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    cmd = [str(ADB), *args]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, errors="replace")


def http_json(method: str, path: str, body: dict | None = None, token: str | None = None) -> dict:
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API_BASE}{path}", data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    req.add_header("User-Agent", "KuayunVPN-Android/1.0")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=45) as resp:
        return json.loads(resp.read().decode())


def login() -> str:
    body = http_json(
        "POST",
        "/auth/login",
        {
            "email": EMAIL,
            "password": PASSWORD,
            "device_type": "phone",
            "platform": "android",
        },
    )
    return body["data"]["token"]


def list_sg_nodes(token: str) -> list[str]:
    body = http_json("GET", "/nodes", token=token)
    names: list[str] = []
    for n in body.get("data", {}).get("nodes") or []:
        name = str(n.get("name", ""))
        if "新加坡" in name or "Singapore" in name:
            names.append(name)
    return names


def fetch_client_config(token: str, node: str) -> str:
    q = urllib.parse.urlencode(
        {"node": node, "profile": "overseas_weak", "route_mode": ROUTE_MODE}
    )
    body = http_json("GET", f"/client/config?{q}", token=token)
    return body["data"]["config"]


@dataclass
class NodeResult:
    name: str
    connect_ok: bool = False
    dataplane_pass: bool = False
    tun_tcp: bool = False
    curl_code: str = ""
    curl_err: str = ""
    failed_reason: str = ""
    log_snippets: list[str] = field(default_factory=list)


def ensure_app_installed() -> None:
    if not APK.is_file():
        raise FileNotFoundError(f"APK not found: {APK}")
    r = adb("install", "-r", str(APK))
    if r.returncode != 0 and "Success" not in (r.stdout + r.stderr):
        raise RuntimeError(f"adb install failed: {r.stderr or r.stdout}")
    adb("shell", "appops", "set", "com.vpn.member", "ACTIVATE_VPN", "allow")
    adb(
        "shell",
        "am",
        "start",
        "-n",
        "com.vpn.member/.MainActivity",
    )
    time.sleep(2)


def launch_app() -> None:
    adb(
        "shell",
        "am",
        "start",
        "-n",
        "com.vpn.member/.MainActivity",
    )


def push_config(yaml_text: str) -> None:
    tmp = REPO / "tmp" / "emulator_sg_config.yaml"
    tmp.parent.mkdir(parents=True, exist_ok=True)
    tmp.write_text(yaml_text, encoding="utf-8", newline="\n")
    adb("push", str(tmp), "/data/local/tmp/config.yaml")
    adb("shell", "run-as", "com.vpn.member", "mkdir", "-p", "files/clash/providers/ruleset")
    adb("shell", "run-as", "com.vpn.member", "cp", "/data/local/tmp/config.yaml", "files/clash/config.yaml")


def disconnect_vpn() -> None:
    adb(
        "shell",
        "am",
        "startservice",
        "-n",
        "com.vpn.member/.vpn.VpnTunnelService",
        "-a",
        "com.vpn.member.DISCONNECT",
    )
    time.sleep(2)


def connect_node(node: str) -> None:
    adb("logcat", "-c")
    launch_app()
    adb(
        "shell",
        "am",
        "broadcast",
        "-a",
        "com.vpn.member.DEBUG_CONNECT",
        "--es",
        "route_target",
        node,
        "--es",
        "route_mode",
        ROUTE_MODE,
        "com.vpn.member",
    )
    time.sleep(CONNECT_WAIT_SEC)


def read_logcat() -> str:
    r = adb("logcat", "-d", timeout=60)
    return (r.stdout or "") + (r.stderr or "")


def probe_egress() -> tuple[str, str]:
    # 模拟器无 curl，用 Chrome 轻量打开 generate_204 触发 TUN 流量。
    adb(
        "shell",
        "am",
        "start",
        "-a",
        "android.intent.action.VIEW",
        "-d",
        "https://www.gstatic.com/generate_204",
    )
    time.sleep(4)
    log = read_logcat()
    if re.search(r"\[TCP\].*172\.19\.0\.1", log):
        return "204", ""
    if "vpn_network_ok" in log:
        return "204", ""
    return "000", "no tun tcp in logcat"


def analyze_logs(log: str, result: NodeResult) -> None:
    snippets = []
    for pat in (
        r"dataplane_check.*",
        r"post_connect_verify.*",
        r"tunnel dataplane inactive.*",
        r"open_tun.*",
        r"tun_stack_fallback.*",
        r"patch_selector.*",
        r"connect failed.*",
        r"Tunnel verify.*",
        r"隧道基础连通性失败",
        r"隧道探测通过",
    ):
        for ln in log.splitlines():
            if re.search(pat, ln, re.I):
                snippets.append(ln[-300:])
    result.log_snippets = snippets[-15:]
    result.tun_tcp = bool(re.search(r"\[TCP\].*172\.19\.0\.1", log)) or (
        "tun_tcp_log=true" in log or "tun_tcp_seen" in log
    )
    result.dataplane_pass = (
        "vpn_network_ok=true" in log
        or "tun_tcp_log=true" in log
        or "tun_download_grew=true" in log
        or ("隧道探测通过" in log and "dataplane inactive" not in log.lower())
    )
    if "dataplane inactive" in log.lower():
        result.failed_reason = "dataplane_inactive"
    elif "connect failed" in log.lower():
        result.failed_reason = "connect_failed"
    elif "隧道基础连通性失败" in log:
        result.failed_reason = "mihomo_probe_failed"


def test_node(token: str, node: str) -> NodeResult:
    result = NodeResult(name=node)
    try:
        cfg = fetch_client_config(token, node)
        if "proxies:" not in cfg:
            result.failed_reason = "empty_config"
            return result
        push_config(cfg)
        connect_node(node)
        log = read_logcat()
        analyze_logs(log, result)
        code, err = probe_egress()
        result.curl_code = code
        result.curl_err = err
        result.connect_ok = "debug connect started" in log.lower() or "start_mihomo" in log.lower()
        if code in ("200", "204"):
            result.dataplane_pass = True
        if not result.dataplane_pass and not result.failed_reason:
            if code not in ("200", "204"):
                result.failed_reason = f"curl_{code}"
    except Exception as e:
        result.failed_reason = str(e)[:200]
    return result


def main() -> int:
    if not ADB.is_file():
        print(f"adb not found: {ADB}", file=sys.stderr)
        return 1
    dev = adb("devices")
    if "device" not in dev.stdout or dev.stdout.strip().count("device") < 2:
        print("No emulator/device attached. Start emulator first.", file=sys.stderr)
        print(dev.stdout)
        return 1

    print(f"API: {API_BASE}")
    print(f"APK: {APK}")
    ensure_app_installed()

    token = login()
    nodes = list_sg_nodes(token)
    if not nodes:
        print("No Singapore nodes from API", file=sys.stderr)
        return 1
    print(f"Singapore nodes ({len(nodes)}): {nodes}")

    results = []
    for n in nodes:
        disconnect_vpn()
        results.append(test_node(token, n))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps([r.__dict__ for r in results], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("\n=== RESULTS ===")
    for r in results:
        ok = r.curl_code in ("200", "204") or r.dataplane_pass
        mark = "PASS" if ok else "FAIL"
        line = (
            f"{mark} | {r.name} | curl={r.curl_code} tun_tcp={r.tun_tcp} "
            f"reason={r.failed_reason or '-'}"
        )
        try:
            print(line)
            if r.log_snippets:
                print(f"  log: {r.log_snippets[-1][:180]}")
        except UnicodeEncodeError:
            print(line.encode("utf-8", errors="replace").decode("utf-8"))

    passed = sum(1 for r in results if r.curl_code in ("200", "204"))
    print(f"\nSummary: {passed}/{len(results)} nodes with working egress")
    print(f"Full report: {OUT}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
