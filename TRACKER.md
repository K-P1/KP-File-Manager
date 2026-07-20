# Implementation tracker

## 2026-07-20 — Repository organization

- Changed: separated the desktop application, configuration, packaged binary, and runtime logs into purpose-specific directories; converted desktop source into an importable package.
- Why: remove mixed source/generated/distribution files at the repository root and make room for the Android application.
- Files: `desktop/`, `.gitignore`, `README.md`, `docs/FEATURE_PARITY.md`.
- Checks: `desktop/run_checks.bat` compiles the package and runs the desktop regression suite without requiring installation.
- Attention: the archived Windows executable predates this layout and must be rebuilt for a future desktop release.

## 2026-07-20 — Native Android application

- Changed: added a Kotlin/Compose Android app with category and series sorting, sequential/scrambled batch rename, metadata-based MP3 rename, keyword replacement, preview, internal logs, per-item results, and session undo.
- Why: provide the desktop workflow on mobile without root, broad storage permission, uploads, or companion apps.
- Files: `mobile/`, `docs/FEATURE_PARITY.md`, root `README.md`.
- Checks: 6/6 Android planner tests passed; `lintDebug` passed with 0 errors (8 dependency/KTX advisories); `assembleDebug` produced an 11,694,622-byte APK; package inspection confirmed min SDK 23, target SDK 36, and no broad storage permission. Desktop checks also passed 3/3.
- Attention: Android’s system picker and each document provider control which folders and mutation operations are available. Copy fallback can change timestamps when native move is unsupported. No emulator profile or physical device was connected, so on-device interaction remains the only skipped smoke check.

## 2026-07-21 - Android batch-rename recovery and performance

- Changed: batch rename now renames each item directly, reserves collision-free target names from one folder listing, and recognizes interrupted `.kp_<uuid>_<extension>` temporary names as their original file type when planning a new batch operation.
- Why: prevent files from being stranded as hidden temporary files after a provider failure and reduce a 256-file batch from roughly 512 provider rename calls plus repeated name lookups to 256 renames and one initial listing.
- Files: `mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt`, `mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt`, `mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt`, `mobile/app/src/test/java/com/kp/filemanager/domain/OperationPlannerTest.kt`.
- Checks: added and passed the interrupted-temporary-file extension regression test; `testDebugUnitTest lintDebug assembleDebug` passed with Android Studio JBR and Android SDK configured. Debug APK rebuilt.
- Attention: on-device verification remains needed because each Android document provider implements rename behaviour independently. The repository's `run_checks.bat` requires its bundled JBR when the shell already exports an incompatible JDK.

## 2026-07-20 - Self-guided file operation UI

- Changed: replaced promotional and ambiguous interface copy with numbered workflow steps, concise operation descriptions, selected-folder references, explicit review/apply actions, and visible in-progress text with loading bars.
- Why: make both clients understandable from the screen itself and keep users informed while an operation is running.
- Files: `desktop/src/kp_file_manager/gui_qt.py`, `mobile/app/src/main/java/com/kp/filemanager/ui/`, `mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt`.
- Checks: `desktop/run_checks.bat` passed 3/3 tests. `mobile/run_checks.bat` passed unit tests, lint, and debug assembly when run with Android Studio JDK (the shell’s JDK 25 is unsupported by this Gradle/Kotlin combination).
- Attention: desktop progress is indeterminate because the existing file engines do not expose per-item progress callbacks. Android displays the system picker’s opaque SAF URI as the granted folder location because Android does not provide a filesystem path for a document tree.
