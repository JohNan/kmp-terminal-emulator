#!/bin/bash
set -e

echo "Building KMP Wasm package..."
./build-npm.sh

echo "Syncing package to agy-web-ui/frontend/kmp-terminal-local..."
DEST_DIR="/home/johan/agy-web-ui/frontend/kmp-terminal-local"
rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"
cp -r npm-package/* "$DEST_DIR"/

echo "Local package synced successfully!"
