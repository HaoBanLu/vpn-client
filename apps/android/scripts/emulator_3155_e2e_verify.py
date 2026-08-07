#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模拟器验收 3.15.5：登录→连接→飞行模式保持→恢复自愈；抓崩溃。"""

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
OUT = SHOT / "_3155_e2e_report.txt"


def adb(*args: str, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run([str(ADB), *args], capture_output=True, text=True, timeout=timeout, errors="replace")


def log(msg: str) -> None:
    sys.stdout.buffer.write((msg + "\n").encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


def dump_xml() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = SHOT / "_ui_e2e.xml"
    adb("pull", "/sdcard/ui.xml", str(local))
    if local.is_file():
        return local.read_text(encoding="utf-8", errors="replace")
    raw = subprocess.run([str(ADB), "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, timeout=60)
    return raw.stdout.decode("utf-8", errors="replace")


def parse_nodes() -> list[dict[str, str]]:
    xml = dump_xml()
    i = xml.find("<hierarchy")
    if i >= 0:
        xml = xml[i:]
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    nodes = []
    for n in root.iter("node"):
        nodes.append(
            {
                "text": (n.get("text") or "").strip(),
                "bounds": n.get("bounds") or "",
                "class": n.get("class") or "",
            }
        )
    return nodes


def all_text() -> set[str]:
    return {n["text"] for n in parse_nodes() if n["text"]}


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
    adb("shell", "input", "tap", str(c[0]), str(c[1]))
    return True


def tap_text(*labels: str, contains: bool = False) -> bool:
    for n in parse_nodes():
        t = n["text"]
        for lab in labels:
            if (lab in t) if contains else (t == lab):
                if tap_bounds(n["bounds"]):
                    return True
    return False


def wait_any(*cands: str, timeout: float = 40) -> str | None:
    end = time.time() + timeout
    while time.time() < end:
        texts = all_text()
        for c in cands:
            if any(c in t for t in texts):
                return c
        time.sleep(0.8)
    return None


def shot(name: str) -> None:
    remote = f"/sdcard/{name}.png"
    local = SHOT / f"{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(local))
    log(f"shot {local}")


def input_literal(text: str) -> None:
    buf: list[str] = []
    for ch in text:
        if ch == "@":
            if buf:
                adb("shell", "input", "text", "".join(buf))
                buf.clear()
            adb("shell", "input", "keyevent", "77")
        else:
            buf.append(ch)
    if buf:
        adb("shell", "input", "text", "".join(buf))


def clear_edit() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(100):
        adb("shell", "input", "keyevent", "67")


def dismiss() -> None:
    for lab in ("知道了", "Allow", "允许", "OK", "确定", "同意"):
        if tap_text(lab, contains=True):
            time.sleep(0.4)


def grant_vpn() -> None:
    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")


def on_main() -> bool:
    texts = " ".join(all_text())
    return any(x in texts for x in ("未连接", "一键连接", "已保护")) and "欢迎回来" not in texts


def login() -> None:
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    for _ in range(4):
        dismiss()
        time.sleep(0.3)
    if on_main():
        log("already main")
        return

    for attempt in range(3):
        dismiss()
        edits = [n for n in parse_nodes() if "EditText" in n["class"]]
        if len(edits) < 2:
            raise RuntimeError("no EditText")
        tap_bounds(edits[0]["bounds"])
        time.sleep(0.2)
        clear_edit()
        input_literal(EMAIL)
        time.sleep(0.3)
        # verify email field not comcom
        edits2 = [n for n in parse_nodes() if "EditText" in n["class"]]
        email_now = edits2[0]["text"] if edits2 else ""
        if email_now and email_now != EMAIL and "comcom" in email_now:
            tap_bounds(edits2[0]["bounds"])
            clear_edit()
            input_literal(EMAIL)
        tap_bounds(edits[1]["bounds"] if len(edits) > 1 else edits2[1]["bounds"])
        time.sleep(0.2)
        clear_edit()
        input_literal(PASSWORD)
        time.sleep(0.25)
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.4)
        tap_text("登录")
        time.sleep(3)
        dismiss()
        if wait_any("未连接", "一键连接", "已保护", "节点", timeout=30) and on_main():
            log(f"login ok attempt={attempt}")
            return
        log(f"login retry {attempt}")
    shot("login_fail")
    raise RuntimeError("login failed")


def connect_vpn() -> bool:
    dismiss()
    if "已保护" in joined() or "已连接" in joined():
        return True
    if not (tap_text("一键连接") or tap_text("连接")):
        tap_text("节点")
        time.sleep(1.2)
        for _ in range(10):
            if any("新加坡" in t for t in all_text()):
                break
            adb("shell", "input", "swipe", "540", "1700", "540", "800", "300")
            time.sleep(0.35)
        for n in parse_nodes():
            if "新加坡" in n["text"]:
                tap_bounds(n["bounds"])
                time.sleep(0.8)
                break
        tap_text("连接此节点") or tap_text("连接", contains=True)

    for i in range(36):
        dismiss()
        j = joined()
        if any(x in j for x in ("Connection request", "要设置", "VPN 连接请求", "VPN connection")):
            tap_text("OK", "允许", "确定", contains=True)
            time.sleep(1)
            continue
        if any(x in j for x in ("已保护", "已连接", "断开连接")):
            log(f"connected i={i}")
            return True
        if any(x in j for x in ("连接失败", "不可达", "无响应", "数据面未生效", "隧道数据面")):
            log(f"connect fail ui")
            return False
        time.sleep(2)
    log("connect timeout")
    return False


def svc() -> bool:
    return "VpnTunnelService" in (adb("shell", "dumpsys", "activity", "services", PKG).stdout or "")


def airplane(on: bool) -> None:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(2)


def main() -> int:
    lines: list[str] = []

    def rec(s: str) -> None:
        lines.append(s)
        log(s)

    ver = adb("shell", "dumpsys", "package", PKG).stdout or ""
    vc = re.search(r"versionCode=(\d+)", ver)
    vn = re.search(r"versionName=([^\s]+)", ver)
    rec(f"version={vn.group(1) if vn else '?'} code={vc.group(1) if vc else '?'}")
    if not (vc and vc.group(1) == "50"):
        rec("FAIL need 50")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    grant_vpn()
    airplane(False)
    adb("logcat", "-c")
    try:
        login()
    except Exception as e:
        rec(f"LOGIN_ERROR {e}")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 2

    shot("e2e_01_logged_in")
    connected = connect_vpn()
    shot("e2e_02_connect")
    rec(f"connected={connected} svc={svc()}")
    if not connected:
        raw = adb("logcat", "-d", "-t", "250").stdout or ""
        for ln in raw.splitlines():
            if any(k in ln for k in ("FATAL", "开始连接", "dataplane", "隧道", "Exception", "连接失败")):
                rec("  " + ln[:220])

    adb("logcat", "-c")
    rec("airplane ON 28s")
    airplane(True)
    time.sleep(28)
    shot("e2e_03_air_on")
    offline = adb("logcat", "-d", "-t", "800").stdout or ""
    svc_off = svc()
    tear = ("DISCONNECT_FOR_RECONNECT" in offline) or (
        "自动重连" in offline and "物理网不可用" not in offline and "推迟自动重连" not in offline
    )
    wait_log = any(k in offline for k in ("物理网不可用", "推迟自动重连", "保持隧道等待"))
    fatal_off = "FATAL EXCEPTION" in offline
    rec(f"offline svc={svc_off} tear={tear} wait_log={wait_log} fatal={fatal_off}")
    for ln in offline.splitlines():
        if any(k in ln for k in ("物理网", "自动重连", "自愈", "DISCONNECT", "FATAL", "保持隧道")):
            rec("  " + ln[:240])

    adb("logcat", "-c")
    rec("airplane OFF 40s")
    airplane(False)
    time.sleep(40)
    shot("e2e_04_air_off")
    restore = adb("logcat", "-d", "-t", "900").stdout or ""
    svc_on = svc()
    heal = any(
        k in restore
        for k in ("物理网络切换", "隧道自愈", "自动重连", "切网后探测", "network_restored", "开始连接")
    )
    fatal_on = "FATAL EXCEPTION" in restore
    rec(f"restore svc={svc_on} heal={heal} fatal={fatal_on}")
    rec(f"ui={joined()[:350]}")
    for ln in restore.splitlines():
        if any(k in ln for k in ("物理网", "自动重连", "自愈", "切网", "探测", "FATAL", "开始连接")):
            rec("  " + ln[:240])

    pass_crash = not fatal_off and not fatal_on
    if connected:
        pass_offline = bool(svc_off) and not tear and not fatal_off
        pass_restore = not fatal_on and (heal or svc_on)
        overall = pass_crash and pass_offline and pass_restore
    else:
        overall = False
        pass_offline = not tear and not fatal_off
        pass_restore = not fatal_on

    rec("")
    rec(f"connect: {'PASS' if connected else 'FAIL'}")
    rec(f"offline_keep: {'PASS' if pass_offline else 'FAIL'}")
    rec(f"restore: {'PASS' if pass_restore else 'FAIL'}")
    rec(f"no_crash: {'PASS' if pass_crash else 'FAIL'}")
    rec(f"OVERALL: {'PASS' if overall else 'FAIL'}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    log(f"report {OUT}")
    return 0 if overall else 2


if __name__ == "__main__":
    raise SystemExit(main())
