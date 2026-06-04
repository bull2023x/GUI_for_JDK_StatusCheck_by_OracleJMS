#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_NAME="JMS Data Viewer"
APP_BUNDLE="${APP_ROOT}/${APP_NAME}.app"
MACOS_DIR="${APP_BUNDLE}/Contents/MacOS"

mkdir -p "${MACOS_DIR}"

cat >"${APP_BUNDLE}/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>JMS Data Viewer</string>
  <key>CFBundleDisplayName</key>
  <string>JMS Data Viewer</string>
  <key>CFBundleIdentifier</key>
  <string>local.jms-data-viewer.launcher</string>
  <key>CFBundleVersion</key>
  <string>1.0</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0</string>
  <key>CFBundleExecutable</key>
  <string>JMS Data Viewer</string>
  <key>LSUIElement</key>
  <false/>
</dict>
</plist>
PLIST

cat >"${MACOS_DIR}/${APP_NAME}" <<'LAUNCHER'
#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LAUNCH_SCRIPT="${APP_ROOT}/scripts/launch-desktop.sh"

osascript <<APPLESCRIPT
tell application "Terminal"
  activate
  do script quoted form of "${LAUNCH_SCRIPT}"
end tell
APPLESCRIPT
LAUNCHER

chmod +x "${MACOS_DIR}/${APP_NAME}"

printf 'Created: %s\n' "${APP_BUNDLE}"
printf 'You can double-click it from Finder.\n'
