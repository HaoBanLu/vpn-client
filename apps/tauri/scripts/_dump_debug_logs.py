# -*- coding: utf-8 -*-
import json
import subprocess
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")


def adb(*args: str) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (p.stdout or "") + (p.stderr or "")


def main() -> None:
    import websocket

    pid = adb("shell", "pidof", "com.vpn.kuayun").strip().split()[0]
    adb("forward", "--remove-all")
    adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
    pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
    page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
    try:
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20, suppress_origin=True)
    except TypeError:
        ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=20, header=["Origin: http://127.0.0.1:9222"]
        )
    mid = 0

    def ev(expr: str):
        nonlocal mid
        mid += 1
        ws.send(
            json.dumps(
                {
                    "id": mid,
                    "method": "Runtime.evaluate",
                    "params": {"expression": expr, "returnByValue": True, "awaitPromise": True},
                }
            )
        )
        while True:
            data = json.loads(ws.recv())
            if data.get("id") == mid:
                if data.get("result", {}).get("exceptionDetails"):
                    raise RuntimeError(str(data["result"]["exceptionDetails"])[:500])
                return data.get("result", {}).get("result", {}).get("value")

    raw = ev('localStorage.getItem("tauri_app_debug_logs")')
    if not raw:
        print("empty")
        return
    arr = json.loads(raw)
    for row in arr[-80:]:
        msg = row.get("msg") or row.get("message") or ""
        print(f"{row.get('ts','')} [{row.get('cat') or row.get('tag')}] {msg[:200]}")
    ws.close()


if __name__ == "__main__":
    main()
