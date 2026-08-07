#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器：缅甸时区 + 回国节点，验证海外回国首连探测（现行 App 3.14 / 策略同 3.12）。

用法（先冷启带时区）:
  emulator -avd Pixel_10 -timezone Asia/Yangon -no-snapshot-load
  python apps/android/scripts/emulator_myanmar_return_verify.py
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ.get("ANDROID_HOME", r"C:\Users\luban\AppData\Local\Android\Sdk")) / "platform-tools" / "adb.exe"
PKG = "com.vpn.member"
EMAIL = os.environ.get("VPN_E2E_EMAIL", "zc16@qq.com")
PASSWORD = os.environ.get("VPN_E2E_PASSWORD", "123456")
NODE_HINT = "芜湖"
SHOT_DIR = ROOT / "scripts" / "_emulator_shots"
SHOT_DIR.mkdir(parents=True, exist_ok=True)
OUT = SHOT_DIR / "myanmar_return_verify.txt"


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(ADB), *args],
        capture_output=True,
        text=True,
        timeout=timeout,
        errors="replace",
    )


def log(msg: str) -> None:
    print(msg, flush=True)
    with OUT.open("a", encoding="utf-8") as f:
        f.write(msg + "\n")


def find_apk() -> Path:
    debug = ROOT / "app" / "build" / "outputs" / "apk" / "debug"
    abi = (adb("shell", "getprop", "ro.product.cpu.abi").stdout or "").strip() or "x86_64"
    preferred = debug / f"app-{abi}-debug.apk"
    if preferred.is_file():
        return preferred
    for name in ("app-x86_64-debug.apk", "app-arm64-v8a-debug.apk"):
        p = debug / name
        if p.is_file():
            return p
    raise FileNotFoundError(f"无 debug apk: {debug}")


def read_timezone() -> str:
    p = adb("shell", "getprop", "persist.sys.timezone")
    d = adb("shell", "date")
    return f"persist={(p.stdout or '').strip()} date={(d.stdout or '').strip()}"


def clear_and_install(apk: Path) -> None:
    adb("uninstall", PKG)
    p = adb("install", "-r", str(apk), timeout=180)
    if p.returncode != 0:
        raise RuntimeError(f"install failed: {p.stdout}\n{p.stderr}")
    log(f"installed {apk.name}")


def launch() -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/com.vpn.member.MainActivity")
    time.sleep(2.5)


def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = SHOT_DIR / "_mm_ui.xml"
    adb("pull", "/sdcard/ui.xml", str(local))
    return local.read_text(encoding="utf-8", errors="replace")


def parse_nodes() -> list[dict[str, str]]:
    raw = dump_ui()
    idx = raw.find("<hierarchy")
    if idx >= 0:
        raw = raw[idx:]
    root = ET.fromstring(raw)
    out: list[dict[str, str]] = []
    for n in root.iter("node"):
        out.append(
            {
                "text": (n.get("text") or "").strip(),
                "class": n.get("class") or "",
                "bounds": n.get("bounds") or "",
            }
        )
    return out


def all_text() -> set[str]:
    return {n["text"] for n in parse_nodes() if n["text"]}


def center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_bounds(bounds: str) -> bool:
    c = center(bounds)
    if not c:
        return False
    adb("shell", "input", "tap", str(c[0]), str(c[1]))
    return True


def tap_text(*texts: str) -> bool:
    for n in parse_nodes():
        if any(t == n["text"] or t in n["text"] for t in texts):
            if tap_bounds(n["bounds"]):
                return True
    return False


def wait_text(*texts: str, timeout: float = 30) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        joined = " ".join(all_text())
        if any(t in joined for t in texts):
            return True
        time.sleep(0.8)
    return False


def input_literal(s: str) -> None:
    buf: list[str] = []
    for ch in s:
        if ch == "@":
            if buf:
                adb("shell", "input", "text", "".join(buf))
                buf.clear()
            adb("shell", "input", "keyevent", "77")
        elif ch == " ":
            if buf:
                adb("shell", "input", "text", "".join(buf))
                buf.clear()
            adb("shell", "input", "text", "%s")
        else:
            buf.append(ch)
    if buf:
        adb("shell", "input", "text", "".join(buf))


def login() -> None:
    launch()
    if not wait_text("登录", "欢迎回来", timeout=25):
        if wait_text("连接", "节点", "一键连接", "未连接", timeout=5):
            log("already on main UI")
            return
        raise RuntimeError(f"未进登录页 texts={sorted(all_text())[:30]}")
    edits = [n for n in parse_nodes() if "EditText" in n.get("class", "")]
    if len(edits) < 2:
        raise RuntimeError(f"EditText不足 {len(edits)}")
    tap_bounds(edits[0]["bounds"])
    time.sleep(0.25)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(50):
        adb("shell", "input", "keyevent", "67")
    input_literal(EMAIL)
    time.sleep(0.35)
    tap_bounds(edits[1]["bounds"])
    time.sleep(0.25)
    for _ in range(30):
        adb("shell", "input", "keyevent", "67")
    input_literal(PASSWORD)
    time.sleep(0.4)
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.4)
    tap_text("登录") or adb("shell", "input", "tap", "540", "1400")
    time.sleep(2.0)
    if any(t in all_text() for t in ("欢迎回来", "登录跨云")):
        tap_text("登录")
        time.sleep(2.5)
    if not wait_text("未连接", "一键连接", "连接", "节点", "已保护", "套餐", timeout=60):
        raise RuntimeError(f"登录失败 texts={sorted(all_text())[:40]}")
    log("login ok")


