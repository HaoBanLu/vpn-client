#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""3.16.1 模拟器验收：必须回到「已保护」，不以连接中/KS 文案冒充成功。"""

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
OUT = SHOT / "_3161_e2e_report.txt"
UI = SHOT / "_ui_3161.xml"
LOGF = SHOT / "_3161_logcat.txt"


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


def texts() -> list[str]:
    return sorted({m for m in re.findall(r'text="([^"]+)"', ui_xml()) if m})


def has_protected() -> bool:
    t = ui_xml()
    return "已保护" in t


def has_fail() -> bool:
    t = ui_xml()
    return "连接失败" in t or "连接超时" in t


def svc() -> bool:
    return "VpnTunnelService" in (adb("shell", "dumpsys", "activity", "services", PKG).stdout or "")


def session() -> str:
    return adb("shell", "run-as", PKG, "cat", "shared_prefs/vpn_session_store.xml").stdout or ""


def airplane(on: bool) -> None:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(2)


def ensure_home() -> None:
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    tap(127, 2256)
    time.sleep(1)


def connect_until_protected(timeout_s: float = 90) -> bool:
    ensure_home()
    if has_protected():
        return True
    # 失败页点重试
    blob = " ".join(texts())
    if "重试连接" in blob:
        tap(540, 2050)
    elif "一键连接" in blob or "未连接" in blob or "连接失败" in blob:
        tap(540, 703)
        time.sleep(2.5)
        if "节点选择" in " ".join(texts()) or "新加坡" in " ".join(texts()):
            tap(934, 1052)
    end = time.time() + timeout_s
    i = 0
    while time.time() < end:
        time.sleep(2)
        i += 1
        ok = has_protected()
        fail = has_fail()
        if i % 3 == 0:
            log(f"connect {i}: protected={ok} fail={fail} texts={texts()[:8]}")
        if ok:
            return True
        if fail and i > 5:
            # 再点重试一次
            tap(540, 2050)
            time.sleep(2)
    return has_protected()


def main() -> int:
    lines: list[str] = []

    def rec(s: str) -> None:
        lines.append(s)
        log(s)

    ver = adb("shell", "dumpsys", "package", PKG).stdout or ""
    vc = re.search(r"versionCode=(\d+)", ver)
    vn = re.search(r"versionName=([^\s]+)", ver)
    rec(f"version={vn.group(1) if vn else '?'} code={vc.group(1) if vc else '?'}")
    if not (vc and vc.group(1) == "54"):
        rec("FAIL need 3.16.1 / 54")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    adb("shell", "appops", "set", PKG, "ACTIVATE_VPN", "allow")
    airplane(False)
    adb("shell", "svc", "wifi", "enable")
    time.sleep(2)

    # 清掉上次失败态：强制停再开
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)

    connected = connect_until_protected(100)
    shot("3161_01_connected")
    rec(f"connected={connected} svc={svc()} texts={texts()[:12]}")
    if not connected:
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 3

    sess_before = session()
    rec(f"session_before={sess_before.replace(chr(10), ' ')[:240]}")

    adb("logcat", "-c")
    logcat = subprocess.Popen(
        [str(ADB), "logcat", "-v", "time"],
        stdout=LOGF.open("w", encoding="utf-8", errors="replace"),
        stderr=subprocess.DEVNULL,
    )

    try:
        rec(">> airplane ON 18s")
        airplane(True)
        time.sleep(18)
        shot("3161_02_air_on")
        svc_off = svc()
        tear = "DISCONNECT_FOR_RECONNECT" in (adb("logcat", "-d", "-t", "50").stdout or "")
        rec(f"offline svc={svc_off} texts={texts()[:10]}")

        rec(">> airplane OFF，最多等 90s 到已保护")
        airplane(False)
        restored = False
        timeout_fail = False
        for i in range(45):
            time.sleep(2)
            ok = has_protected()
            fail = has_fail()
            if i % 3 == 0:
                log(f"restore {i}: protected={ok} fail={fail} sample={texts()[:6]}")
            if ok:
                restored = True
                break
            if fail and i >= 15:
                timeout_fail = True
                # 给自动重试留时间，不立刻退出
        shot("3161_03_restore")
    finally:
        logcat.terminate()
        try:
            logcat.wait(timeout=5)
        except Exception:
            logcat.kill()

    blob = LOGF.read_text(encoding="utf-8", errors="replace") if LOGF.is_file() else ""
    hits = {
        "onLost": blob.count("MihomoNetworkObserver") and blob.count("onLost"),
        "onAvailable": blob.count("onAvailable"),
        "vpn_network_ok": blob.count("vpn_network_ok"),
        "kill switch": blob.lower().count("kill switch"),
        "FATAL": blob.count("FATAL EXCEPTION"),
    }
    sess_after = session()
    att = re.search(r'reconnect_attempts" value="(\d+)"', sess_after)
    attempts = int(att.group(1)) if att else 0

    rec(f"restore protected={restored} timeout_ui={timeout_fail} svc={svc()}")
    rec(f"texts={texts()[:15]}")
    rec(f"log_hits={hits}")
    rec(f"session_after={sess_after.replace(chr(10), ' ')[:280]}")
    rec(f"reconnect_attempts={attempts}")

    overall = connected and restored and hits["FATAL"] == 0
    rec("")
    rec(f"connect: {'PASS' if connected else 'FAIL'}")
    rec(f"restore_to_protected: {'PASS' if restored else 'FAIL'}")
    rec(f"no_fatal: {'PASS' if hits['FATAL'] == 0 else 'FAIL'}")
    rec(f"OVERALL: {'PASS' if overall else 'FAIL'}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    log(f"report {OUT}")
    return 0 if overall else 2


if __name__ == "__main__":
    raise SystemExit(main())
