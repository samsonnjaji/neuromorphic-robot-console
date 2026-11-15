#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="9.7.0"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="$PROJECT_ROOT/.tools"
DIST_DIR="$TOOLS_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"

if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$TOOLS_DIR"
  ARCHIVE="$TOOLS_DIR/gradle-$GRADLE_VERSION-bin.zip"
  BASE_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  EXPECTED="84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae"
  echo "Downloading Gradle $GRADLE_VERSION..."
  curl -fL "$BASE_URL" -o "$ARCHIVE"
  ACTUAL="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
  if [[ "$EXPECTED" != "$ACTUAL" ]]; then
    rm -f "$ARCHIVE"
    echo "Gradle distribution checksum verification failed." >&2
    exit 1
  fi
  unzip -q "$ARCHIVE" -d "$TOOLS_DIR"
  rm -f "$ARCHIVE"
fi

cd "$PROJECT_ROOT"
exec "$GRADLE_BIN" "$@"
