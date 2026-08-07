#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""3.16.1 手动驱动验收：保持前台，严格以「已保护」判定。"""

from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path

ADB = Path(r"C:\Users\luban\AppData\Local\Android\Sdk\platform-tools\adb.exe")
PKG = "com.vpn.member"
SHOT = Path(__file__).resolve().parent / "_emulator_shots"
SHOT.mkdir(parents=True, exist_ok=True)
OUT = SHOT / "_3161_manual_report.txt"
UI = SHOT / "_ui_3161m.xml"


def adb(*args: str, timeout: int = 40) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            [str(ADB), *args],
            capture_output=True,
            text=True,
            timeout=timeout,
            errors="replace",
        )
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(list(args), 124, "", "timeout")


def log(msg: str) -> None:
    sys.stdout.buffer.write((msg + "\n").encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y), timeout=10)


def shot(name: str) -> None:
    remote = f"/sdcard/{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(SHOT / f"{name}.png"))
    log(f"shot {SHOT / (name + '.png')}")


def bring_front() -> None:
    adb(
        "shell",
        "am",
        "start",
        "-n",
        f"{PKG}/.MainActivity",
        "--activity-single-top",
        timeout=15,
    )
    time.sleep(1.2)


def ui() -> str:
    bring_front()
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=25)
    adb("pull", "/sdcard/ui.xml", str(UI), timeout=15)
    return UI.read_text(encoding="utf-8", errors="replace") if UI.is_file() else ""


def texts() -> list[str]:
    return sorted({m for m in re.findall(r'text="([^"]+)"', ui()) if m})


def protected() -> bool:
    return "已保护" in ui()


def airplane(on: bool) -> None:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(2.5)


def svc() -> bool:
    return "VpnTunnelService" in (adb("shell", "dumpsys", "activity", "services", PKG).stdout or "")


def main() -> int:
    lines: list[str] = []

    def rec(s: str) -> None:
        lines.append(s)
        log(s)

    ver = adb("shell", "dumpsys", "package", PKG).stdout or ""
    vn = re.search(r"versionName=([^\s]+)", ver)
    vc = re.search(r"versionCode=(\d+)", ver)
    rec(f"version={vn.group(1) if vn else '?'} code={vc.group(1) if vc else '?'}")

    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")
    airplane(False)
    adb("shell", "svc", "wifi", "enable")
    # 彻底停掉旧 KS/隧道
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.5)
    bring_front()
    time.sleep(2)
    tap(127, 2256)  # 连接 tab
    time.sleep(1)
    t0 = texts()
    rec(f"home texts={t0[:14]}")
    shot("3161m_00_home")

    if not protected():
        if any("重试连接" in x for x in t0):
            tap(540, 2050)
        else:
            tap(540, 703)  # 一键连接
            time.sleep(2.5)
            t1 = texts()
            rec(f"after one-click={t1[:12]}")
            if any("新加坡" in x or "节点选择" in x for x in t1):
                tap(934, 1052)
        for i in range(50):
            time.sleep(2)
            if i % 2 == 0:
                bring_front()
            ok = protected()
            ts = texts()
            if i % 4 == 0:
                log(f"connect {i}: ok={ok} sample={ts[:8]}")
            if ok:
                break
            if any("连接失败" in x or "连接超时" in x for x in ts) and i in (12, 24, 36):
                tap(540, 2050)

    ok_conn = protected()
    shot("3161m_01_conn")
    rec(f"connected={ok_conn} svc={svc()} texts={texts()[:12]}")
    if not ok_conn:
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 3

    adb("logcat", "-c")
    rec(">> airplane ON 15s")
    airplane(True)
    time.sleep(15)
    bring_front()
    shot("3161m_02_air")
    rec(f"air_on svc={svc()} texts={texts()[:10]}")

    rec(">> airplane OFF wait protected <=100s")
    airplane(False)
    restored = False
    for i in range(50):
        time.sleep(2)
        if i % 2 == 0:
            bring_front()
        ok = protected()
        ts = texts()
        if i % 3 == 0:
            log(f"restore {i}: ok={ok} sample={ts[:8]}")
        if ok:
            restored = True
            break
    shot("3161m_03_restore")
    sess = adb("shell", "run-as", PKG, "cat", "shared_prefs/vpn_session_store.xml").stdout or ""
    att = re.search(r'reconnect_attempts" value="(\d+)"', sess)
    rec(f"restored={restored} svc={svc()} attempts={att.group(1) if att else '?'} texts={texts()[:12]}")
    rec(f"session={sess.replace(chr(10), ' ')[:260]}")

    overall = ok_conn and restored
    rec("")
    rec(f"connect: {'PASS' if ok_conn else 'FAIL'}")
    rec(f"restore_protected: {'PASS' if restored else 'FAIL'}")
    rec(f"OVERALL: {'PASS' if overall else 'FAIL'}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    log(f"report {OUT}")
    return 0 if overall else 2


if __name__ == "__main__":
    raise SystemExit(main())
