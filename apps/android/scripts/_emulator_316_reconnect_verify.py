#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""3.16 断网恢复验收（少 dump，优先主页一键连接）。"""

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
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
SHOT = ROOT / "scripts" / "_emulator_shots"
SHOT.mkdir(parents=True, exist_ok=True)
OUT = SHOT / "_316_reconnect_report.txt"


def adb(*args: str, timeout: int = 45) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            [str(ADB), *args],
            capture_output=True,
            text=True,
            timeout=timeout,
            errors="replace",
        )
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess([str(ADB), *args], 124, "", "timeout")


def log(msg: str) -> None:
    sys.stdout.buffer.write((msg + "\n").encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


def dump_xml() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=30)
    local = SHOT / "_ui_316b.xml"
    p = adb("pull", "/sdcard/ui.xml", str(local), timeout=20)
    if local.is_file() and p.returncode == 0:
        return local.read_text(encoding="utf-8", errors="replace")
    return ""


def parse_nodes() -> list[dict[str, str]]:
    xml = dump_xml()
    i = xml.find("<hierarchy")
    if i >= 0:
        xml = xml[i:]
    if not xml:
        return []
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        out.append(
            {
                "text": (n.get("text") or "").strip(),
                "desc": (n.get("content-desc") or "").strip(),
                "bounds": n.get("bounds") or "",
                "class": n.get("class") or "",
                "clickable": n.get("clickable") or "",
            }
        )
    return out


def all_text() -> set[str]:
    s: set[str] = set()
    for n in parse_nodes():
        if n["text"]:
            s.add(n["text"])
        if n["desc"]:
            s.add(n["desc"])
    return s


def joined() -> str:
    return " | ".join(sorted(all_text()))


def center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    a, b, c, d = map(int, m.groups())
    return (a + c) // 2, (b + d) // 2


def tap_bounds(bounds: str) -> bool:
    c = center(bounds)
    if not c:
        return False
    adb("shell", "input", "tap", str(c[0]), str(c[1]), timeout=10)
    return True


def tap_exact(label: str) -> bool:
    for n in parse_nodes():
        if n["text"] == label or n["desc"] == label:
            return tap_bounds(n["bounds"])
    return False


def tap_contains(*labels: str) -> bool:
    for n in parse_nodes():
        t = n["text"] + " " + n["desc"]
        for lab in labels:
            if lab in t:
                return tap_bounds(n["bounds"])
    return False


def shot(name: str) -> None:
    remote = f"/sdcard/{name}.png"
    local = SHOT / f"{name}.png"
    adb("shell", "screencap", "-p", remote, timeout=20)
    adb("pull", remote, str(local), timeout=20)
    log(f"shot {local}")


def input_literal(text: str) -> None:
    buf: list[str] = []
    for ch in text:
        if ch == "@":
            if buf:
                adb("shell", "input", "text", "".join(buf), timeout=10)
                buf.clear()
            adb("shell", "input", "keyevent", "77", timeout=10)
        else:
            buf.append(ch)
    if buf:
        adb("shell", "input", "text", "".join(buf), timeout=10)


def clear_edit() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END", timeout=10)
    for _ in range(80):
        adb("shell", "input", "keyevent", "67", timeout=5)


def dismiss() -> None:
    for lab in ("知道了", "Allow", "允许", "OK", "确定", "同意", "以后再说"):
        if tap_contains(lab):
            time.sleep(0.3)


def grant_vpn() -> None:
    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow", timeout=15)
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", timeout=15)


def airplane(on: bool) -> None:
    adb(
        "shell",
        "cmd",
        "connectivity",
        "airplane-mode",
        "enable" if on else "disable",
        timeout=20,
    )
    time.sleep(2)


def svc() -> bool:
    return "VpnTunnelService" in (adb("shell", "dumpsys", "activity", "services", PKG, timeout=30).stdout or "")


def go_home_tab() -> None:
    # 底栏「连接」常在左侧；优先点 content-desc / text 精确匹配，避免点到节点行「连接」
    nodes = parse_nodes()
    for n in nodes:
        if n["text"] == "连接" and n.get("clickable") == "true":
            # 底栏 y 通常很大
            c = center(n["bounds"])
            if c and c[1] > 2000:
                tap_bounds(n["bounds"])
                time.sleep(1)
                return
    # 坐标兜底：Pixel 常见底栏第一项
    adb("shell", "input", "tap", "135", "2280", timeout=10)
    time.sleep(1)


def ensure_login() -> None:
    adb("shell", "am", "force-stop", PKG, timeout=15)
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", timeout=15)
    time.sleep(4)
    dismiss()
    texts = all_text()
    blob = " ".join(texts)
    if any(x in blob for x in ("未连接", "一键连接", "已保护", "节点选择")):
        log("already logged in")
        go_home_tab()
        return
    edits = [n for n in parse_nodes() if "EditText" in n["class"]]
    if len(edits) < 2:
        raise RuntimeError(f"login UI missing: {sorted(texts)[:20]}")
    tap_bounds(edits[0]["bounds"])
    clear_edit()
    input_literal(EMAIL)
    tap_bounds(edits[1]["bounds"])
    clear_edit()
    input_literal(PASSWORD)
    adb("shell", "input", "keyevent", "4", timeout=10)
    time.sleep(0.3)
    tap_exact("登录") or tap_contains("登录")
    time.sleep(4)
    dismiss()
    go_home_tab()


