#!/bin/bash
set -e

echo "Building production WebAssembly bundle..."
mise exec -- ./gradlew :demo-web:wasmJsBrowserDistribution --no-daemon

echo "Preparing npm package directory..."
rm -rf npm-package
mkdir -p npm-package

# Copy build outputs
cp -r demo-web/build/dist/wasmJs/productionExecutable/* npm-package/

# Create package.json inside the npm package
cat <<EOF > npm-package/package.json
{
  "name": "@JohNan/kmp-terminal",
  "version": "0.3.0",
  "description": "Kotlin Multiplatform Terminal Emulator compiled to WebAssembly",
  "main": "demo-web.js",
  "files": [
    "demo-web.js",
    "*.wasm",
    "composeResources/**/*"
  ],
  "publishConfig": {
    "registry": "https://npm.pkg.github.com"
  },
  "repository": {
    "type": "git",
    "url": "git@github.com:JohNan/kmp-terminal-emulator.git"
  }
}
EOF

# Create .npmrc for publishing
cat <<EOF > npm-package/.npmrc
@JohNan:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=\${GH_TOKEN_PKG}
EOF

echo "NPM Package prepared under npm-package/!"
