#!/usr/bin/env bash
# 从 release-meta.json 生成 GitHub Release 正文（Markdown）
set -euo pipefail

META="${1:-release-meta.json}"

if [[ ! -f "$META" ]]; then
  echo "ERROR: $META not found" >&2
  exit 1
fi

python3 - <<'PY' "$META"
import json, sys
meta = json.load(open(sys.argv[1]))
lines = [
    f"## 跨云客户端发版 {meta['tag']}",
    "",
    f"- **Git SHA**: `{meta['sha'][:12]}`",
    f"- **构建时间 (UTC)**: {meta['built_at_utc']}",
    "",
    "## 各端版本",
    "",
    "| 平台 | versionName | versionCode |",
    "|------|-------------|-------------|",
]
for platform, label in [
    ("android", "Android"),
    ("windows", "Windows"),
    ("macos", "macOS"),
    ("ios", "iPhone"),
]:
    p = meta["platforms"][platform]
    lines.append(f"| {label} | {p['version_name']} | {p['version_code']} |")
lines += [
    "",
    "## 附件",
    "",
    "请下载对应平台安装包。Android / Windows / macOS 可上传至管理后台 **App 版本管理**。",
    "",
    "详细说明见 [客户端 CI 自动发包 PRD](docs/product/客户端CI自动发包产品需求.md)。",
]
print("\n".join(lines))
PY