def connect_wuhu() -> None:
    tap_text("节点")
    time.sleep(1.5)
    found = False
    for _ in range(14):
        if any(NODE_HINT in t for t in all_text()):
            found = True
            break
        adb("shell", "input", "swipe", "540", "1700", "540", "700", "350")
        time.sleep(0.4)
    if not found:
        raise RuntimeError(f"未找到节点含 {NODE_HINT}")
    if not tap_text("连接此节点"):
        for n in parse_nodes():
            if NODE_HINT in n["text"] and tap_bounds(n["bounds"]):
                break
        time.sleep(0.8)
        tap_text("连接此节点") or tap_text("一键连接")
    log("tapped connect")


def shot(name: str) -> Path:
    remote = f"/sdcard/{name}.png"
    local = SHOT_DIR / f"{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(local))
    return local


def analyze(logcat: str) -> dict[str, object]:
    keys = [
        "handshake_wait",
        "handshake_ready",
        "handshake_timeout",
        "entry_unreachable",
        "domestic_probe_fail",
        "post_connect_policy",
        "overseas_tz=true",
        "回国隧道基础连通性失败",
        "隧道探测通过",
    ]
    hits = {k: (k in logcat) for k in keys}
    samples = [
        ln
        for ln in logcat.splitlines()
        if any(
            x in ln
            for x in (
                "handshake_",
                "overseas_tz",
                "post_connect_policy",
                "entry_unreachable",
                "domestic_probe",
                "VpnDiag",
                "AppDebug",
            )
        )
    ]
    hits["sample_lines"] = samples[-30:]
    return hits


def main() -> int:
    OUT.write_text("", encoding="utf-8")
    log("=== Myanmar return verify start ===")
    if "device" not in (adb("devices").stdout or ""):
        log("无模拟器")
        return 2

    tz_info = read_timezone()
    log(f"timezone {tz_info}")
    yangon = "Yangon" in tz_info or "Asia/Yangon" in tz_info
    if not yangon:
        log(
            "WARN: 系统时区不是 Asia/Yangon。"
            "请用: emulator -avd Pixel_10 -timezone Asia/Yangon -no-snapshot-load"
        )

    apk = find_apk()
    log(f"apk={apk.name}")
    clear_and_install(apk)
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.6)

    login()
    time.sleep(0.8)
    tap_text("确定", "允许", "OK", "Allow")
    connect_wuhu()
    wait_text("已保护", "已连接", "连接失败", "不可达", "入口", "握手", timeout=100)
    path = shot("mm_return_result")
    texts = sorted(all_text())
    joined = " ".join(texts)
    log(f"shot={path}")
    log(f"ui_texts={texts[:55]}")

    time.sleep(3)
    raw = adb("logcat", "-d", "-t", "5000", timeout=60).stdout or ""
    hits = analyze(raw)
    log(f"log_hits={[k for k,v in hits.items() if v and k!='sample_lines']}")
    for ln in hits.get("sample_lines") or []:
        log(f"  LOG {ln[:260]}")

    gated = bool(hits.get("handshake_wait") or hits.get("post_connect_policy"))
    overseas_log = bool(hits.get("overseas_tz=true") or hits.get("handshake_wait"))
    connected = ("已保护" in joined or "已连接" in joined) and "连接失败" not in joined
    failed = "连接失败" in joined or "不可达" in joined or "入口" in joined

    log(
        f"RESULT yangon_tz={yangon} gated={gated} overseas_log={overseas_log} "
        f"connected={connected} failed_ui={failed}"
    )

    if not yangon:
        # 时区未切到仰光时，只能验证安装/登录/点连接，不能证明海外门控
        log("FAIL: 时区未设为仰光，海外门控不会触发")
        return 1
    if connected and (gated or overseas_log):
        log("PASS: 缅甸时区下回国连接成功，门控路径有日志")
        return 0
    if failed and (hits.get("entry_unreachable") or hits.get("handshake_timeout") or hits.get("domestic_probe_fail") or gated):
        log("PASS-PARTIAL: 门控/分类已触发（模拟器出口非缅甸时连接失败可接受）")
        return 0
    if gated:
        log("PASS-PARTIAL: 已进入门控，UI 结果待确认")
        return 0
    log("FAIL: 未观测到海外回国门控")
    return 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        log(f"ERROR {e}")
        raise
