# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"D:\Code\Go-www\vpn-client")


def bump(path: Path, pairs: list[tuple[str, str]]) -> None:
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        if old not in text:
            raise SystemExit(f"missing {old!r} in {path}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8", newline="\n")
    print("ok", path)


bump(ROOT / "apps/tauri/src/lib/app-meta.ts", [
    ("APP_VERSION_NAME = '1.2.31'", "APP_VERSION_NAME = '1.2.32'"),
    ("APP_VERSION_CODE = 151", "APP_VERSION_CODE = 152"),
])
bump(ROOT / "apps/tauri/package.json", [('"version": "1.2.31"', '"version": "1.2.32"')])
bump(ROOT / "apps/tauri/package-lock.json", [('"version": "1.2.31"', '"version": "1.2.32"')])
bump(ROOT / "apps/tauri/src-tauri/tauri.conf.json", [("1.2.31", "1.2.32")])
bump(ROOT / "apps/tauri/src-tauri/Cargo.toml", [('version = "1.2.31"', 'version = "1.2.32"')])
lock = ROOT / "apps/tauri/src-tauri/Cargo.lock"
text = lock.read_text(encoding="utf-8")
old = 'name = "vpn-tauri"\nversion = "1.2.31"'
new = 'name = "vpn-tauri"\nversion = "1.2.32"'
if old not in text:
    raise SystemExit("Cargo.lock pattern missing")
lock.write_text(text.replace(old, new, 1), encoding="utf-8", newline="\n")
print("ok", lock)
ios = ROOT / "apps/tauri/platforms/ios/project.yml"
bump(ios, [
    ('MARKETING_VERSION: "1.2.31"', 'MARKETING_VERSION: "1.2.32"'),
    ('CURRENT_PROJECT_VERSION: "151"', 'CURRENT_PROJECT_VERSION: "152"'),
])
