#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器单节点连接：对照真机「已连接下载0」——抓 vpn_network_ok / dataplane / 速率相关日志。"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent.parent
SHOT = ROOT / "scripts" / "_emulator_shots"
SHOT.mkdir(parents=True, exist_ok=True)
OUT = SHOT / "emulator_zero_dl_diag.json"
LOG_OUT = SHOT / "emulator_zero_dl_logcat.txt"

ADB = Path(r"C:\Users\luban\AppData\Local\Android\Sdk\platform-tools\adb.exe")
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-x86_64-debug.apk"
API = "http://192.229.87.112:44080/api/v1"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
PKG = "com.vpn.member"
# 真机昨晚失败/成功对照
NODES = ["新加坡-BGP线路", "新加坡1", "新加坡4"]


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(ADB), *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        errors="replace",
    )


def http_json(method: str, path: str, body: dict | None = None, token: str | None = None) -> dict:
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{path}", data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    req.add_header("User-Agent", "KuayunVPN-Android/1.0")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=45) as resp:
        return json.loads(resp.read().decode())


def login() -> str:
    return http_json(
        "POST",
        "/auth/login",
        {"email": EMAIL, "password": PASSWORD, "device_type": "phone", "platform": "android"},
    )["data"]["token"]


def fetch_config(token: str, node: str) -> str:
    q = urllib.parse.urlencode({"node": node, "profile": "overseas_weak", "route_mode": "full"})
    return http_json("GET", f"/client/config?{q}", token=token)["data"]["config"]


def ensure_install() -> None:
    r = adb("install", "-r", str(APK), timeout=180)
    text = (r.stdout or "") + (r.stderr or "")
    if r.returncode != 0 and "Success" not in text:
        raise RuntimeError(f"install failed: {text[-500:]}")
    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")
    # 部分模拟器仍弹 VPN 确认
    adb("shell", "settings", "put", "global", "policy_control", "immersive.full=*")


def push_config(yaml_text: str) -> None:
    tmp = SHOT / "_emu_cfg.yaml"
    tmp.write_text(yaml_text, encoding="utf-8", newline="\n")
    adb("push", str(tmp), "/data/local/tmp/config.yaml")
    adb("shell", "run-as", PKG, "mkdir", "-p", "files/clash")
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/config.yaml", "files/clash/config.yaml")


def disconnect() -> None:
    adb(
        "shell",
        "am",
        "startservice",
        "-n",
        f"{PKG}/.vpn.VpnTunnelService",
        "-a",
        f"{PKG}.DISCONNECT",
    )
    time.sleep(3)


def connect(node: str) -> None:
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    adb(
        "shell",
        "am",
        "broadcast",
        "-a",
        f"{PKG}.DEBUG_CONNECT",
        "--es",
        "route_target",
        node,
        "--es",
        "route_mode",
        "full",
        PKG,
    )


def logcat() -> str:
    r = adb("logcat", "-d", "-t", "4000", timeout=90)
    return (r.stdout or "") + (r.stderr or "")


def interesting(log: str) -> list[str]:
    keys = (
        "dataplane_check",
        "vpn_network_ok",
        "隧道探测",
        "隧道基础",
        "open_tun",
        "start_mihomo",
        "dataplane inactive",
        "DEBUG_CONNECT",
        "debug connect",
        "TunDataPlane",
        "TunConnectivity",
        "VpnTunnel",
        "patch_selector",
        "traffic",
        "now_down",
        "connect failed",
        "VpnDiag",
        "post_connect",
    )
    out = []
    for ln in log.splitlines():
        if any(k.lower() in ln.lower() for k in keys):
            out.append(ln[-350:])
    return out[-80:]


