#!/usr/bin/env bash
# Builds dist/Client.jar — a runnable JAR for the CS401 client with
# Main-Class=client.ClientController.
#
# Usage: scripts/build-client-jar.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/out"
DIST="$ROOT/dist"
JAR="$DIST/Client.jar"

# Stamp BuildInfo with the current git revision so the bundled jar identifies itself.
"$ROOT/utils/write-build-info.sh"

mkdir -p "$OUT" "$DIST"

# Recompile from source. The project intentionally has no build system,
# so we drive javac directly over src/.
SOURCES="$(mktemp)"
trap 'rm -f "$SOURCES"' EXIT
find "$ROOT/src" -name '*.java' > "$SOURCES"
javac -d "$OUT" @"$SOURCES"

# Package only the client + shared trees. server.* and the default-package
# launcher classes (ServerWithIPLauncher, SeedSerializedData, IP_Finder) are
# intentionally omitted so the client deliverable cannot run a server.
jar --create --file "$JAR" \
    --main-class=client.ClientController \
    -C "$OUT" client \
    -C "$OUT" shared

echo "Built $JAR"
