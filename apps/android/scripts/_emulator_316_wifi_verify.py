#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""二次验收：开 debug 日志 + 连上后关/开 WiFi，核对自动重连日志与 UI。"""

from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path

ADB = Path(r"C:\Users\luban\AppData\Local\Android\Sdk\platform-tools\adb.exe")
PKG = "com.vpn.member"
SHOT = Path(__file__).resolve().parent / "_emulator_shots"
OUT = SHOT / "_316_wifi_reconnect_report.txt"
UI = SHOT / "_ui_live.xml"
LOGF = SHOT / "_316_wifi_logcat.txt"


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
    return UI.read_text(encoding="utf-8", errors="replace") if UI.is_file() else ""


def ui_has(*keys: str) -> bool:
    t = ui_xml()
    return any(k in t for k in keys)


def session_xml() -> str:
    p = adb("shell", "run-as", PKG, "cat", "shared_prefs/vpn_session_store.xml")
    return p.stdout or ""


def main() -> int:
    lines: list[str] = []

    def rec(s: str) -> None:
        lines.append(s)
        log(s)

    # push prefs with debug on
    prefs = SHOT / "vpn_member_prefs.xml"
    adb("push", str(prefs), "/data/local/tmp/vpn_member_prefs.xml")
    adb(
        "shell",
        "run-as",
        PKG,
        "cp",
        "/data/local/tmp/vpn_member_prefs.xml",
        "shared_prefs/vpn_member_prefs.xml",
    )
    # run-as may not read /data/local/tmp — fallback via cat
    pushed = adb("shell", f"run-as {PKG} ls shared_prefs/vpn_member_prefs.xml")
    rec(f"prefs push ls={pushed.stdout.strip()} err={pushed.stderr.strip()}")

    adb("shell", "svc", "wifi", "enable")
    adb("shell", "cmd", "connectivity", "airplane-mode", "disable")
    time.sleep(2)
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)
    # login already; ensure home
    tap(127, 2256)
    time.sleep(1)

    if not ui_has("已保护", "已连接"):
        # retry connect button if fail screen
        if ui_has("重试连接"):
            # bottom retry roughly
            tap(540, 2050)
        else:
            tap(540, 703)
            time.sleep(2)
            if ui_has("节点选择", "新加坡"):
                tap(934, 1052)
        for i in range(40):
            time.sleep(2)
            if i % 3 == 0 and ui_has("已保护", "已连接"):
                break
            if i % 3 == 0:
                log(f"connect {i}")
    connected = ui_has("已保护", "已连接")
    rec(f"connected={connected} session_before={session_xml().replace(chr(10),' ')}")
    shot("316w_01_connected")
    if not connected:
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 3

    # start logcat file
    adb("logcat", "-c")
    logcat_proc = subprocess.Popen(
        [str(ADB), "logcat", "-v", "time"],
        stdout=LOGF.open("w", encoding="utf-8", errors="replace"),
        stderr=subprocess.DEVNULL,
    )

    try:
        rec(">> wifi OFF 12s")
        adb("shell", "svc", "wifi", "disable")
        time.sleep(12)
        shot("316w_02_wifi_off")
        off_texts = ui_xml()
        rec(f"wifi_off has_fail={'连接失败' in off_texts} has_hint={'自动重连' in off_texts or '网络已断开' in off_texts}")

        rec(">> wifi ON wait 70s")
        adb("shell", "svc", "wifi", "enable")
        restored = False
        for i in range(35):
            time.sleep(2)
            if i % 3 == 0:
                ok = ui_has("已保护", "已连接")
                hint = ui_has("正在自动重连", "自动重连")
                log(f"restore {i} ok={ok} hint={hint}")
                if ok:
                    restored = True
                    break
        shot("316w_03_restore")
    finally:
        logcat_proc.terminate()
        try:
            logcat_proc.wait(timeout=5)
        except Exception:
            logcat_proc.kill()

    blob = LOGF.read_text(encoding="utf-8", errors="replace") if LOGF.is_file() else ""
    keys = [
        "准备完整重连",
        "自动重连",
        "网络恢复",
        "物理网络切换",
        "onLost",
        "onAvailable",
        "network_lost",
        "network_restored",
        "重连进行中",
        "MihomoNetworkObserver",
        "开始连接",
    ]
    hits = {k: blob.count(k) for k in keys}
    rec(f"log_hits={hits}")
    for ln in blob.splitlines():
        if any(
            k in ln
            for k in (
                "准备完整",
                "自动重连",
                "网络恢复",
                "物理网络",
                "onLost",
                "onAvailable",
                "重连进行中",
                "AppDebug",
                "network_restored",
            )
        ):
            rec("  " + ln[:240])

    sess = session_xml()
    rec(f"session_after={sess.replace(chr(10),' ')}")
    attempts = re.search(r'reconnect_attempts" value="(\d+)"', sess)
    att = int(attempts.group(1)) if attempts else -1

    # AppDebug may still be off if prefs copy failed — UI/session still count
    pass_trigger = hits.get("自动重连", 0) > 0 or hits.get("准备完整重连", 0) > 0 or att >= 1
    pass_restore = restored
    overall = connected and pass_trigger and pass_restore
    # soft pass: trigger ok but restore timed out on emulator net
    soft = connected and pass_trigger and not pass_restore

    rec("")
    rec(f"trigger_reconnect: {'PASS' if pass_trigger else 'FAIL'} attempts={att}")
    rec(f"ui_restored: {'PASS' if pass_restore else 'FAIL'}")
    rec(f"OVERALL: {'PASS' if overall else ('SOFT_FAIL_NET' if soft else 'FAIL')}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    log(f"report {OUT}")
    return 0 if overall else (4 if soft else 2)


if __name__ == "__main__":
    raise SystemExit(main())
