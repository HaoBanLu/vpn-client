# -*- coding: utf-8 -*-
"""模拟器验证：连接页大按钮文案/图标；关于页更新流程不挡假进度。"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = "D:/Android/Sdk/platform-tools/adb.exe"
PACKAGE = "com.vpn.kuayun"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
SHOT_CONNECT = Path(__file__).resolve().parent / "_ui_verify_connect_button.png"
SHOT_ABOUT = Path(__file__).resolve().parent / "_ui_verify_about_update.png"

FOUR_CHAR_LABELS = {"一键连接", "断开连接", "正在连接", "正在重连", "正在切换", "购买套餐", "重新加载"}


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run(
        [ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return (p.stdout or "") + (p.stderr or "")


def cdp_session():
    try:
        import websocket
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
        import websocket

    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(5)

    pid = ""
    for _ in range(40):
        pid = adb("shell", "pidof", PACKAGE, check=False).strip().split()
        if pid:
            pid = pid[0]
            break
        time.sleep(1)
    if not pid:
        raise RuntimeError("no pid")

    adb("forward", "--remove-all", check=False)
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = None
    for _ in range(25):
        try:
            pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
            if any(p.get("type") == "page" and p.get("webSocketDebuggerUrl") for p in pages):
                break
        except Exception:
            pages = None
        time.sleep(1)
    if not pages:
        raise RuntimeError("cdp unavailable")

    page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
    try:
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=25, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=25, header=["Origin: http://127.0.0.1:9222"]
        )

    nid = 0

    def call(method: str, params=None):
        nonlocal nid
        nid += 1
        mid = nid
        ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(200):
            data = json.loads(ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def ev(expr: str):
        result = call(
            "Runtime.evaluate",
            {"expression": expr, "returnByValue": True, "awaitPromise": True},
        )
        if result.get("exceptionDetails"):
            raise RuntimeError(json.dumps(result["exceptionDetails"], ensure_ascii=False)[:500])
        return result.get("result", {}).get("value")

    def login_if_needed():
        body = (ev("document.body.innerText || ''") or "")[:400]
        if "登录" not in body and "邮箱" not in body:
            return
        print("  logging in…")
        ev(
            f"""
            (async () => {{
              const inputs = [...document.querySelectorAll('input')];
              const email = inputs.find(i => /email|邮箱|mail/i.test(i.type+i.placeholder+i.name));
              const pass = inputs.find(i => i.type === 'password');
              if (email) {{
                email.focus(); email.value = {json.dumps(EMAIL)};
                email.dispatchEvent(new Event('input', {{ bubbles: true }}));
              }}
              if (pass) {{
                pass.focus(); pass.value = {json.dumps(PASSWORD)};
                pass.dispatchEvent(new Event('input', {{ bubbles: true }}));
              }}
              const btn = [...document.querySelectorAll('button')].find(b => /登录|登 录/.test(b.textContent||''));
              if (btn) btn.click();
            }})()
            """
        )
        time.sleep(6)

    def go_connect():
        ev(
            """
            (() => {
              const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
                .find(el => (el.textContent||'').trim() === '连接');
              if (btn) btn.click(); else location.hash = '#/connect';
            })()
            """
        )
        time.sleep(2)

    def go_about():
        ev("location.hash = '#/profile/about'")
        time.sleep(2)

    return ws, ev, login_if_needed, go_connect, go_about


def main() -> int:
    print("== emulator connect + update UX verify ==")
    ver = adb("shell", "dumpsys", "package", PACKAGE, check=False)
    m = re.search(r"versionName=([^\s]+)", ver)
    print(f"  installed versionName={m.group(1) if m else '?'}")

    ws, ev, login_if_needed, go_connect, go_about = cdp_session()
    fails: list[str] = []

    try:
        login_if_needed()
        go_connect()

        connect = ev(
            """
            (() => {
              const btn = document.querySelector('.power-btn');
              const label = document.querySelector('.power-label');
              const svg = document.querySelector('.power-btn__svg');
              const spinner = document.querySelector('.power-btn__spinner');
              const iconBox = document.querySelector('.power-btn__icon');
              return {
                label: (label?.textContent || '').trim(),
                hasSvg: !!svg,
                hasSpinner: !!spinner,
                iconW: iconBox ? Math.round(iconBox.getBoundingClientRect().width) : 0,
                svgW: svg ? Math.round(svg.getBoundingClientRect().width) : 0,
                title: (document.querySelector('.connect-hero__title')?.textContent || '').trim(),
              };
            })()
            """
        )
        print("connect=", json.dumps(connect, ensure_ascii=False))

        label = (connect or {}).get("label", "")
        if label not in FOUR_CHAR_LABELS:
            fails.append(f"connect button label not 4-char unified: {label!r}")
        if not (connect or {}).get("hasSvg") and not (connect or {}).get("hasSpinner"):
            fails.append("connect button missing svg/spinner icon")
        icon_w = (connect or {}).get("iconW") or 0
        if icon_w < 40:
            fails.append(f"icon box too small: {icon_w}px")

        with open(SHOT_CONNECT, "wb") as f:
            subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
        print(f"  shot {SHOT_CONNECT.name}")

        go_about()
        about = ev(
            """
            (() => {
              const overlay = document.querySelector('.app-update-overlay');
              const progress = document.querySelector('.app-update-panel .ky-progress');
              const checkBtn = [...document.querySelectorAll('button')]
                .find(b => (b.textContent||'').includes('检查更新'));
              return {
                versionText: (document.body.innerText.match(/版本\\s*[\\d.]+/)||[])[0] || '',
                hasOverlay: !!overlay,
                hasProgressInPanel: !!progress,
                hasCheckBtn: !!checkBtn,
              };
            })()
            """
        )
        print("about=", json.dumps(about, ensure_ascii=False))

        if (about or {}).get("hasOverlay") and (about or {}).get("hasProgressInPanel"):
            fails.append("about page shows blocking update progress overlay unexpectedly")

        # 点检查更新：Android 不应立刻弹出带进度条的浮层（DownloadManager 走通知栏）
        ev(
            """
            (() => {
              const btn = [...document.querySelectorAll('button')]
                .find(b => (b.textContent||'').includes('检查更新'));
              if (btn && !btn.disabled) btn.click();
            })()
            """
        )
        time.sleep(3)
        after_check = ev(
            """
            (() => {
              const overlay = document.querySelector('.app-update-overlay');
              const progress = overlay ? overlay.querySelector('.ky-progress') : null;
              const phaseText = overlay ? (overlay.textContent||'').slice(0, 120) : '';
              return {
                overlayVisible: !!overlay,
                hasProgress: !!progress,
                overlaySnippet: phaseText.replace(/\\s+/g, ' ').trim(),
              };
            })()
            """
        )
        print("after_check_update=", json.dumps(after_check, ensure_ascii=False))

        # 有新版本时：prompt 可以；downloading+progress 不应出现
        if (after_check or {}).get("hasProgress"):
            fails.append("check update shows in-app progress bar (should use DownloadManager notification)")

        with open(SHOT_ABOUT, "wb") as f:
            subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
        print(f"  shot {SHOT_ABOUT.name}")

    finally:
        ws.close()

    if fails:
        for f in fails:
            print("FAIL", f)
        return 1
    print("PASS connect icon/label + update UX (no fake progress overlay)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
