# KP File Manager

KP File Manager is organized as a small monorepo with desktop and Android applications that share the same user-facing feature set.

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

## Capabilities

- Sort files and folders into configurable categories.
- Group similarly named TV/series files by title.
- Batch rename sequentially or with unique scrambled numbers.
- Rename music from embedded track, artist, and title metadata.
- Replace keywords across matching filenames.
- Preview changes before execution.
- Show operation results and undo the last completed operation during the current session.

See [docs/FEATURE_PARITY.md](docs/FEATURE_PARITY.md) for platform details and Android storage constraints.
