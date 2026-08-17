# Contributing to KMP Terminal Emulator

Thank you for your interest in contributing to `kmp-terminal-emulator`! We welcome contributions, bug fixes, architecture improvements, and new platform targets.

---

## 1. Code of Conduct

All contributors are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md).

---

## 2. Architecture & Design Principles

When writing or refactoring code in this repository, keep the following core architectural invariants in mind:

### Decoupling `terminal-core` and `terminal-ui`
- **`terminal-core`**: Pure Kotlin Multiplatform logic only. It must **never** depend on Android views, Compose UI, Skia graphics, or UIKit. All state representation, ANSI/VT escape parsing, scrollback buffers, coordinate indexing, and configuration models live here.
- **`terminal-ui`**: Compose Multiplatform rendering layer. Contains `TerminalCanvas`, `TerminalRenderer`, gesture detectors, soft keyboard input capture, key bar components, and rendering caches.

### Low Allocation Draw Loops
The terminal grid canvas rendering loop runs at 60–120 FPS. To prevent GC pauses and frame drops:
- Avoid instantiating temporary objects, iterators, or collection wrappers inside draw loops.
- Use primitive indexing, pre-allocated char buffers, and reusable draw batches.

### Responsive Mobile & Soft Keyboard Handling
- Maintain viewport constraints dynamically when virtual keyboards open/close on Android and iOS.
- Use `TerminalInputCapture` actuals for platform-specific text input capture.

---

## 3. Development Toolchain & Environment Setup

This project uses [mise-en-place](https://mise.jdx.dev) to manage dev toolchains reproducibly (JDK 21, Node.js, Android SDK).

### Prerequisites
- [mise](https://mise.jdx.dev) installed on your system.

### Initializing Environment
```bash
# Clone the repository
git clone https://github.com/JohNan/kmp-terminal-emulator.git
cd kmp-terminal-emulator

# Install toolchains via mise
mise install
```

---

## 4. Building, Linting & Testing

### Running Tests
```bash
# Run all JVM unit tests
mise exec -- ./gradlew jvmTest

# Run all multiplatform unit tests
mise exec -- ./gradlew check
```

### Code Formatting & Linting (KtLint)
Code style is enforced via `ktlint`. All contributions must pass `ktlintCheck` cleanly before merging:
```bash
# Check code style
mise exec -- ./gradlew ktlintCheck

# Automatically format code
mise exec -- ./gradlew ktlintFormat
```

### Building Demo Applications
```bash
# Compile WebAssembly target and bundle
mise run compile-wasm

# Run Desktop JVM Demo
mise run run-jvm

# Run WebAssembly browser demo
mise run run-demo
```

---

## 5. Submitting Changes & Invariants

### Synchronized Documentation
Whenever public APIs, configuration structures, or capabilities change, it is **mandatory** to update:
1. `README.md`: Code samples and feature descriptions.
2. `MIGRATION.md`: Recipe-driven before/after migration diffs.
3. `llms.txt`: Machine-readable LLM sitemap and API index.

### Pull Request Guidelines
1. Create a descriptive branch from `main`:
   ```bash
   git checkout -b feature/my-feature
   ```
2. Commit with conventional commit messages (`feat:`, `fix:`, `refactor:`, `docs:`, `perf:`).
3. Ensure test coverage remains >= 80% on core terminal logic.
4. Verify all tests and linters pass locally:
   ```bash
   mise exec -- ./gradlew ktlintCheck jvmTest compileKotlinWasmJs
   ```
5. Open a Pull Request on GitHub against `main`. All CI checks must pass green.
