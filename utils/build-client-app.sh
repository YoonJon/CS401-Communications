#!/usr/bin/env bash
# Assembles a double-clickable macOS .app bundle for the CS401 client.
# The bundle wraps dist/Client.jar (built by build-client-jar.sh) inside
# Contents/Resources/ and launches it through a tiny shell stub.
#
# Usage:
#   utils/build-client-app.sh           # build the bundle
#   utils/build-client-app.sh --sign    # also ad-hoc codesign it
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
APP="$DIST/CS401-Client.app"

SIGN=0
for arg in "$@"; do
    if [[ "$arg" == "--sign" ]]; then SIGN=1; fi
done

"$ROOT/utils/build-client-jar.sh"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

cp "$DIST/Client.jar" "$APP/Contents/Resources/Client.jar"

cat > "$APP/Contents/MacOS/launch" <<'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")/../Resources" && pwd)"
exec /usr/bin/java -jar "$DIR/Client.jar"
EOF
chmod 755 "$APP/Contents/MacOS/launch"

cat > "$APP/Contents/MacOS/Info.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>launch</string>
    <key>CFBundleIdentifier</key>
    <string>edu.csueb.cs401.client</string>
    <key>CFBundleName</key>
    <string>CS401 Client</string>
    <key>CFBundleDisplayName</key>
    <string>CS401 Client</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>12.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>LSUIElement</key>
    <false/>
</dict>
</plist>
EOF

if [[ $SIGN -eq 1 ]]; then
    codesign --force --deep --sign - "$APP"
    echo "Ad-hoc signed $APP"
fi

echo "Built $APP"
echo "Distribute the bundle by zipping it (e.g. ditto -c -k --keepParent $APP $APP.zip)."
