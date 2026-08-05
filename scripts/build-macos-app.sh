#!/usr/bin/env bash
set -Eeuo pipefail

# Produces a self-contained macOS windowed application in dist/.
# Java remains an intentional system prerequisite: OCI SDK authentication uses the
# user's local ~/.oci configuration and key material at runtime.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
APP_NAME="JMS Data Viewer"
BUILD_DIR="${APP_ROOT}/.macos-build"
APP_BUNDLE="${APP_ROOT}/dist/${APP_NAME}.app"
CONTENTS_DIR="${APP_BUNDLE}/Contents"
RESOURCES_DIR="${CONTENTS_DIR}/Resources"
MACOS_DIR="${CONTENTS_DIR}/MacOS"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "$1 is required but was not found in PATH."; }

require java
require mvn
require npm
require swiftc

rm -rf "${BUILD_DIR}" "${APP_BUNDLE}"
mkdir -p "${BUILD_DIR}" "${RESOURCES_DIR}" "${MACOS_DIR}"

printf 'Building the React interface…\n'
(
  cd "${APP_ROOT}/frontend"
  if [[ ! -d node_modules ]]; then npm ci; fi
  npm run build
)

printf 'Packaging the Spring Boot service…\n'
(
  cd "${APP_ROOT}/backend"
  mvn -DskipTests package
)

STATIC_DIR="${BUILD_DIR}/static"
mkdir -p "${STATIC_DIR}"
cp -R "${APP_ROOT}/frontend/dist/." "${STATIC_DIR}/"
JAR_SOURCE="${APP_ROOT}/backend/target/java-fleet-commander-ai-0.0.1-SNAPSHOT.jar"
jar uf "${JAR_SOURCE}" -C "${BUILD_DIR}" static
cp "${JAR_SOURCE}" "${RESOURCES_DIR}/JavaFleetCommanderAI.jar"

printf 'Compiling the native macOS window…\n'
swiftc -parse-as-library -O -framework Cocoa -framework WebKit \
  "${APP_ROOT}/desktop/JMSDataViewer.swift" \
  -o "${MACOS_DIR}/JMS Data Viewer"

cat > "${CONTENTS_DIR}/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleName</key><string>JMS Data Viewer</string>
  <key>CFBundleDisplayName</key><string>JMS Data Viewer</string>
  <key>CFBundleIdentifier</key><string>com.oracle.jms-data-viewer</string>
  <key>CFBundleVersion</key><string>1.0.0</string>
  <key>CFBundleShortVersionString</key><string>1.0.0</string>
  <key>CFBundleExecutable</key><string>JMS Data Viewer</string>
  <key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST

printf 'Created: %s\n' "${APP_BUNDLE}"
printf 'Open it in Finder, or run: open %q\n' "${APP_BUNDLE}"
