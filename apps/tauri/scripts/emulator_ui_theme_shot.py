# -*- coding: utf-8 -*-
import json, subprocess, time, urllib.request
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
PKG = "com.vpn.kuayun"
SHOT = Path(__file__).resolve().parent


def adb(*args: str) -> str:
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (p.stdout or "") + (p.stderr or "")


def main() -> None:
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(5)
    import websocket

    pid = adb("shell", "pidof", PKG).strip().split()[0]
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
                if "error" in data:
                    raise RuntimeError(data["error"])
                return (data.get("result") or {}).get("result", {}).get("value")

    tokens = ev(
        """(() => {
          const s = getComputedStyle(document.documentElement);
          return {
            accent: s.getPropertyValue('--ky-accent').trim(),
            bg: s.getPropertyValue('--ky-bg').trim(),
            success: s.getPropertyValue('--ky-success').trim(),
            brand: s.getPropertyValue('--ky-accent-brand').trim(),
          };
        })()"""
    )
    print("tokens", tokens)

    # login if needed
    href = ev("location.href")
    text = ev("document.body.innerText || ''") or ""
    if "login" in str(href).lower() or "登录" in text[:80]:
        ev(
            """(async () => {
              const setNative = (el, val) => {
                const proto = HTMLInputElement.prototype;
                const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
                if (setter) setter.call(el, val); else el.value = val;
                el.dispatchEvent(new Event('input', { bubbles: true }));
              };
              const inputs = [...document.querySelectorAll('input')];
              const email = inputs.find(i => i.type === 'email') || inputs[0];
              const pass = inputs.find(i => i.type === 'password') || inputs[1];
              if (!email || !pass) return 'no-inputs';
              setNative(email, 'luban7733@gmail.com');
              setNative(pass, '123456');
              const btn = [...document.querySelectorAll('button')].find(b => (b.textContent||'').includes('登录'));
              if (btn) btn.click();
              return 'ok';
            })()"""
        )
        time.sleep(8)

    tabs = [
        ("Connect", "_ui_connect.png"),
        ("Nodes", "_ui_nodes.png"),
        ("Packages", "_ui_packages.png"),
        ("Profile", "_ui_profile.png"),
    ]
    for name, shot in tabs:
        ev(
            f"""(async () => {{
              const app = document.querySelector('#app').__vue_app__;
              const router = app.config.globalProperties.$router;
              await router.push({{ name: '{name}' }});
              await new Promise(r => setTimeout(r, 1500));
              return location.href;
            }})()"""
        )
        adb("shell", "screencap", "-p", f"/sdcard/{shot}")
        adb("pull", f"/sdcard/{shot}", str(SHOT / shot))
        print("shot", shot)

    ws.close()
    print("done")


if __name__ == "__main__":
    main()
