<!--
Path: README.md
Purpose: Workspace overview and operating instructions.
Last changed: 2026-11-24, update protected export performance and undo documentation.
-->

# KP File Manager

KP File Manager is a two-application workspace for organizing and renaming files:

- **Desktop** is a Windows-oriented Python utility for category sorting, series grouping, batch renaming, music-name cleanup, and filename find-and-replace.
- **Android** is a native Kotlin/Jetpack Compose app focused on safely normalizing movie and TV libraries, with Batch Rename and Find & Replace retained as separate tools.

Both applications use a deliberate workflow: choose a folder, inspect the proposed changes, explicitly apply them, and undo the latest successful session operation when possible. They are separate implementations with different storage APIs and current feature scopes; do not assume a desktop feature is automatically available on Android.

## Contents

- [Choose an application](#choose-an-application)
- [Desktop application](#desktop-application)
- [Android application](#android-application)
- [Safety and undo](#safety-and-undo)
- [Repository layout](#repository-layout)
- [Development and verification](#development-and-verification)
- [Project documentation](#project-documentation)

## Choose an application

| Use case | Recommended application |
| --- | --- |
| Sort mixed local files into extension-based folders on Windows | Desktop |
| Group similarly named files into series folders on Windows | Desktop |
| Rename music files from local metadata or clean their filenames | Desktop |
| Organize a movie/TV download folder into a normalized library | Android |
| Work with folders selected through Android's system picker | Android |
| Batch-rename files or replace text in filenames | Desktop or Android |

## Desktop application

The desktop app is a Python package with a PyQt GUI, a retained Tk GUI, and a command-line entry point. It operates on normal local filesystem paths.

### What it does

- Sorts the direct contents of a selected folder into type-based category folders.
- Groups files that share a normalized series title when Series mode is used.
- Performs numbered batch renames with extension filters, prefixes, start values, and zero padding.
- Simplifies music filenames using MP3 tag information when available, with a filename-cleanup fallback.
- Replaces literal text in filenames, without changing their extensions.
- Supports a dry-run/review workflow, local operation logs, and best-effort undo during the active application session.

The default sorting rules live in [`desktop/config/categories.json`](desktop/config/categories.json). They cover Images, Documents, Audio, Video, Archives, and a fallback Others category. Existing target names are disambiguated rather than silently overwritten.

### Requirements

- Windows or another environment that can run the Python desktop dependencies.
- Python 3.9 or newer.

### Install and run the PyQt GUI

Run these commands from the repository root:

```powershell
Set-Location desktop
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -e .
kp-file-manager-gui
```

The older Tk-based interface remains available:

```powershell
python -m kp_file_manager.gui_tk
```

### Command line

After installation, inspect the available flags with:

```powershell
kp-file-manager --help
```

The CLI supports category sorting, an example mass-rename flow, music-name cleanup, dry runs, and session undo. For example, this previews sorting the current directory into `sorted/` without changing files:

```powershell
kp-file-manager --sort --source . --dest sorted --dry-run
```

Desktop operation details and errors are written to `desktop/runtime/operation_logs.txt`. That directory is local runtime state and is intentionally ignored by Git.

The existing packaged Windows executable under `desktop/dist/` is retained for archival distribution. It predates the current source layout and should be rebuilt before a new public desktop release.

For desktop-specific setup and checks, see [desktop/README.md](desktop/README.md).

## Android application

The Android app is a single-activity native application built with Kotlin and Jetpack Compose. Its primary feature is a media-library normalization workflow, not generic document or music sorting.

### Primary workflow

1. Select a source folder with Android's system folder picker.
2. Choose **Organize**, **Batch Rename**, or **Find & Replace**.
3. For media organization, review the output root. It defaults to the selected source folder, but you can choose another root.
4. Optionally select existing Movies and TV library folders directly, or set the folder names to create or reuse under the output root.
5. Scan and review the complete retained plan, including conflicts, warnings, skipped files, and proposed paths.
6. Resolve blocking conflicts and explicitly apply the plan.
7. Review the result and use **Undo** before closing the app if needed.

Scanning and previewing are read-only. The app does not mutate files until a reviewed plan is explicitly applied.

### Media organization

The organizer recursively scans to a configurable depth of **0-20** directories (default **4**). It performs one indexed scan, parses and groups media in memory, resolves the selected library context, builds a deterministic plan, then executes approved operations and safely cleans up verified-empty source folders.

The output root defaults to the source folder. Under that root the app reuses existing Movies and TV folders or creates the configured folder paths when they are missing. Optional direct Movies-library and TV-library selections support devices where Android's picker will not grant access to the libraries' shared parent; files are placed relative to those selected library folders without adding a duplicate library-name level.

It recognizes these file roles:

| Role | Supported extensions |
| --- | --- |
| Video | `mkv`, `mp4`, `avi`, `mov`, `m4v`, `webm` |
| Subtitle | `srt`, `ass`, `ssa`, `sub`, `vtt` |
| Artwork | `jpg`, `jpeg`, `png`, `webp` |
| Metadata | `nfo` |

The parser handles common release-style names, including season/episode forms such as `S01E01`, `1x01`, and `Season 1 Episode 1`; movie years; release tags; multi-episode files; subtitle languages; and qualifiers such as `forced`, `sdh`, `cc`, and `default`. Uncertain or ambiguous matches are surfaced for review instead of being guessed.

For a generic incoming folder, a typical plan produces this structure:

```text
Movies/
  Avatar (2009)/
    Avatar (2009).mkv
    Avatar (2009).en.forced.srt
    Poster.jpg
    Movie.nfo

TV Shows/
  Lost/
    Season 01/
      Lost - S01E01.mkv
      Lost - S01E01.en.srt
```

The organizer detects established Movies, TV, series, movie, and season folders to avoid redundant nesting such as `TV Shows/Series Name/TV Shows/Series Name`. Already-normalized paths are planned as unchanged where appropriate.

### Batch Rename and Find & Replace

Both Android file tools use the same plan, conflict detection, execution, and undo infrastructure as the organizer.

- **Batch Rename** supports an extension filter, prefix, suffix, starting number, digit padding, and optional extension replacement.
- **Find & Replace** performs literal filename text replacement or removal and can be limited to a single extension or applied to all file types.

Each tool requires a current preview before its apply action is available.

### Progress, results, and storage model

The organizer reports its phase, discovered items while scanning, exact post-scan counts, current item, moved/renamed/skipped/failed totals, cleanup count, and ETA when it is stable enough to be meaningful. Long-running storage work runs outside the Compose UI.

Android uses the Storage Access Framework (SAF). You grant access only to folders chosen in the system picker; the app requires no root access, broad-storage permission, network connection, or companion file manager. Folder grants can persist, but undo history intentionally remains in memory for the current app session.

On Android 11 and later, the system picker cannot grant access to a storage root, the `Download` root, `Android/data`, or `Android/obb`. A document provider can also refuse a move, rename, or deletion. The app prefers direct same-provider moves, caches unsupported provider capabilities, and falls back to a verified direct copy/delete only when necessary. The fallback can change timestamps because SAF has no portable timestamp-restore API.

### Requirements and build

- Android Studio with JDK 17 or newer.
- Android SDK 36.
- An Android 7.0 / API 24 or newer device or emulator for installation.

From the repository root:

```powershell
Set-Location mobile
.\run_checks.bat
```

This runs Android unit tests, lint, and a debug assembly. The debug APK is written to:

```text
mobile/app/build/outputs/apk/debug/app-debug.apk
```

For Android-specific setup and storage details, see [mobile/README.md](mobile/README.md).

## Safety and undo

KP File Manager is designed to make planned filesystem changes visible before they run, but filesystem state and provider permissions can still change after review. Keep backups of important media and inspect every plan before applying it.

- Previewing, scanning, and regenerating a plan do not modify files.
- Existing destinations and duplicate targets are conflicts; files are never silently overwritten.
- A failure for one Android item is reported without hiding successful items.
- The Android organizer revalidates operations and only deletes source folders confirmed empty; the selected root and destination library folders are protected.
- Undo is best effort, applies only to successful operations from the latest in-memory session, and may fail if a file was changed, moved, deleted, or blocked outside the app afterwards. After restoring files, it removes empty destination folders created by that operation; cleanup failures remain retryable without moving restored files again.
- Android storage-provider behavior is not uniform. Test the provider and folder type you intend to use before processing a valuable library.

## Repository layout

```text
KP-File Manager/
|-- desktop/                     Python desktop application
|   |-- config/                  Desktop category rules
|   |-- dist/                    Archived packaged Windows build
|   |-- runtime/                 Local operation logs (Git-ignored)
|   |-- src/kp_file_manager/     Python package and GUI/CLI entry points
|   `-- tests/                   Desktop regression tests
|-- mobile/                      Kotlin/Compose Android application
|   `-- app/                     Android app module, source, and tests
|-- docs/                        Product, UX, refactor, and implementation notes
|-- README.md                    This cross-project guide
`-- TRACKER.md                   Concise repository-level implementation record
```

## Development and verification

Use the check script in the application you changed:

```powershell
# Desktop
Set-Location desktop
.\run_checks.bat

# Android
Set-Location mobile
.\run_checks.bat
```

The desktop script compiles the package and runs its regression suite without requiring installation. The Android script runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`; it prefers Android Studio's bundled JBR when present so an unrelated global JDK does not prevent Gradle from starting.

Generated Python environments, Android/Gradle build output, local SDK configuration, and runtime logs are ignored by Git. Do not commit credentials, local storage URIs, APKs, or build artifacts.

The Android app now includes a **Protected Export** tab for Shizuku-powered MovieBox video/subtitle exporting. It reuses KP's media planner to sort files into shared Movies and TV Shows libraries, with move-first transfers, verified-copy fallback and persistent selective undo. See [protected export details](docs/PROTECTED_EXPORT.md).
