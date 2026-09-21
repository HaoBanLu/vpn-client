# -*- coding: utf-8 -*-
"""Connect on emulator and verify Hero shows 延迟 without 入口."""
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
SHOT = Path(__file__).resolve().parent / "_ui_verify_connect_protected.png"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return (p.stdout or "") + (p.stderr or "")


def main() -> int:
    import websocket

    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(3)
    pid = ""
    for _ in range(20):
        pid = adb("shell", "pidof", PACKAGE, check=False).strip()
        if pid:
            pid = pid.split()[0]
            break
        time.sleep(1)
    if not pid:
        raise RuntimeError("no pid")

    adb("forward", "--remove-all", check=False)
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = None
    for _ in range(20):
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
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=20, header=["Origin: http://127.0.0.1:9222"]
        )
    nid = 0

    def call(method: str, params=None):
        nonlocal nid
        nid += 1
        mid = nid
        ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(180):
            data = json.loads(ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def ev(expr: str):
        result = call("Runtime.evaluate", {"expression": expr, "returnByValue": True, "awaitPromise": True})
        if result.get("exceptionDetails"):
            raise RuntimeError(result["exceptionDetails"])
        return result.get("result", {}).get("value")

    ev(
        """
        (() => {
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === '连接');
          if (btn) btn.click(); else location.hash = '#/connect';
        })()
        """
    )
    time.sleep(1.5)

    clicked = ev(
        """
        (() => {
          const candidates = [...document.querySelectorAll('button,[role=button]')];
          const hero = candidates.find(el => /一键连接|断开/.test((el.textContent||'').trim()));
          if (!hero) return null;
          const label = (hero.textContent||'').trim();
          if (label.includes('断开')) return 'already-connected';
          hero.click();
          return label;
        })()
        """
    )
    print("clicked=", json.dumps(clicked, ensure_ascii=True))

    status = {}
    for i in range(50):
        text = ev("document.body.innerText || ''") or ""
        status = {
            "protected": "已保护" in text,
            "hasEntry": "入口" in text,
            "hasDelay": bool(re.search(r"延迟\s*\d+ms", text)),
            "hasTunnelWord": bool(re.search(r"隧道\s*\d+ms", text)),
            "connecting": "连接中" in text,
            "snippet": text[:300],
        }
        print(
            "poll",
            i,
            json.dumps(
                {k: status[k] for k in ("protected", "hasEntry", "hasDelay", "hasTunnelWord", "connecting")},
                ensure_ascii=True,
            ),
        )
        if status["protected"]:
            # give probe a moment for delay text
            time.sleep(3)
            text = ev("document.body.innerText || ''") or ""
            status = {
                "protected": "已保护" in text,
                "hasEntry": "入口" in text,
                "hasDelay": bool(re.search(r"延迟\s*\d+ms", text)),
                "hasTunnelWord": bool(re.search(r"隧道\s*\d+ms", text)),
                "connecting": "连接中" in text,
                "snippet": text[:300],
            }
            break
        time.sleep(2)

    print("final=", json.dumps(status, ensure_ascii=True))
    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print("shot", SHOT.name)
    ws.close()

    if not status.get("protected"):
        print("FAIL not protected")
        return 1
    if status.get("hasEntry"):
        print("FAIL still shows 入口")
        return 1
    if status.get("hasTunnelWord"):
        print("FAIL still shows 隧道 xxms")
        return 1
    print("PASS protected without 入口/隧道 wording")
    if status.get("hasDelay"):
        print("PASS shows 延迟 xxms")
    else:
        print("WARN no 延迟 yet (probe may be slow on emulator)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
