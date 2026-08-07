#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""坐标驱动：连上节点 → 飞行模式 → 恢复，验收 3.16 自动重连。"""

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
OUT = SHOT / "_316_reconnect_report.txt"
UI = SHOT / "_ui_live.xml"


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


def ui_xml() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=25)
    adb("pull", "/sdcard/ui.xml", str(UI), timeout=15)
    if UI.is_file():
        return UI.read_text(encoding="utf-8", errors="replace")
    return ""


def ui_has(*keys: str) -> bool:
    t = ui_xml()
    return any(k in t for k in keys)


def ui_texts() -> list[str]:
    return sorted({m for m in re.findall(r'text="([^"]+)"', ui_xml()) if m})


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

    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")
    airplane(False)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    tap(127, 2256)  # 底栏连接
    time.sleep(1)

    if not ui_has("已保护", "已连接", "断开连接"):
        # 一键连接圆形按钮
        tap(540, 703)
        time.sleep(2.5)
        if ui_has("节点选择", "新加坡"):
            # 首个节点行「连接」按钮中心
            tap(934, 1052)
            time.sleep(1)
        connected = False
        for i in range(45):
            time.sleep(2)
            if i % 2 == 0 and ui_has("Connection request", "VPN 连接请求", "要设置"):
                tap(780, 1450)
                time.sleep(1)
            if i % 3 == 0:
                has = ui_has("已保护", "已连接", "断开连接")
                fail = ui_has("连接失败", "数据面未生效")
                log(f"connect wait {i} ok={has} fail={fail}")
                if has:
                    connected = True
                    break
                if fail:
                    break
        if not connected:
            # 再试一次点节点连接
            tap(934, 1052)
            for i in range(30):
                time.sleep(2)
                if ui_has("已保护", "已连接", "断开连接"):
                    connected = True
                    break
    else:
        connected = True

    shot("316c_02_connect")
    rec(f"connected={connected} svc={svc()} texts={ui_texts()[:18]}")
    if not connected:
        raw = adb("logcat", "-d", "-t", "200").stdout or ""
        for ln in raw.splitlines():
            if any(k in ln for k in ("开始连接", "连接失败", "dataplane", "FATAL", "Exception")):
                rec("  " + ln[:220])
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 3

    adb("logcat", "-c")
    rec(">> airplane ON 20s")
    airplane(True)
    time.sleep(20)
    shot("316c_03_air")
    offline = adb("logcat", "-d", "-t", "900").stdout or ""
    svc_off = svc()
    tear = "DISCONNECT_FOR_RECONNECT" in offline
    wait_log = any(k in offline for k in ("物理网不可用", "推迟自动重连", "保持隧道"))
    fatal_off = "FATAL EXCEPTION" in offline or "Fatal signal" in offline
    rec(f"offline svc={svc_off} tear={tear} wait={wait_log} fatal={fatal_off}")
    for ln in offline.splitlines():
        if any(k in ln for k in ("物理网", "自动重连", "自愈", "DISCONNECT", "准备完整", "network_")):
            rec("  " + ln[:240])

    adb("logcat", "-c")
    rec(">> airplane OFF 65s")
    airplane(False)
    restored = False
    for i in range(33):
        time.sleep(2)
        if i % 3 == 0:
            ok = ui_has("已保护", "已连接")
            log(f"restore {i} ui_ok={ok}")
            if ok:
                restored = True
                break
    shot("316c_04_restore")
    restore = adb("logcat", "-d", "-t", "1200").stdout or ""
    svc_on = svc()
    auto = "自动重连" in restore
    prepare = "准备完整重连" in restore
    heal = "隧道自愈" in restore
    start = "开始连接" in restore
    storm = prepare and not auto
    fatal_on = "FATAL EXCEPTION" in restore or "Fatal signal" in restore
    rec(
        f"restore svc={svc_on} ui={restored} auto={auto} prepare={prepare} start={start} heal={heal} storm={storm} fatal={fatal_on}"
    )
    rec(f"texts={ui_texts()[:20]}")
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
    pass_on = auto and not storm and not heal and not fatal_on and (restored or (svc_on and start))
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
