# Vanilla JS/HTML Integration Demo

This folder contains a vanilla HTML and JavaScript demo demonstrating how to mount, control, and handle events from the compiled Kotlin Multiplatform WebAssembly terminal emulator.

## Running the Demo

1. Build the production Wasm distribution package:
   ```bash
   mise run compile-wasm
   ```
   Or manually via Gradle:
   ```bash
   ./gradlew :demo-web:wasmJsBrowserDistribution
   ```

2. Copy the compiled JS/Wasm artifacts into this folder:
   ```bash
   cp ../demo-web/build/dist/wasmJs/productionExecutable/demo-web.js .
   cp ../demo-web/build/dist/wasmJs/productionExecutable/demo-web.wasm .
   ```

3. Start a local static file server to serve the assets. Since WebAssembly modules must be served with correct MIME types, running a basic web server is required (do not just open the HTML file directly in your browser).
   
   Using Node's `http-server`:
   ```bash
   npx http-server -p 8083
   ```
   
   Or using Python:
   ```bash
   python3 -m http.server 8083
   ```

4. Open your browser and navigate to `http://localhost:8083`.
