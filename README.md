# KP File Manager

KP File Manager is organized as a small monorepo with desktop and Android applications. The Android application is being refactored into the focused movie and TV library organizer described in `docs/refactor_plan.md`.

## Repository layout

```text
KP-File Manager/
├── desktop/                 Python desktop application and packaged Windows build
│   ├── config/              File-category rules
│   ├── dist/                Existing packaged executable
│   ├── runtime/             Local operation logs (ignored by Git)
│   ├── src/kp_file_manager/ Desktop source package
│   └── tests/               Desktop regression tests
├── mobile/                  Native Android application
├── docs/                    Architecture and feature-parity notes
└── TRACKER.md               Concise implementation log and verification status
```

## Desktop app

See [desktop/README.md](desktop/README.md) for installation, GUI, and CLI instructions.

## Android app

See [mobile/README.md](mobile/README.md) for build and device instructions. The mobile app uses Android's system folder picker, so it needs neither root access nor a companion file-manager app.

## Android capabilities

- Recursively scan and classify movies, TV episodes, subtitles, artwork, and metadata.
- Parse release-style names and group matched media companions.
- Preview normalized `Movies/` and `TV Shows/` destinations without changing files.
- Detect blocking conflicts and never silently overwrite existing files.
- Batch rename and replace filename text through the same safe plan/execution layer.
- Undo successful changes from the latest operation during the current session.

See [mobile/README.md](mobile/README.md) for the Android workflow and storage constraints.
