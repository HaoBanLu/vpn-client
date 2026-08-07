#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json, urllib.parse, urllib.request, subprocess, time, os
from pathlib import Path

ADB = os.environ.get("ADB", r"C:\Users\luban\AppData\Local\Android\Sdk\platform-tools\adb.exe")
S = "KFMZTC5DY9GYXS95"
API = "http://192.229.87.112:44080/api/v1"
PKG = "com.vpn.member"
NODES = ["新加坡-普通线路", "新加坡-BGP线路", "新加坡1", "新加坡2", "新加坡5"]

def adb(*a, timeout=120):
    return subprocess.run([ADB, "-s", S, *a], capture_output=True, text=True, errors="replace", timeout=timeout)

def http(m, p, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"{API}{p}", data=data, method=m)
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "KuayunVPN-Android/1.0")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=45) as r:
        return json.loads(r.read().decode())

token = http("POST", "/auth/login", {
    "email": "luban7733@gmail.com", "password": "123456",
    "device_type": "phone", "platform": "android",
})["data"]["token"]

results = []
for node in NODES:
    print(f"--- {node} ---")
    q = urllib.parse.urlencode({"node": node, "profile": "overseas_weak", "route_mode": "full"})
    cfg = http("GET", f"/client/config?{q}", token=token)["data"]["config"]
    tmp = Path(r"d:\Code\Go-www\vpn\tmp\device_sg_config.yaml")
    tmp.write_text(cfg, encoding="utf-8")
    adb("shell", "am", "startservice", "-n", f"{PKG}/.vpn.VpnTunnelService", "-a", f"{PKG}.DISCONNECT")
    time.sleep(2)
    adb("push", str(tmp), "/data/local/tmp/config.yaml")
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/config.yaml", "files/clash/config.yaml")
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(1)
    adb("shell", "am", "broadcast", "-a", "com.vpn.member.DEBUG_CONNECT",
        "--es", "route_target", node, "--es", "route_mode", "full", PKG)
    time.sleep(50)
    log = adb("logcat", "-d", timeout=90).stdout
    passed = (
        "tun_download_grew=true" in log or "vpn_network_ok=true" in log
        or ("overseas_mixed=true" in log and "dataplane inactive" not in log.lower()
            and "setting state=CONNECTED" in log)
    )
    reason = "-"
    if "tunnel verify failed (no network)" in log.lower():
        reason = "mixed_no_network"
    elif "dataplane inactive" in log.lower():
        reason = "dataplane_inactive"
    elif "connect failed" in log.lower():
        reason = "connect_failed"
    elif not passed:
        reason = "other"
    row = {
        "name": node, "pass": passed, "reason": reason,
        "connected": "CONNECTED" in log,
        "overseas": "overseas_mixed=true" in log,
        "tun_rx": "tun_download_grew=true" in log,
    }
    results.append(row)
    mark = "PASS" if passed else "FAIL"
    print(f"{mark} | reason={reason} | connected={row['connected']} overseas={row['overseas']} tun_rx={row['tun_rx']}")
    for ln in [l for l in log.splitlines() if "dataplane_check" in l or "TunConnectivityVerifier" in l][-3:]:
        print(" ", ln[-220:])

out = Path(r"d:\Code\Go-www\vpn\tmp\sg_device_retry_results.json")
out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"\nKEY: {sum(1 for r in results if r['pass'])}/{len(results)} PASS")