def parse_dataplane(log: str) -> dict:
    m = None
    for ln in log.splitlines():
        if "dataplane_check" in ln:
            m = ln
    flags = {
        "has_dataplane_check": "dataplane_check" in log,
        "vpn_network_ok_true": "vpn_network_ok=true" in log or '"vpn_network_ok":"true"' in log or "vpn_network_ok=true" in log.replace(" ", ""),
        "vpn_network_ok_false": "vpn_network_ok=false" in log,
        "tun_download_grew": "tun_download_grew=true" in log,
        "overseas_mixed": "overseas_mixed=true" in log,
        "domestic_mixed": "domestic_mixed=true" in log,
        "probe_pass": "隧道探测通过" in log,
        "probe_fail": "隧道基础连通性失败" in log,
        "dataplane_inactive": "dataplane inactive" in log.lower(),
        "open_tun": "open_tun" in log or "phase=open_tun" in log,
        "last_dataplane_line": (m or "")[-400:],
    }
    # also parse key=value from last dataplane line
    if m:
        for key in (
            "vpn_network_ok",
            "tun_download_grew",
            "overseas_mixed",
            "domestic_mixed",
            "traffic_grew",
            "tun_tcp_log",
            "stack",
        ):
            mm = re.search(rf"{key}[=:](\S+)", m)
            if mm:
                flags[f"dp_{key}"] = mm.group(1).strip('",}')
    return flags


def probe_browser() -> None:
    adb(
        "shell",
        "am",
        "start",
        "-a",
        "android.intent.action.VIEW",
        "-d",
        "https://www.gstatic.com/generate_204",
    )
    time.sleep(5)


def test_one(token: str, node: str) -> dict:
    print(f"\n===== TEST {node} =====", flush=True)
    disconnect()
    cfg = fetch_config(token, node)
    print(f"config bytes={len(cfg)} has_proxies={'proxies:' in cfg}", flush=True)
    push_config(cfg)
    connect(node)
    print("waiting 40s for connect+verify...", flush=True)
    time.sleep(40)
    log1 = logcat()
    flags1 = parse_dataplane(log1)
    print("after connect:", json.dumps({k: v for k, v in flags1.items() if k != "last_dataplane_line"}, ensure_ascii=False), flush=True)
    if flags1.get("last_dataplane_line"):
        print("dataplane:", flags1["last_dataplane_line"][-220:], flush=True)

    probe_browser()
    log2 = logcat()
    flags2 = parse_dataplane(log2)
    # vpn iface traffic?
    iface = adb("shell", "ip", "link", "show")
    routes = adb("shell", "ip", "route")
    # try curl via toybox if any
    curl = adb(
        "shell",
        "sh",
        "-c",
        "toybox wget -T 8 -O /dev/null https://www.gstatic.com/generate_204 2>&1 | head -5; "
        "ping -c 1 -W 3 1.1.1.1 2>&1 | head -3",
        timeout=30,
    )

    snippets = interesting(log2)
    result = {
        "node": node,
        "after_connect": flags1,
        "after_browser": flags2,
        "curl_out": ((curl.stdout or "") + (curl.stderr or ""))[-800:],
        "iface_has_tun": "tun" in ((iface.stdout or "").lower()),
        "snippets": snippets,
    }
    print(
        f"RESULT {node}: vpn_ok_false={flags2.get('vpn_network_ok_false')} "
        f"vpn_ok_true={flags2.get('vpn_network_ok_true')} "
        f"probe_pass={flags2.get('probe_pass')} probe_fail={flags2.get('probe_fail')} "
        f"inactive={flags2.get('dataplane_inactive')} tun={result['iface_has_tun']}",
        flush=True,
    )
    return result


def main() -> int:
    dev = adb("devices").stdout
    if "emulator-" not in dev or "\tdevice" not in dev:
        print("no emulator device", dev)
        return 1
    print("device ok")
    ensure_install()
    token = login()
    print("login ok")

    all_logs = []
    results = []
    for node in NODES:
        try:
            r = test_one(token, node)
            results.append(r)
            all_logs.append(f"\n\n===== {node} =====\n")
            all_logs.extend(r.get("snippets") or [])
        except Exception as e:
            results.append({"node": node, "error": str(e)})
            print("ERR", node, e, flush=True)
        finally:
            disconnect()

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    LOG_OUT.write_text("\n".join(all_logs), encoding="utf-8")
    print("\n=== SUMMARY ===")
    for r in results:
        if "error" in r:
            print(r["node"], "ERROR", r["error"])
            continue
        f = r.get("after_browser") or r.get("after_connect") or {}
        print(
            r["node"],
            "| probe_pass=",
            f.get("probe_pass"),
            "vpn_network_ok_false=",
            f.get("vpn_network_ok_false"),
            "vpn_network_ok_true=",
            f.get("vpn_network_ok_true"),
            "grew=",
            f.get("tun_download_grew"),
            "inactive=",
            f.get("dataplane_inactive"),
        )
    print("saved", OUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
