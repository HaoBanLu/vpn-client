#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真机 USB E2E：登录 luban 账号，逐个连接新加坡节点，分析 logcat 判定通断。

用法:
  python apps/android/scripts/sg_device_all_nodes_test.py
  python apps/android/scripts/sg_device_all_nodes_test.py --serial KFMZTC5DY9GYXS95
"""
from __future__ import annotations

import argparse
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
OUT = REPO / "tmp" / "sg_device_e2e_results.json"

API_BASE = "http://192.229.87.112:44080/api/v1"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
ROUTE_MODE = "full"
CONNECT_WAIT_SEC = 48
ADB = Path(os.environ.get("ANDROID_HOME", r"C:\Users\luban\AppData\Local\Android\Sdk")) / "platform-tools" / "adb.exe"
PKG = "com.vpn.member"


def pick_apk() -> Path:
    debug_dir = ROOT / "app" / "build" / "outputs" / "apk" / "debug"
    for name in ("app-arm64-v8a-debug.apk", "app-universal-debug.apk", "app-debug.apk"):
        p = debug_dir / name
        if p.is_file():
            return p
    matches = sorted(debug_dir.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    if matches:
        return matches[0]
    raise FileNotFoundError(f"No debug APK under {debug_dir}")


@dataclass
class NodeResult:
    name: str
    connect_started: bool = False
    vpn_connected: bool = False
    mixed_overseas: bool = False
    dataplane_pass: bool = False
    vpn_network_ok: bool = False
    tun_download_grew: bool = False
    failed_reason: str = ""
    log_snippets: list[str] = field(default_factory=list)


class Device:
    def __init__(self, serial: str | None) -> None:
        self.serial = serial
        self.apk = pick_apk()

    def adb(self, *args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
        cmd = [str(ADB)]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += list(args)
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, errors="replace")

    def ensure_ready(self) -> None:
        if not ADB.is_file():
            raise FileNotFoundError(f"adb not found: {ADB}")
        devs = self.adb("devices").stdout
        if self.serial:
            if f"{self.serial}\tdevice" not in devs:
                raise RuntimeError(f"Device {self.serial} not found:\n{devs}")
        else:
            lines = [ln for ln in devs.splitlines() if "\tdevice" in ln and "List" not in ln]
            emulators = [ln for ln in lines if "emulator-" in ln]
            phones = [ln for ln in lines if "emulator-" not in ln]
            if phones:
                self.serial = phones[0].split("\t")[0].strip()
            elif lines:
                self.serial = lines[0].split("\t")[0].strip()
            else:
                raise RuntimeError(f"No device:\n{devs}")

    def install(self) -> None:
        r = self.adb("install", "-r", str(self.apk))
        if r.returncode != 0 and "Success" not in (r.stdout + r.stderr):
            raise RuntimeError(f"install failed: {r.stderr or r.stdout}")
        self.adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")

    def launch(self) -> None:
        self.adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")

    def disconnect(self) -> None:
        self.adb(
            "shell",
            "am",
            "startservice",
            "-n",
            f"{PKG}/.vpn.VpnTunnelService",
            "-a",
            f"{PKG}.DISCONNECT",
        )
        time.sleep(2)

    def push_config(self, yaml_text: str) -> None:
        tmp = REPO / "tmp" / "device_sg_config.yaml"
        tmp.parent.mkdir(parents=True, exist_ok=True)
        tmp.write_text(yaml_text, encoding="utf-8", newline="\n")
        self.adb("push", str(tmp), "/data/local/tmp/config.yaml")
        self.adb("shell", "run-as", PKG, "mkdir", "-p", "files/clash/providers/ruleset")
        self.adb(
            "shell",
            "run-as",
            PKG,
            "cp",
            "/data/local/tmp/config.yaml",
            "files/clash/config.yaml",
        )

    def connect_node(self, node: str) -> None:
        self.adb("logcat", "-c")
        self.launch()
        time.sleep(1)
        self.adb(
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
            PKG,
        )
        time.sleep(CONNECT_WAIT_SEC)

    def read_logcat(self) -> str:
        r = self.adb("logcat", "-d", timeout=90)
        return (r.stdout or "") + (r.stderr or "")


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
        {"email": EMAIL, "password": PASSWORD, "device_type": "phone", "platform": "android"},
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
    q = urllib.parse.urlencode({"node": node, "profile": "overseas_weak", "route_mode": ROUTE_MODE})
    body = http_json("GET", f"/client/config?{q}", token=token)
    return body["data"]["config"]


def analyze(log: str, result: NodeResult) -> None:
    snippets: list[str] = []
    patterns = (
        r"dataplane_check.*",
        r"vpn_network_ok.*",
        r"TunConnectivityVerifier.*",
        r"tunnel dataplane inactive.*",
        r"connect failed.*",
        r"open_tun.*",
        r"隧道探测通过.*",
        r"隧道基础连通性失败.*",
        r"setting state=CONNECTED.*",
        r"debug connect started.*",
    )
    for pat in patterns:
        for ln in log.splitlines():
            if re.search(pat, ln, re.I):
                snippets.append(ln[-320:])
    result.log_snippets = snippets[-12:]

    result.connect_started = "debug connect started" in log.lower() or "start_mihomo" in log
    result.vpn_connected = "setting state=CONNECTED" in log or "going from CONNECTING to CONNECTED" in log
    result.mixed_overseas = "overseas_mixed=true" in log
    result.vpn_network_ok = "vpn_network_ok=true" in log or "phase=vpn_network_ok" in log
    result.tun_download_grew = "tun_download_grew=true" in log

    dp_lines = [ln for ln in log.splitlines() if "dataplane_check" in ln]
    if dp_lines:
        last = dp_lines[-1]
        result.dataplane_pass = (
            "vpn_network_ok=true" in last
            or "tun_download_grew=true" in last
            or "tun_tcp_log=true" in last
        ) and "dataplane inactive" not in log.lower()

    if result.dataplane_pass or (result.vpn_connected and result.mixed_overseas and result.tun_download_grew):
        result.dataplane_pass = True

    if result.dataplane_pass:
        return
    if "dataplane inactive" in log.lower():
        result.failed_reason = "dataplane_inactive"
    elif "tunnel verify failed (no network)" in log.lower():
        result.failed_reason = "mixed_probe_no_network"
    elif "tunnel verify failed (proxy unreachable)" in log.lower():
        result.failed_reason = "proxy_unreachable"
    elif "connect failed" in log.lower():
        result.failed_reason = "connect_failed"
    elif not result.vpn_connected:
        result.failed_reason = "vpn_not_connected"
    else:
        result.failed_reason = "unknown"


def test_node(dev: Device, token: str, node: str) -> NodeResult:
    result = NodeResult(name=node)
    try:
        cfg = fetch_client_config(token, node)
        if "proxies:" not in cfg:
            result.failed_reason = "empty_config"
            return result
        dev.push_config(cfg)
        dev.connect_node(node)
        log = dev.read_logcat()
        analyze(log, result)
    except Exception as e:
        result.failed_reason = str(e)[:200]
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    args = parser.parse_args()

    dev = Device(args.serial)
    dev.ensure_ready()
    print(f"Device: {dev.serial}")
    print(f"APK: {dev.apk}")
    print(f"API: {API_BASE}")

    dev.install()
    token = login()
    nodes = list_sg_nodes(token)
    if not nodes:
        print("No Singapore nodes", file=sys.stderr)
        return 1
    print(f"Singapore nodes ({len(nodes)}): {nodes}")

    results: list[NodeResult] = []
    for n in nodes:
        print(f"\n--- Testing {n} ---")
        dev.disconnect()
        r = test_node(dev, token, n)
        results.append(r)
        mark = "PASS" if r.dataplane_pass else "FAIL"
        print(
            f"{mark} | connected={r.vpn_connected} mixed_overseas={r.mixed_overseas} "
            f"vpn_net={r.vpn_network_ok} tun_rx={r.tun_download_grew} reason={r.failed_reason or '-'}"
        )
        if r.log_snippets:
            print(f"  log: {r.log_snippets[-1][:200]}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps([r.__dict__ for r in results], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    passed = sum(1 for r in results if r.dataplane_pass)
    print(f"\n=== SUMMARY: {passed}/{len(results)} PASS ===")
    print(f"Report: {OUT}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
