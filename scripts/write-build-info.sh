#!/usr/bin/env bash
# Updates embedded VERSION and GIT_REVISION in src/shared/BuildInfo.java from the Git checkout
# at scripts/write-build-info.sh run time (fallback values when runtime Git is unavailable).
# VERSION = git rev-list --count HEAD; GIT_REVISION = short hash plus -dirty when needed.
# Run before javac, or wire into a compile alias / git pre-commit hook.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/shared/BuildInfo.java"

if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  COUNT="$(git -C "$ROOT" rev-list --count HEAD)"
  SHORT="$(git -C "$ROOT" rev-parse --short HEAD)"
  DIRTY=""
  if ! git -C "$ROOT" diff-index --quiet HEAD -- 2>/dev/null; then
    DIRTY="-dirty"
  fi
else
  COUNT="0"
  SHORT="nogit"
  DIRTY=""
fi

REV="${SHORT}${DIRTY}"

if [[ ! -f "$OUT" ]]; then
  echo "ERROR: $OUT not found — create shared/BuildInfo.java first." >&2
  exit 1
fi

# BSD/macOS and GNU sed: in-place edit of only the two constant lines (class body otherwise hand-maintained).
if sed --version >/dev/null 2>&1; then
  sed -i 's/public static final int VERSION = [0-9][0-9]*/public static final int VERSION = '"$COUNT"'/' "$OUT"
  sed -i 's|public static final String GIT_REVISION = "[^"]*"|public static final String GIT_REVISION = "'"$REV"'"|' "$OUT"
else
  sed -i '' 's/public static final int VERSION = [0-9][0-9]*/public static final int VERSION = '"$COUNT"'/' "$OUT"
  sed -i '' 's|public static final String GIT_REVISION = "[^"]*"|public static final String GIT_REVISION = "'"$REV"'"|' "$OUT"
fi

echo "Updated $OUT (VERSION=$COUNT, GIT_REVISION=$REV)"
