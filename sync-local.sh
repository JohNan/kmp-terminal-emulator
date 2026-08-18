#!/bin/bash
set -e

DEST_DIR="${1:-${DEST_DIR:-../agy-web-ui/frontend/kmp-terminal-local}}"

echo "Building KMP Wasm package..."
./build-npm.sh

echo "Syncing package to ${DEST_DIR}..."
rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"
cp -r npm-package/* "$DEST_DIR"/

echo "Local package synced successfully!"
