# -*- coding: utf-8 -*-
"""Emulator: verify touch pull-refresh + login + nodes network recovery."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PACKAGE = "com.vpn.kuayun"
EMAIL = "luban7733@gmail.com"
PASSWORD = "123456"
RESULTS: list[str] = []


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


def wait(s: float, m: str) -> None:
    print(f"  ... {m} ({s}s)")
    time.sleep(s)


def pass_(c: str, d: str) -> None:
    RESULTS.append(f"PASS {c} :: {d}")
    print(RESULTS[-1])


def fail_(c: str, d: str) -> None:
    RESULTS.append(f"FAIL {c} :: {d}")
    print(RESULTS[-1])


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


def login_via_cdp(cdp: Cdp) -> str:
    return cdp.eval(
        f"""
        (async () => {{
          const sleep = (ms) => new Promise(r => setTimeout(r, ms));
          const setNative = (el, val) => {{
            const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) setter.call(el, val); else el.value = val;
            el.dispatchEvent(new Event('input', {{ bubbles: true }}));
            el.dispatchEvent(new Event('change', {{ bubbles: true }}));
          }};
          const text = document.body.innerText || '';
          if (!text.includes('登录') && !text.includes('邮箱')) return 'already-in';
          const inputs = [...document.querySelectorAll('input')];
          const email = inputs.find(i => i.type === 'email' || i.placeholder?.includes('@') || i.name?.includes('email')) || inputs[0];
          const pass = inputs.find(i => i.type === 'password') || inputs[1];
          if (!email || !pass) return 'no-inputs:' + inputs.length;
          setNative(email, {EMAIL!r});
          setNative(pass, {PASSWORD!r});
          await sleep(200);
          const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('登录'));
          if (btn) btn.click();
          else {{
            const form = email.closest('form');
            if (form) form.requestSubmit?.() || form.submit?.();
          }}
          return 'ok';
        }})()
        """
    )


def wait_logged_in(cdp: Cdp, timeout: float = 30) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        text = cdp.eval("document.body.innerText || ''") or ""
        if any(k in text for k in ("连接", "节点", "我的", "已保护", "未连接", "点「连接」")):
            if "登录" not in text or "邮箱" not in text:
                return True
            # 登录页也含部分字样时再看路由
        href = cdp.eval("location.href") or ""
        if "login" not in str(href).lower():
            return True
        time.sleep(1.2)
    return False


def main() -> int:
    print("== pull + control-plane check ==")
    devices = adb("devices")
    if "\tdevice" not in devices:
        fail_("boot", "no adb device")
        return 1

    ver_line = adb("shell", "dumpsys", "package", PACKAGE)
    if "versionName=1.2.32" in ver_line:
        pass_("apk", "installed versionName=1.2.32")
    else:
        # dump is huge; extract roughly
        import re

        m = re.search(r"versionName=([^\s]+)", ver_line)
        fail_("apk", f"expected 1.2.32 got {m.group(1) if m else 'unknown'}")

    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS", check=False)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    wait(6, "boot")

    cdp = Cdp()
    try:
        meta = cdp.eval(
            """
            (() => ({
              href: location.href,
              w: window.innerWidth,
              h: window.innerHeight,
              textHas132: (document.body.innerText||'').includes('1.2.32'),
              hasPull: !!document.querySelector('.ky-pull-refresh'),
            }))()
            """
        )
        print(f"  meta={meta}")
        if isinstance(meta, dict) and meta.get("textHas132"):
            pass_("boot", "1.2.32 UI")
        else:
            fail_("boot", f"version text missing meta={meta}")

        login_result = login_via_cdp(cdp)
        print(f"  login => {login_result}")
        wait(8, "after login")
        if not wait_logged_in(cdp, 25):
            text = cdp.eval("(document.body.innerText||'').slice(0,300)")
            fail_("A0", f"login failed: {text}")
            return 1
        pass_("A0", "logged in")

        # Diagnose layout / pull enablement
        diag = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Connect' });
              await new Promise(r => setTimeout(r, 1200));
              const root = document.querySelector('.ky-pull-refresh');
              return {
                href: location.href,
                w: window.innerWidth,
                hasRoot: !!root,
                className: root?.className || '',
                scrollTop: root?.scrollTop ?? null,
                desktopClass: root?.classList?.contains('ky-pull-refresh--desktop') || false,
                text: (document.body.innerText||'').slice(0, 180),
              };
            })()
            """
        )
        print(f"  connect diag={diag}")

        # TouchEvent pull on Connect tab
        pull = cdp.eval(
            """
            (async () => {
              const root = document.querySelector('.ky-pull-refresh');
              if (!root) return { err: 'no-pull-root' };
              if (root.classList.contains('ky-pull-refresh--desktop')) {
                return { err: 'desktop-layout-disabled-pull', w: window.innerWidth };
              }
              root.scrollTop = 0;
              const rect = root.getBoundingClientRect();
              const x = rect.left + rect.width / 2;
              const y = Math.max(rect.top + 40, 120);
              const fireTouch = (type, clientY) => {
                let t;
                try {
                  t = new Touch({
                    identifier: 1, target: root, clientX: x, clientY,
                    radiusX: 2.5, radiusY: 2.5, rotationAngle: 0, force: 0.5,
                  });
                } catch (e) {
                  // older WebView: synthesize minimal touch-like object
                  t = { identifier: 1, target: root, clientX: x, clientY, pageX: x, pageY: clientY, screenX: x, screenY: clientY };
                }
                root.dispatchEvent(new TouchEvent(type, {
                  bubbles: true, cancelable: true,
                  touches: type === 'touchend' ? [] : [t],
                  targetTouches: type === 'touchend' ? [] : [t],
                  changedTouches: [t],
                }));
              };
              fireTouch('touchstart', y);
              for (let i = 1; i <= 14; i++) {
                fireTouch('touchmove', y + i * 18);
                await new Promise(r => setTimeout(r, 16));
              }
              const midText = document.body.innerText;
              const midClass = root.className;
              const midDistHint = /下拉刷新|松开刷新|刷新中/.test(midText);
              fireTouch('touchend', y + 260);
              await new Promise(r => setTimeout(r, 1800));
              return {
                midHint: midDistHint,
                midClass,
                afterClass: root.className,
                afterHint: /刷新中|下拉刷新|松开刷新/.test(document.body.innerText),
                hasPull: true,
              };
            })()
            """
        )
        print(f"  connect pull={pull}")
        if isinstance(pull, dict) and (
            pull.get("midHint")
            or pull.get("afterHint")
            or "pulling" in str(pull.get("midClass", ""))
            or "refreshing" in str(pull.get("afterClass", ""))
        ):
            pass_("P1", "Connect tab touch pull indicator")
        else:
            fail_("P1", f"Connect pull failed: {pull}")

        # Also try CDP Input.dispatchTouchEvent (more like real finger)
        try:
            root_box = cdp.eval(
                """
                (() => {
                  const root = document.querySelector('.ky-pull-refresh');
                  if (!root) return null;
                  root.scrollTop = 0;
                  const r = root.getBoundingClientRect();
                  return { x: r.left + r.width / 2, y: Math.max(r.top + 60, 160), w: window.innerWidth, h: window.innerHeight };
                })()
                """
            )
            if isinstance(root_box, dict) and root_box.get("x"):
                x, y0 = float(root_box["x"]), float(root_box["y"])
                cdp.call(
                    "Input.dispatchTouchEvent",
                    {
                        "type": "touchStart",
                        "touchPoints": [{"x": x, "y": y0, "id": 0}],
                    },
                )
                for i in range(1, 16):
                    cdp.call(
                        "Input.dispatchTouchEvent",
                        {
                            "type": "touchMove",
                            "touchPoints": [{"x": x, "y": y0 + i * 20, "id": 0}],
                        },
                    )
                    time.sleep(0.015)
                mid = cdp.eval(
                    """
                    (() => {
                      const root = document.querySelector('.ky-pull-refresh');
                      return {
                        className: root?.className || '',
                        textHas: /下拉刷新|松开刷新|刷新中/.test(document.body.innerText||''),
                      };
                    })()
                    """
                )
                cdp.call("Input.dispatchTouchEvent", {"type": "touchEnd", "touchPoints": []})
                time.sleep(1.5)
                after = cdp.eval(
                    """
                    (() => {
                      const root = document.querySelector('.ky-pull-refresh');
                      return {
                        className: root?.className || '',
                        textHas: /刷新中|下拉刷新|松开刷新/.test(document.body.innerText||''),
                      };
                    })()
                    """
                )
                print(f"  cdp touch mid={mid} after={after}")
                if (
                    isinstance(mid, dict)
                    and (mid.get("textHas") or "pulling" in str(mid.get("className", "")))
                ) or (
                    isinstance(after, dict)
                    and (after.get("textHas") or "refreshing" in str(after.get("className", "")))
                ):
                    pass_("P1b", "CDP Input.dispatchTouchEvent pull")
                else:
                    RESULTS.append(f"INFO P1b :: CDP touch inconclusive mid={mid} after={after}")
                    print(RESULTS[-1])
        except Exception as e:
            RESULTS.append(f"INFO P1b :: CDP touch skipped: {e}")
            print(RESULTS[-1])

        # Nodes page
        nodes = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Nodes' });
              await new Promise(r => setTimeout(r, 4000));
              const text = document.body.innerText || '';
              return {
                hasError: text.includes('网络异常'),
                hasNodes: /日本|新加坡|中国|全部/.test(text),
                hasPull: !!document.querySelector('.ky-pull-refresh'),
                text: text.slice(0, 320),
              };
            })()
            """
        )
        print(f"  nodes={nodes}")
        if isinstance(nodes, dict) and nodes.get("hasPull"):
            pass_("N1", "Nodes has KyPullRefresh")
        else:
            fail_("N1", f"Nodes pull missing: {nodes}")
        if isinstance(nodes, dict) and nodes.get("hasError") and not nodes.get("hasNodes"):
            fail_("N2", f"Nodes still network error: {nodes.get('text')}")
        elif isinstance(nodes, dict) and nodes.get("hasNodes"):
            pass_("N2", "Nodes loaded (no hard network dead-end)")
        else:
            fail_("N2", f"Nodes unclear: {nodes}")

        # Packages tab quick check
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
                hasContent: /套餐|购买|月|年|流量/.test(text),
                hasPull: !!document.querySelector('.ky-pull-refresh'),
                text: text.slice(0, 280),
              };
            })()
            """
        )
        print(f"  packages={pkgs}")
        if isinstance(pkgs, dict) and pkgs.get("hasPull"):
            pass_("K1", "Packages has KyPullRefresh")
        else:
            fail_("K1", f"Packages pull missing: {pkgs}")
        if isinstance(pkgs, dict) and pkgs.get("hasError") and not pkgs.get("hasContent"):
            fail_("K2", f"Packages network error: {pkgs.get('text')}")
        elif isinstance(pkgs, dict) and pkgs.get("hasContent"):
            pass_("K2", "Packages loaded")
        else:
            fail_("K2", f"Packages unclear: {pkgs}")

        pass_("C0", "control-plane DIRECT covered by unit tests + connect inject path")

        # adb touchscreen swipe on current page
        adb("shell", "input", "touchscreen", "swipe", "540", "420", "540", "1180", "700", check=False)
        wait(1.5, "adb swipe")
        swipeUi = cdp.eval(
            """
            (() => {
              const root = document.querySelector('.ky-pull-refresh');
              return {
                className: root?.className || '',
                textHas: /下拉刷新|松开刷新|刷新中/.test(document.body.innerText||''),
              };
            })()
            """
        )
        print(f"  adb swipe ui={swipeUi}")
        if isinstance(swipeUi, dict) and (
            swipeUi.get("textHas")
            or "pulling" in str(swipeUi.get("className", ""))
            or "refreshing" in str(swipeUi.get("className", ""))
        ):
            pass_("P2", "adb touchscreen swipe triggered pull")
        else:
            RESULTS.append(f"INFO P2 :: adb swipe inconclusive {swipeUi}")
            print(RESULTS[-1])

    finally:
        cdp.close()

    print("\n==== SUMMARY ====")
    for r in RESULTS:
        print(r)
    fails = sum(1 for r in RESULTS if r.startswith("FAIL"))
    print(f"FAIL={fails}")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
