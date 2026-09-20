# -*- coding: utf-8 -*-
"""Emulator: VPN connected then nodes/packages must not show 网络异常."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PACKAGE = "com.vpn.kuayun"


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


class Cdp:
    def __init__(self) -> None:
        import websocket

        pid = adb("shell", "pidof", PACKAGE).strip().split()[0]
        adb("forward", "--remove-all", check=False)
        adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
        pages = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5).read().decode())
        page = next(p for p in pages if p.get("type") == "page" and p.get("webSocketDebuggerUrl"))
        try:
            self.ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=20, suppress_origin=True)
        except TypeError:
            self.ws = websocket.create_connection(
                page["webSocketDebuggerUrl"], timeout=20, header=["Origin: http://127.0.0.1:9222"]
            )
        self._id = 0

    def call(self, method: str, params: dict | None = None) -> dict:
        self._id += 1
        mid = self._id
        self.ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
        for _ in range(120):
            data = json.loads(self.ws.recv())
            if data.get("id") == mid:
                if "error" in data:
                    raise RuntimeError(data["error"])
                return data.get("result") or {}
        raise TimeoutError(method)

    def eval(self, expression: str):
        result = self.call(
            "Runtime.evaluate",
            {"expression": expression, "returnByValue": True, "awaitPromise": True},
        )
        if result.get("exceptionDetails"):
            raise RuntimeError(json.dumps(result["exceptionDetails"], ensure_ascii=False)[:400])
        return result.get("result", {}).get("value")

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass


def dismiss_vpn_dialog() -> None:
    # Common Pixel emulator VPN consent dialog OK
    for _ in range(3):
        xml_path = "/sdcard/_vpn_ui.xml"
        adb("shell", "uiautomator", "dump", xml_path, check=False)
        xml = adb("shell", "cat", xml_path, check=False)
        for label in ("确定", "允许", "OK", "Allow", "同意"):
            if label in xml:
                # try bounds parse
                import re

                m = re.search(rf'text="{label}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
                if not m:
                    m = re.search(rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="{label}"', xml)
                if m:
                    x = (int(m.group(1)) + int(m.group(3))) // 2
                    y = (int(m.group(2)) + int(m.group(4))) // 2
                    adb("shell", "input", "tap", str(x), str(y), check=False)
                    time.sleep(1.5)
                    return
        adb("shell", "input", "tap", "700", "1450", check=False)
        time.sleep(1.0)


def main() -> int:
    print("== VPN-on network check ==")
    cdp = Cdp()
    try:
        href = cdp.eval("location.href")
        print(f"  href={href}")
        if "login" in str(href).lower():
            print("FAIL need login first (run emulator_pull_fix_check.py)")
            return 1

        before = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Connect' });
              await new Promise(r => setTimeout(r, 1000));
              const text = document.body.innerText || '';
              return { protected: text.includes('已保护'), text: text.slice(0, 220) };
            })()
            """
        )
        print(f"  before={before}")

        if not (isinstance(before, dict) and before.get("protected")):
            clicked = cdp.eval(
                """
                (() => {
                  const btns = [...document.querySelectorAll('button')];
                  const b = btns.find(x => /一键连接|^\\s*连接\\s*$|开始/.test(x.textContent || ''));
                  if (!b) return 'no-btn';
                  b.click();
                  return 'clicked:' + (b.textContent || '').trim().slice(0, 24);
                })()
                """
            )
            print(f"  click={clicked}")
            time.sleep(3)
            dismiss_vpn_dialog()
            time.sleep(8)

        after = cdp.eval(
            """
            (() => {
              const text = document.body.innerText || '';
              return {
                protected: text.includes('已保护'),
                disconnect: text.includes('断开'),
                text: text.slice(0, 240),
              };
            })()
            """
        )
        print(f"  after={after}")

        nodes = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Nodes' });
              await new Promise(r => setTimeout(r, 4500));
              const text = document.body.innerText || '';
              return {
                hasError: text.includes('网络异常'),
                hasNodes: /日本|新加坡|中国|全部/.test(text),
                text: text.slice(0, 300),
              };
            })()
            """
        )
        print(f"  nodes={nodes}")

        pkgs = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Packages' });
              await new Promise(r => setTimeout(r, 3500));
              const text = document.body.innerText || '';
              return {
                hasError: text.includes('网络异常'),
                hasContent: /套餐|购买|流量/.test(text),
                text: text.slice(0, 240),
              };
            })()
            """
        )
        print(f"  packages={pkgs}")

        ok = True
        if isinstance(nodes, dict) and nodes.get("hasError") and not nodes.get("hasNodes"):
            print("FAIL N :: nodes network error while VPN likely on")
            ok = False
        elif isinstance(nodes, dict) and nodes.get("hasNodes"):
            print("PASS N :: nodes OK with VPN session")
        else:
            print(f"FAIL N :: unclear {nodes}")
            ok = False

        if isinstance(pkgs, dict) and pkgs.get("hasError") and not pkgs.get("hasContent"):
            print("FAIL K :: packages network error while VPN likely on")
            ok = False
        elif isinstance(pkgs, dict) and pkgs.get("hasContent"):
            print("PASS K :: packages OK with VPN session")
        else:
            print(f"FAIL K :: unclear {pkgs}")
            ok = False

        return 0 if ok else 1
    finally:
        cdp.close()


if __name__ == "__main__":
    sys.exit(main())
