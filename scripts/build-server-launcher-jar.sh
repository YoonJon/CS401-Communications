#!/usr/bin/env bash
# Builds utils/server-with-ip-launcher.jar from src/ + utils/ with the latest
# DiscoveryResponder code embedded. Run this whenever discovery code changes
# or the launcher itself changes — otherwise the bundled jar will go stale.
#
# Usage: scripts/build-server-launcher-jar.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/out"
JAR="$ROOT/utils/server-with-ip-launcher.jar"

"$ROOT/utils/write-build-info.sh"

mkdir -p "$OUT"

SOURCES="$(mktemp)"
trap 'rm -f "$SOURCES"' EXIT
find "$ROOT/src" "$ROOT/utils" -name '*.java' > "$SOURCES"
javac -d "$OUT" @"$SOURCES"

jar --create --file "$JAR" \
    --main-class=ServerWithIPLauncher \
    -C "$OUT" .

echo "Built $JAR"
