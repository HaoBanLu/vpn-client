# -*- coding: utf-8 -*-
"""Verify nodes page does not show fake 1ms, and connect uses 响应."""
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
SHOT = Path(__file__).resolve().parent / "_ui_verify_nodes_latency.png"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr or p.stdout)
    return (p.stdout or "") + (p.stderr or "")


def main() -> int:
    import websocket

    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", check=False)
    time.sleep(5)
    pid = ""
    for _ in range(30):
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
        for _ in range(160):
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

    # clear bogus latency cache
    ev("sessionStorage.removeItem('ky_entry_latency_by_node_id')")

    ev(
        """
        (() => {
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === '节点');
          if (btn) btn.click(); else location.hash = '#/nodes';
        })()
        """
    )
    time.sleep(4)

    nodes = ev(
        """
        (() => {
          const vals = [...document.querySelectorAll('.ky-node-row__latency')].map(el => (el.textContent||'').replace(/\\s+/g,'').trim());
          return {
            vals,
            fakeLow: vals.filter(v => /^(1|2|3|4|5|6|7)ms/.test(v)),
            dashOrPending: vals.filter(v => v === '—' || v === '…' || v === ''),
            fastest: vals.filter(v => v.includes('最快')),
            nodeCount: document.querySelectorAll('.ky-node-row').length,
          };
        })()
        """
    )
    print("nodes=", json.dumps(nodes, ensure_ascii=True))
    with open(SHOT, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)

    # connect page wording
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
    text = ev("document.body.innerText || ''") or ""
    connect = {
        "hasEntry": "入口" in text,
        "hasLatencyWord": "延迟" in text,
        "hasResponse": bool(re.search(r"响应\s*\d+ms", text)),
        "protected": "已保护" in text,
        "snippet": text[:220],
    }
    print("connect=", json.dumps(connect, ensure_ascii=True))
    ws.close()

    fails = 0
    if (nodes or {}).get("fakeLow"):
        print("FAIL fake low ms still shown:", nodes.get("fakeLow"))
        fails += 1
    else:
        print("PASS nodes hide <8ms noise")
    if connect.get("hasLatencyWord"):
        print("FAIL connect still has 延迟")
        fails += 1
    else:
        print("PASS connect no 延迟 word")
    if connect.get("hasEntry"):
        print("FAIL connect still has 入口")
        fails += 1
    if connect.get("protected") and not connect.get("hasResponse"):
        print("WARN protected but no 响应 yet")
    elif connect.get("hasResponse"):
        print("PASS connect shows 响应 xxms")
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())
