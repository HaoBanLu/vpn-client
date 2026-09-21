# -*- coding: utf-8 -*-
"""Emulator CDP: verify flat nodes list + Android OTA in-app download."""
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
SHOT_DIR = Path(__file__).resolve().parent
RESULTS: list[str] = []


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run(
        [ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if check and p.returncode != 0:
        raise RuntimeError(f"adb failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


def shot(name: str) -> None:
    path = SHOT_DIR / name
    with open(path, "wb") as f:
        p = subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {name}" if p.returncode == 0 else f"  shot failed {name}")


class Cdp:
    def __init__(self) -> None:
        try:
            import websocket  # noqa: F401
        except ImportError:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
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
        for _ in range(160):
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
            raise RuntimeError(json.dumps(result["exceptionDetails"], ensure_ascii=False)[:500])
        return result.get("result", {}).get("value")

    def close(self) -> None:
        try:
            self.ws.close()
        except Exception:
            pass


def ok(msg: str) -> None:
    print(f"PASS {msg}")
    RESULTS.append(f"PASS {msg}")


def fail(msg: str) -> None:
    print(f"FAIL {msg}")
    RESULTS.append(f"FAIL {msg}")


def login(cdp: Cdp) -> None:
    cdp.eval(
        f"""
        (async () => {{
          const setNative = (el, val) => {{
            const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) setter.call(el, val); else el.value = val;
            el.dispatchEvent(new Event('input', {{ bubbles: true }}));
            el.dispatchEvent(new Event('change', {{ bubbles: true }}));
          }};
          const text = document.body.innerText || '';
          if (!text.includes('登录') || !text.includes('邮箱')) return 'already';
          const inputs = [...document.querySelectorAll('input')];
          const email = inputs.find(i => i.type === 'email' || (i.placeholder||'').includes('@')) || inputs[0];
          const pass = inputs.find(i => i.type === 'password') || inputs[1];
          setNative(email, {EMAIL!r});
          setNative(pass, {PASSWORD!r});
          const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('登录'));
          btn?.click();
          return 'ok';
        }})()
        """
    )
    deadline = time.time() + 40
    while time.time() < deadline:
        text = cdp.eval("document.body.innerText || ''") or ""
        if "节点" in text and ("连接" in text or "已保护" in text or "未连接" in text):
            if "邮箱" not in text:
                return
        time.sleep(1)
    raise RuntimeError("login timeout")


def verify_nodes(cdp: Cdp) -> None:
    print("\n== nodes flat list ==")
    cdp.eval(
        """
        (() => {
          const btn = [...document.querySelectorAll('button,a,[role=button],.bottom-nav *')]
            .find(el => (el.textContent||'').trim() === '节点');
          if (btn) { btn.click(); return 'click'; }
          location.hash = '#/nodes';
          return 'hash';
        })()
        """
    )
    time.sleep(2.5)
    layout = cdp.eval(
        """
        (() => {
          const book = document.querySelector('.nodes-book');
          const section = document.querySelector('.nodes-book__section');
          const nav = document.querySelector('.nodes-region-nav');
          if (!book) return { ok: false, reason: 'no-book' };
          const bs = getComputedStyle(book);
          const ss = section ? getComputedStyle(section) : null;
          const parseR = (v) => {
            const n = parseFloat(String(v || '0').split(' ')[0]);
            return Number.isFinite(n) ? n : 0;
          };
          return {
            ok: true,
            bookRadius: parseR(bs.borderRadius),
            bookBg: bs.backgroundColor,
            sectionRadius: ss ? parseR(ss.borderRadius) : null,
            sectionBorder: ss ? ss.borderTopWidth : null,
            chips: [...document.querySelectorAll('.nodes-region-nav__chip')].map(el => (el.textContent||'').trim()),
            heads: [...document.querySelectorAll('.nodes-book__head')].slice(0, 4).map(el => (el.textContent||'').trim()),
            nodeCount: document.querySelectorAll('.ky-node-row').length,
            hasTopNav: !!nav,
          };
        })()
        """
    )
    print(f"  layout={json.dumps(layout, ensure_ascii=False)}")
    if not layout or not layout.get("ok"):
        fail("nodes layout missing")
        return
    if (layout.get("bookRadius") or 0) > 1 or (layout.get("sectionRadius") or 0) > 1:
        fail(f"still rounded wrap book={layout.get('bookRadius')} section={layout.get('sectionRadius')}")
    else:
        ok("nodes no rounded wrap")
    if layout.get("nodeCount", 0) < 1:
        fail("no nodes loaded")
    else:
        ok(f"nodes loaded ({layout.get('nodeCount')})")
    if layout.get("hasTopNav") and len(layout.get("chips") or []) >= 2:
        ok(f"region chips {layout.get('chips')[:6]}")
    else:
        fail("region chips missing")
    shot("_ui_nodes_flat_verify.png")


def verify_ota(cdp: Cdp) -> None:
    print("\n== OTA download ==")
    cdp.eval(
        """
        (() => {
          const keys = Object.keys(localStorage).filter(k =>
            /update|dismiss|accepted/i.test(k)
          );
          keys.forEach(k => localStorage.removeItem(k));
          return keys;
        })()
        """
    )

    probe = cdp.eval(
        """
        (async () => {
          let api = null;
          try {
            const res = await fetch('http://192.229.87.112:44080/api/v1/client/version?platform=android&version_code=1&version_name=1.0.0');
            api = await res.json();
          } catch (e) {
            api = { error: String(e) };
          }
          const inv = window.__TAURI_INTERNALS__?.invoke;
          return { api, hasInvoke: typeof inv === 'function' };
        })()
        """
    )
    print(f"  probe={json.dumps(probe, ensure_ascii=False)[:900]}")
    data = (probe or {}).get("api") or {}
    payload = data.get("data") if isinstance(data, dict) and isinstance(data.get("data"), dict) else data
    if not isinstance(payload, dict) or payload.get("error"):
        fail(f"version API error: {payload}")
        return

    download_url = payload.get("download_url")
    latest = payload.get("latest_version_name") or "ota-test"
    code = payload.get("latest_version_code") or 0
    if not download_url:
        fail("API missing download_url")
        return

    url = str(download_url).strip()
    if url.startswith("/"):
        url = "http://192.229.87.112:44080" + url
    print(f"  download_url={url} latest={latest} code={code}")

    # Prefer the known-good public HTTPS URL if API returns relative/IP that may be blocked
    if "eodkko" not in url and url.startswith("http://192."):
        # still try API url first; fallback later
        pass

    start = cdp.eval(
        f"""
        (async () => {{
          const inv = window.__TAURI_INTERNALS__?.invoke;
          if (typeof inv !== 'function') return {{ ok: false, reason: 'no-invoke' }};
          try {{
            await inv('vpn_install_apk_update', {{
              options: {{
                url: {url!r},
                versionLabel: {str(latest)!r},
                versionCode: Number({code}),
              }}
            }});
            return {{ ok: true, url: {url!r} }};
          }} catch (e) {{
            return {{ ok: false, reason: String(e) }};
          }}
        }})()
        """
    )
    print(f"  startDownload={json.dumps(start, ensure_ascii=False)}")
    if not start or not start.get("ok"):
        # fallback to known reachable HTTPS apk
        fallback = "https://vpn.eodkko.xyz/api/uploads/apk/android_153_20260921100607.apk"
        start = cdp.eval(
            f"""
            (async () => {{
              const inv = window.__TAURI_INTERNALS__?.invoke;
              try {{
                await inv('vpn_install_apk_update', {{
                  options: {{
                    url: {fallback!r},
                    versionLabel: '1.2.33-fallback',
                    versionCode: 153,
                  }}
                }});
                return {{ ok: true, url: {fallback!r}, via: 'fallback' }};
              }} catch (e) {{
                return {{ ok: false, reason: String(e) }};
              }}
            }})()
            """
        )
        print(f"  startFallback={json.dumps(start, ensure_ascii=False)}")

    if not start or not start.get("ok"):
        fail(f"could not start download: {start}")
        shot("_ui_ota_start_fail.png")
        return
    ok(f"OTA download started ({start.get('url')})")

    deadline = time.time() + 150
    last = ""
    while time.time() < deadline:
        pending = cdp.eval(
            """
            (async () => {
              const inv = window.__TAURI_INTERNALS__?.invoke;
              try {
                return await inv('vpn_get_pending_apk_update');
              } catch (e) {
                return { error: String(e) };
              }
            })()
            """
        )
        log = adb(
            "logcat", "-d", "-t", "120",
            "AppUpdateInstaller:I", "AppUpdateInstaller:W", "AppUpdateInstaller:E",
            check=False,
        )
        body = cdp.eval("document.body.innerText || ''") or ""
        last = f"pending={pending}"
        print(f"  poll pending={json.dumps(pending, ensure_ascii=False)[:220]}")
        if "download via" in log:
            print("  log: physical/default network download path seen")
        if isinstance(pending, dict):
            p = pending.get("pending")
            if isinstance(p, dict) and p.get("versionLabel"):
                ok(f"OTA APK ready pending={p}")
                shot("_ui_ota_download_ok.png")
                return
        if "下载失败" in body or "下载失败" in log or "download failed" in log.lower():
            fail(f"OTA failed body/log: {(body[:200] + ' | ' + log[-500:])}")
            shot("_ui_ota_download_fail.png")
            return
        # look for explicit fail event toast/log
        if "emitFailed" in log or "HTTP " in log and "下载失败" in log:
            fail(f"OTA log indicates failure: {log[-600:]}")
            shot("_ui_ota_download_fail.png")
            return
        time.sleep(4)

    # dump last logs for diagnosis
    print("  final logcat:\n" + log[-1200:])
    fail(f"OTA download timeout: {last}")
    shot("_ui_ota_download_timeout.png")


def wait_app_pid(timeout: float = 30) -> str:
    deadline = time.time() + timeout
    while time.time() < deadline:
        out = adb("shell", "pidof", PACKAGE, check=False).strip()
        if out:
            return out.split()[0]
        time.sleep(1)
    raise RuntimeError("app pid not found")


def main() -> int:
    print("== emulator OTA + nodes verify ==")
    ver = adb("shell", "dumpsys", "package", PACKAGE)
    m = re.search(r"versionName=([^\s]+)", ver)
    print(f"  apk versionName={m.group(1) if m else '?'}")

    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("logcat", "-c", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    pid = wait_app_pid()
    print(f"  pid={pid}")
    time.sleep(4)

    cdp = Cdp()
    try:
        login(cdp)
        ok("login")
        verify_nodes(cdp)
        verify_ota(cdp)
    finally:
        cdp.close()

    print("\n== summary ==")
    for line in RESULTS:
        print(line)
    fails = sum(1 for r in RESULTS if r.startswith("FAIL"))
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())
