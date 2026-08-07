#!/usr/bin/env bash
# Release 发版门禁：单元测试 →（可选）仪器化 + adb 稳定性 → Release APK
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB="${ADB:-adb}"
API_BASE="${RELEASE_GATE_API_BASE:-http://10.0.2.2:48080/}"

echo "=========================================="
echo "跨云 Android Release Gate"
echo "=========================================="

echo "[1/4] 单元测试"
./gradlew :app:testDebugUnitTest

HAS_DEVICE=false
if "$ADB" devices 2>/dev/null | grep -E '^\S+\s+device$' | grep -qv 'emulator'; then
  HAS_DEVICE=true
fi
if "$ADB" devices 2>/dev/null | grep -q 'emulator.*device'; then
  HAS_DEVICE=true
fi

if [[ "$HAS_DEVICE" == true ]]; then
  echo "[2/4] 仪器化冒烟（需 Docker API 或跳过 -PskipConnectedTests=1）"
  if [[ "${SKIP_CONNECTED_TESTS:-0}" != "1" ]]; then
    ./gradlew :app:connectedDebugAndroidTest \
      -PdebugApiBase="$API_BASE" \
      -Pandroid.testInstrumentationRunnerArguments.class=com.vpn.member.ConnectDataplaneInstrumentedTest,com.vpn.member.AuthStabilitySmokeTest
    echo "[3/4] adb 稳定性脚本"
    bash scripts/vpn-stability-adb-check.sh || true
  else
    echo "  跳过仪器化（SKIP_CONNECTED_TESTS=1）"
  fi
else
  echo "[2/4] 无 adb 设备，跳过仪器化与稳定性脚本"
  echo "[3/4] 跳过"
fi

if [[ "${SKIP_WEAK_NETWORK_SMOKE:-0}" != "1" ]]; then
  if curl -sf "${RELEASE_GATE_API_BASE:-http://localhost:48080}/health" >/dev/null 2>&1; then
    echo "[3.5/4] 弱网 API 冒烟"
    API_BASE="${RELEASE_GATE_API_BASE:-http://localhost:48080}" bash ../../scripts/dev/weak-network-smoke.sh || true
  else
    echo "[3.5/4] API 不可达，跳过弱网冒烟"
  fi
fi

echo "[4/4] Release 构建（默认仅 arm64-v8a；瘦包加 -PslimNativeLibs=true）"
SLIM_FLAG=""
if [[ "${SLIM_NATIVE_LIBS:-0}" == "1" ]]; then
  SLIM_FLAG="-PslimNativeLibs=true"
fi
./gradlew :app:assembleRelease -PreleaseArm64Only=true $SLIM_FLAG

echo ""
echo "=========================================="
echo "Release Gate 完成"
echo "=========================================="
echo "APK: app/build/outputs/apk/release/"
echo "发版前请对照: docs/guides/App-Android-发版检查清单.md"
bash scripts/release-crash-health-hint.sh
bash scripts/rom-matrix-gate.sh
if [[ "${ROM_MATRIX_STRICT:-0}" == "1" ]]; then
  bash scripts/rom-matrix-gate.sh --strict
fi
ls -la app/build/outputs/apk/release/*.apk 2>/dev/null || true

if [[ "${BANGKOK_ACCEPTANCE:-0}" == "1" ]]; then
  echo ""
  echo "=========================================="
  echo "E1 曼谷回国验收（需 SSH）"
  echo "=========================================="
  if command -v python3 >/dev/null 2>&1 && [[ -f ../../ssh_debug/test_bangkok_cn_nodes_domestic_return.py ]]; then
    python3 ../../ssh_debug/test_bangkok_cn_nodes_domestic_return.py || {
      echo "曼谷验收未通过（KPI ≥12/15）"
      exit 1
    }
  else
    echo "跳过：未找到验收脚本或 python3"
    exit 1
  fi
fi
