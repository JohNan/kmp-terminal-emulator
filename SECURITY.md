# Security Policy

## Supported Versions

We provide security updates and patches for the following versions of `kmp-terminal`:

| Version | Supported          |
| ------- | ------------------ |
| 0.2.x   | :white_check_mark: |
| < 0.2.0 | :x:                |

---

## Reporting a Vulnerability

The `kmp-terminal-emulator` team takes security and user safety seriously. If you discover a security vulnerability, please report it responsibly:

1. **Do not create a public GitHub issue.**
2. Report the vulnerability privately via **GitHub Security Advisory** on the repository page:
   - Navigate to the **Security** tab of [JohNan/kmp-terminal-emulator](https://github.com/JohNan/kmp-terminal-emulator).
   - Click **Report a vulnerability** to open a draft advisory.
3. Include detailed information:
   - Description of the vulnerability.
   - Steps to reproduce or proof-of-concept payload (e.g. malformed ANSI sequence, OSC escape exploit, buffer overflow).
   - Potential impact and affected platforms (Android, iOS, JVM, WasmJs).

### What to Expect
- **Acknowledgment**: Within 48 hours.
- **Triage & Fix**: We will investigate and develop a patch in a private fork.
- **Disclosure**: Once a fix is verified and released, a security advisory will be published with attribution to the reporter.