def connect_vpn() -> bool:
    go_home_tab()
    time.sleep(1)
    j = joined()
    if any(x in j for x in ("已保护", "已连接", "断开连接")):
        return True
    if not (tap_exact("一键连接") or tap_contains("一键连接")):
        # 主页大按钮可能只有「连接」且在中部
        for n in parse_nodes():
            if n["text"] == "连接":
                c = center(n["bounds"])
                if c and 800 < c[1] < 1800:
                    tap_bounds(n["bounds"])
                    break
    for i in range(36):
        dismiss()
        # 少 dump：隔几次才解析 UI
        if i % 2 == 0:
            j = joined()
            log(f"connect wait {i}: protected={'已保护' in j} fail={'失败' in j}")
            if any(x in j for x in ("Connection request", "VPN 连接请求", "要设置")):
                tap_contains("OK", "允许", "确定")
                continue
            if any(x in j for x in ("已保护", "已连接", "断开连接")):
                return True
            if any(x in j for x in ("连接失败", "数据面未生效", "隧道数据面")):
                return False
        else:
            time.sleep(2)
            continue
        time.sleep(2)
    return False


def main() -> int:
    lines: list[str] = []

    def rec(s: str) -> None:
        lines.append(s)
        log(s)

    ver = adb("shell", "dumpsys", "package", PKG, timeout=30).stdout or ""
    vc = re.search(r"versionCode=(\d+)", ver)
    vn = re.search(r"versionName=([^\s]+)", ver)
    rec(f"version={vn.group(1) if vn else '?'} code={vc.group(1) if vc else '?'}")
    if not (vc and vc.group(1) == "53"):
        rec("FAIL need versionCode 53")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    grant_vpn()
    airplane(False)
    adb("logcat", "-c", timeout=15)
    try:
        ensure_login()
    except Exception as e:
        rec(f"LOGIN_ERROR {e}")
        shot("316b_login_fail")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 2

    shot("316b_01_home")
    connected = connect_vpn()
    shot("316b_02_connect")
    rec(f"connected={connected} svc={svc()}")
    if not connected:
        raw = adb("logcat", "-d", "-t", "250", timeout=30).stdout or ""
        for ln in raw.splitlines():
            if any(k in ln for k in ("FATAL", "开始连接", "dataplane", "连接失败", "Exception", "SIGILL")):
                rec("  " + ln[:220])
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 3

    adb("logcat", "-c", timeout=15)
    rec(">> airplane ON 20s")
    airplane(True)
    time.sleep(20)
    shot("316b_03_air")
    offline = adb("logcat", "-d", "-t", "800", timeout=40).stdout or ""
    svc_off = svc()
    tear = "DISCONNECT_FOR_RECONNECT" in offline
    wait_log = any(k in offline for k in ("物理网不可用", "推迟自动重连", "保持隧道"))
    fatal_off = "FATAL EXCEPTION" in offline or "Fatal signal" in offline
    rec(f"offline svc={svc_off} tear={tear} wait={wait_log} fatal={fatal_off}")
    for ln in offline.splitlines():
        if any(k in ln for k in ("物理网", "自动重连", "自愈", "DISCONNECT", "准备完整", "network_")):
            rec("  " + ln[:240])

    adb("logcat", "-c", timeout=15)
    rec(">> airplane OFF 60s")
    airplane(False)
    restored = False
    for i in range(30):
        time.sleep(2)
        if i % 3 == 0:
            j = joined()
            log(f"restore {i}: {[k for k in ('已保护','自动重连','连接中','失败') if k in j]}")
            if "已保护" in j or "已连接" in j:
                restored = True
                break
    shot("316b_04_restore")
    restore = adb("logcat", "-d", "-t", "1200", timeout=40).stdout or ""
    svc_on = svc()
    auto_reconnect = "自动重连" in restore
    prepare = "准备完整重连" in restore
    heal = "隧道自愈" in restore
    start = "开始连接" in restore
    storm = prepare and not auto_reconnect
    fatal_on = "FATAL EXCEPTION" in restore or "Fatal signal" in restore
    rec(
        f"restore svc={svc_on} ui={restored} auto={auto_reconnect} prepare={prepare} start={start} heal={heal} storm={storm} fatal={fatal_on}"
    )
    for ln in restore.splitlines():
        if any(
            k in ln
            for k in (
                "物理网",
                "自动重连",
                "自愈",
                "准备完整",
                "开始连接",
                "network_restored",
                "跳过",
                "dns_changed",
                "FATAL",
            )
        ):
            rec("  " + ln[:240])

    pass_off = svc_off and not tear and not fatal_off
    pass_on = auto_reconnect and not storm and not heal and not fatal_on and (restored or (svc_on and start))
    overall = connected and pass_off and pass_on
    rec("")
    rec(f"connect: {'PASS' if connected else 'FAIL'}")
    rec(f"offline_keep: {'PASS' if pass_off else 'FAIL'}")
    rec(f"restore_full_reconnect: {'PASS' if pass_on else 'FAIL'}")
    rec(f"OVERALL: {'PASS' if overall else 'FAIL'}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    log(f"report {OUT}")
    return 0 if overall else 2


if __name__ == "__main__":
    raise SystemExit(main())
