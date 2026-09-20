# -*- coding: utf-8 -*-
import re
import subprocess
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
script = Path(r"D:\Code\Go-www\vpn-client\apps\tauri\scripts\emulator_update_refresh_check.py")
raw = script.read_bytes()
print("has utf8 我的:", b"\xe6\x88\x91\xe7\x9a\x84" in raw)
text = script.read_text(encoding="utf-8")
for line in text.splitlines():
    if "我的" in line or "关于" in line or "检查更新" in line:
        print("LINE:", line)

subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ky_ui.xml"])
xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/ky_ui.xml"], capture_output=True).stdout.decode(
    "utf-8", "replace"
)
print("xml len", len(xml))
print("packages", sorted(set(re.findall(r'package="([^"]+)"', xml))))
print("texts:", list(dict.fromkeys(re.findall(r'text="([^"]+)"', xml)))[:60])
print("content-desc:", list(dict.fromkeys(re.findall(r'content-desc="([^"]+)"', xml)))[:40])
