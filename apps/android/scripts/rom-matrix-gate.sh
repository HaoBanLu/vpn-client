#!/usr/bin/env bash
# E2 ROM 抽测矩阵门禁：校验 records.json 中本轮有效通过数。
# 用法：
#   bash scripts/rom-matrix-gate.sh          # 提示模式（默认）
#   bash scripts/rom-matrix-gate.sh --strict # 未达标则 exit 1
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/rom-matrix/records.json"
STRICT=false
if [[ "${1:-}" == "--strict" ]]; then
  STRICT=true
fi

if [[ ! -f "$MANIFEST" ]]; then
  echo "ROM 矩阵文件不存在: $MANIFEST"
  exit 1
fi

PYTHON=""
for cmd in python3 python; do
  if command -v "$cmd" >/dev/null 2>&1; then
    PYTHON="$cmd"
    break
  fi
done
if [[ -z "$PYTHON" ]]; then
  echo "需要 python3 或 python 以校验 ROM 矩阵"
  exit 1
fi

"$PYTHON" - "$MANIFEST" "$STRICT" <<'PY'
import json, sys, io
from datetime import datetime, timedelta

# Windows 控制台常为 GBK，避免 emoji 导致 UnicodeEncodeError
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

path = sys.argv[1]
strict = str(sys.argv[2]).lower() in ("1", "true", "yes")
with open(path, encoding="utf-8") as f:
    data = json.load(f)

req = data.get("requirements", {})
release_round = data.get("release_round", "")
min_passed = int(req.get("min_passed", 5))
target = int(req.get("target_coverage", 20))
max_age = int(req.get("max_age_days", 90))
cutoff = datetime.utcnow() - timedelta(days=max_age)

def parse_date(s):
    if not s:
        return None
    for fmt in ("%Y-%m-%d", "%Y-%m-%dT%H:%M:%S"):
        try:
            return datetime.strptime(s[:19], fmt)
        except ValueError:
            continue
    return None

passed = []
pending = []
stale = []
for r in data.get("records", []):
    status = str(r.get("connect", "")).lower()
    if status in ("pass", "ok", "✅"):
        tested = parse_date(r.get("tested_at", ""))
        round_ok = not r.get("release_round") or r.get("release_round") == release_round
        fresh = tested is not None and tested >= cutoff
        if round_ok and fresh:
            passed.append(r)
        else:
            stale.append(r)
    elif status in ("pending", "", "skip"):
        pending.append(r)
    else:
        stale.append(r)

coverage = len(passed)
rate = (coverage / target * 100) if target else 0
print("==========================================")
print("E2 ROM 抽测矩阵")
print("==========================================")
print(f"发版轮次: {release_round}")
print(f"有效通过: {coverage} / 本轮要求 {min_passed}（目标覆盖 {target} 款，当前 {rate:.0f}%）")
print(f"待测占位: {len(pending)}  过期/未通过: {len(stale)}")
print(f"数据文件: {path}")
if passed:
    print("\n已通过机型:")
    for r in passed:
        print(f"  - {r.get('brand')} {r.get('model')} ({r.get('tun_stack')}) @ {r.get('tested_at')}")
print("\n发版前请更新 rom-matrix/records.json，并同步 docs/product/App真机ROM抽测矩阵.md")

ok = coverage >= min_passed
if not ok:
    print(f"\n[WARN] 未达本轮最低 {min_passed} 台通过标准")
    if strict:
        sys.exit(1)
    sys.exit(0)
print("\n[OK] ROM 矩阵本轮最低要求已满足")
PY
