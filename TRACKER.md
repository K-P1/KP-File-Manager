# Implementation tracker

## 2026-07-20 — Repository organization

- Changed: separated the desktop application, configuration, packaged binary, and runtime logs into purpose-specific directories; converted desktop source into an importable package.
- Why: remove mixed source/generated/distribution files at the repository root and make room for the Android application.
- Files: `desktop/`, `.gitignore`, `README.md`, `docs/FEATURE_PARITY.md`.
- Checks: `desktop/run_checks.bat` compiles the package and runs the desktop regression suite without requiring installation.
- Attention: the archived Windows executable predates this layout and must be rebuilt for a future desktop release.
