# KMP Terminal Emulator Coding Rules

When developing or refactoring files in this codebase, ensure the following style rules and architecture guidelines are strictly followed:

## Code Quality & Architecture
1. **Decoupling Core and UI**: Always keep `terminal-core` completely free of UI dependencies. It must contain only pure Kotlin multiplatform logic. Any UI-related classes, rendering canvas drawing routines, and layout metrics belong in `terminal-ui`.
2. **Platform Actuals**: Keep `actual` declarations simple and localized. For example, `TerminalInputCapture` is implemented via platform-native text inputs (views on Android, TextField in iOS/Wasm/JVM) to capture soft keyboard inputs correctly.
3. **Low Allocation rendering**: Monospace terminal grids have highly critical draw loops. Avoid allocating lists, streams, or wrappers inside `TerminalCanvas` draw loops (e.g., use raw indexes, pre-allocated char buffers, or `VirtualTerminalList`).

## Responsive Mobile Layouts
1. **Viewport Height Listeners**: Monospace rendering is highly sensitive to the virtual keyboard toggles on mobile viewports. Ensure height metrics are refreshed dynamically when available constraints change.
2. **Keyboard Toggle Handling**: Use the `onKeyboardToggleAvailable` callback inside the `TerminalRenderer` to handle showing/hiding soft keyboards explicitly when tapping on canvas areas.

## Tooling & Execution
1. **Use Mise**: Always use `mise` to run Gradle tasks to ensure JDK 21 and Android SDK configurations are correctly injected:
   ```bash
   mise run compile-wasm
   mise run run-demo
   ```
