# -*- coding: utf-8 -*-
"""CDP verification using Vue Router push."""
from __future__ import annotations

import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PACKAGE = "com.vpn.kuayun"
SHOT_DIR = Path(r"D:\Code\Go-www\vpn-client\apps\tauri\scripts")
RESULTS: list[str] = []


def adb(*args: str, check: bool = True) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {p.stderr or p.stdout}")
    return (p.stdout or "") + (p.stderr or "")


def wait(sec: float, msg: str) -> None:
    print(f"  ... {msg} ({sec}s)")
    time.sleep(sec)


def shot(name: str) -> None:
    with open(SHOT_DIR / name, "wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print(f"  shot {name}")


def pass_(c: str, d: str) -> None:
    RESULTS.append(f"PASS {c} :: {d}")
    print(RESULTS[-1])


def fail_(c: str, d: str) -> None:
    RESULTS.append(f"FAIL {c} :: {d}")
    print(RESULTS[-1])


def info_(c: str, d: str) -> None:
    RESULTS.append(f"INFO {c} :: {d}")
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
        for _ in range(100):
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


ROUTER_JS = r"""
(() => {
  const app = document.querySelector('#app')?.__vue_app__;
  if (!app) return null;
  return app.config.globalProperties.$router || null;
})()
"""


def main() -> int:
    print("== CDP router verification ==")
    if "\tdevice" not in adb("devices"):
        raise RuntimeError("no device")

    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    wait(6, "boot")

    cdp = Cdp()
    try:
        for _ in range(20):
            href = cdp.eval("location.href")
            print(f"  href={href}")
            if href and "login" not in str(href).lower():
                break
            wait(1, "wait auth")
        shot("_v0_home.png")
        pass_("boot", f"href={href}")

        has_router = cdp.eval(
            """
            (() => {
              const app = document.querySelector('#app')?.__vue_app__;
              return !!(app && app.config.globalProperties.$router);
            })()
            """
        )
        if not has_router:
            fail_("router", "vue router not found on #app")
            return 1
        pass_("router", "vue router available")

        # -------- A update --------
        print("\n---- A update ----")
        about = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'About' });
              await new Promise(r => setTimeout(r, 1200));
              return {
                route: router.currentRoute.value.name,
                path: router.currentRoute.value.fullPath,
                text: document.body.innerText.slice(0, 600),
                hasCheck: [...document.querySelectorAll('button')].some(b => (b.textContent||'').includes('检查更新')),
              };
            })()
            """
        )
        print(f"  about={about}")
        shot("_v1_about.png")
        if isinstance(about, dict) and (about.get("hasCheck") or "检查更新" in str(about.get("text", ""))):
            pass_("A1", f"about opened route={about.get('route')}")
        else:
            fail_("A1", f"about missing check button: {about}")
            # continue anyway

        check = cdp.eval(
            """
            (async () => {
              const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('检查更新'));
              if (!btn) return { err: 'no-btn', text: document.body.innerText.slice(0,400) };
              btn.click();
              await new Promise(r => setTimeout(r, 4000));
              const text = document.body.innerText;
              const buttons = [...document.querySelectorAll('button')].map(b => (b.textContent||'').trim()).filter(Boolean);
              return {
                text: text.slice(0, 800),
                buttons,
                hasPrompt: /发现新版本|立即更新|需要更新/.test(text),
                hasLatest: /已是最新|当前已是最新/.test(text),
              };
            })()
            """
        )
        print(f"  check={str(check)[:400]}")
        shot("_v2_check.png")
        if isinstance(check, dict) and check.get("hasPrompt"):
            pass_("A2", "update prompt shown")
            # accept update and observe cancel/error UI
            flow = cdp.eval(
                """
                (async () => {
                  // optional VPN hint path: mark connect store if possible, else just update
                  const updateBtn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('立即更新'));
                  if (!updateBtn) return { state: 'no-update-btn' };
                  updateBtn.click();
                  await new Promise(r => setTimeout(r, 2500));
                  const text = document.body.innerText;
                  const buttons = [...document.querySelectorAll('button')].map(b => (b.textContent||'').trim());
                  const hasCancel = buttons.some(t => t.includes('取消'));
                  const hasRetry = buttons.some(t => t.includes('重试'));
                  const hasLater = buttons.some(t => t.includes('稍后再说'));
                  const hasHint = text.includes('建议断开');
                  if (hasCancel) {
                    [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('取消')).click();
                    await new Promise(r => setTimeout(r, 1200));
                    return {
                      state: 'cancelled',
                      hasHint,
                      after: document.body.innerText.slice(0, 400),
                      buttonsAfter: [...document.querySelectorAll('button')].map(b => (b.textContent||'').trim()),
                    };
                  }
                  if (hasRetry) {
                    return { state: 'error', hasRetry, hasLater, hasHint, text: text.slice(0, 400) };
                  }
                  return { state: 'other', hasCancel, hasRetry, hasLater, hasHint, text: text.slice(0, 400), buttons };
                })()
                """
            )
            print(f"  flow={flow}")
            shot("_v3_update_flow.png")
            if isinstance(flow, dict):
                if flow.get("hasHint"):
                    pass_("A-vpn-hint", "showed disconnect VPN hint")
                if flow.get("state") == "cancelled":
                    pass_("A4", "cancel button worked")
                    after = str(flow.get("after", ""))
                    if "正在更新" not in after and "正在下载" not in after:
                        pass_("A5", "overlay closed after cancel")
                    else:
                        fail_("A5", "still downloading after cancel")
                elif flow.get("state") == "error":
                    if flow.get("hasRetry"):
                        pass_("A4", "error shows retry")
                    else:
                        fail_("A4", "error missing retry")
                    if flow.get("hasLater"):
                        pass_("A5", "error shows later")
                    else:
                        fail_("A5", "error missing later")
                else:
                    info_("A4", f"update UI state={flow}")
        elif isinstance(check, dict) and check.get("hasLatest"):
            pass_("A2", "already latest — fail/retry/cancel E2E needs a newer package on server")
            # Still verify dismiss/accepted keys helpers exist in localStorage API surface by reading code path via overlay idle
            keys = cdp.eval(
                """
                ({
                  dismissed: localStorage.getItem('tauri_update_dismissed_version'),
                  accepted: localStorage.getItem('tauri_update_accepted_version'),
                  lastCheck: localStorage.getItem('tauri_update_last_check_at'),
                })
                """
            )
            print(f"  update keys={keys}")
            if isinstance(keys, dict) and keys.get("lastCheck"):
                pass_("A-interval", "last check timestamp recorded (30min gate uses this)")
            else:
                info_("A-interval", f"lastCheck missing: {keys}")
        else:
            fail_("A2", f"unexpected: {check}")

        # -------- B pull refresh on Orders subpage --------
        print("\n---- B pull refresh ----")
        orders = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Orders' });
              await new Promise(r => setTimeout(r, 1500));
              const pullRoot = document.querySelector('.ky-sub-body.ky-sub-body--pull, .ky-sub-page .ky-pull-refresh');
              const anyPull = document.querySelector('.ky-pull-refresh');
              let indicator = false;
              let pullingClass = false;
              if (pullRoot) {
                const rect = pullRoot.getBoundingClientRect();
                const x = rect.left + rect.width / 2;
                const y = Math.min(rect.top + 30, rect.bottom - 10);
                const fire = (type, clientY, buttons=1) => {
                  pullRoot.dispatchEvent(new PointerEvent(type, {
                    bubbles: true, cancelable: true, pointerId: 7, pointerType: 'touch',
                    clientX: x, clientY, buttons, isPrimary: true,
                    pressure: type === 'pointerup' ? 0 : 0.5,
                  }));
                };
                fire('pointerdown', y);
                for (let i = 1; i <= 10; i++) {
                  fire('pointermove', y + i * 28);
                  await new Promise(r => setTimeout(r, 25));
                }
                pullingClass = pullRoot.className.includes('pulling') || pullRoot.className.includes('refreshing');
                indicator = document.body.innerText.includes('下拉刷新')
                  || document.body.innerText.includes('松开刷新')
                  || document.body.innerText.includes('刷新中')
                  || pullingClass;
                fire('pointerup', y + 280, 0);
                await new Promise(r => setTimeout(r, 1200));
              }
              return {
                route: router.currentRoute.value.name,
                hasSubPull: !!pullRoot,
                hasAnyPull: !!anyPull,
                indicator,
                pullingClass,
                pullClass: pullRoot?.className || '',
                text: document.body.innerText.slice(0, 350),
              };
            })()
            """
        )
        print(f"  orders={orders}")
        shot("_v4_orders_pull.png")
        if isinstance(orders, dict) and orders.get("route") == "Orders":
            pass_("B1", "Orders route opened")
        else:
            fail_("B1", f"not on Orders: {orders}")
        if isinstance(orders, dict) and orders.get("hasSubPull"):
            pass_("B1b", "KySubPage KyPullRefresh mounted")
        else:
            fail_("B1b", f"subpage pull missing: {orders}")
        if isinstance(orders, dict) and orders.get("indicator"):
            pass_("B2", "pull indicator/class active")
        else:
            fail_("B2", f"no pull feedback: {orders}")

        # Traffic subpage also
        traffic = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Traffic' });
              await new Promise(r => setTimeout(r, 1200));
              return {
                route: router.currentRoute.value.name,
                hasSubPull: !!document.querySelector('.ky-sub-body.ky-sub-body--pull, .ky-sub-page .ky-pull-refresh'),
                text: document.body.innerText.slice(0, 250),
              };
            })()
            """
        )
        print(f"  traffic={traffic}")
        shot("_v5_traffic.png")
        if isinstance(traffic, dict) and traffic.get("route") == "Traffic" and traffic.get("hasSubPull"):
            pass_("B4", "Traffic subpage has pull refresh")
        elif isinstance(traffic, dict) and traffic.get("route") == "Traffic":
            fail_("B4", "Traffic opened but no sub pull")
        else:
            fail_("B4", f"Traffic failed: {traffic}")

        # Devices too
        devices = cdp.eval(
            """
            (async () => {
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({ name: 'Devices' });
              await new Promise(r => setTimeout(r, 1200));
              return {
                route: router.currentRoute.value.name,
                hasSubPull: !!document.querySelector('.ky-sub-page .ky-pull-refresh'),
              };
            })()
            """
        )
        print(f"  devices={devices}")
        if isinstance(devices, dict) and devices.get("hasSubPull"):
            pass_("B5", "Devices subpage has pull refresh")
        else:
            fail_("B5", f"Devices pull missing: {devices}")

    finally:
        cdp.close()

    print("\n==== SUMMARY ====")
    for r in RESULTS:
        print(r)
    fails = sum(1 for r in RESULTS if r.startswith("FAIL"))
    passes = sum(1 for r in RESULTS if r.startswith("PASS"))
    print(f"PASS={passes} FAIL={fails}")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
