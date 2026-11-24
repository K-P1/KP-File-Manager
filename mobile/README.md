<!--
Path: mobile/README.md
Purpose: Android setup, features and validation instructions.
Last changed: 2026-11-24, update protected export performance and undo documentation.
-->

# KP File Manager for Android

This is a native, single-activity Android app built with Kotlin and Jetpack Compose. Its regular file-manager features use the system Storage Access Framework without root or broad storage permission. The optional Protected Export feature uses the Shizuku companion service to access a restricted MovieBox download directory.

## Requirements

- Android Studio with JDK 17 or newer.
- Android SDK 36.
- Android 7.0 (API 24) or newer device.

## Build and verify

From this directory:

```powershell
run_checks.bat
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Core workflow

1. Choose a source folder using Android’s system picker.
2. Select Organize, Batch Rename, or Find & Replace.
3. For organization, keep the source folder as the default output root or choose another output root.
4. Optionally choose existing Movies and TV library folders directly; otherwise their configured paths are created or reused under the output root.
5. Configure the remaining scan or filename options.
6. Generate and review the complete plan.
7. Apply the retained plan only after conflicts are resolved.
8. Use Undo before closing the app to restore successful changes and remove empty folders created by the operation.

Media organization recursively scans to a configurable depth (default 4), detects movies, TV episodes, subtitles, artwork, and NFO metadata, then proposes normalized `Movies/` and `TV Shows/` paths. Existing destination folders are reused and missing paths are created. Direct library overrides place normalized content relative to the selected Movies or TV folder, avoiding duplicate library-name nesting. Scanning and preview never mutate files. Folder grants are persisted by Android, while undo history intentionally remains in memory for the current app session.

Undo restores files before it cleans destination folders. Only folders created by that operation are considered, and they are removed only while empty. If a provider temporarily refuses cleanup, the app keeps a cleanup-only retry available instead of attempting to restore the same file twice.

Batch Rename and Find & Replace use the same preview, conflict detection, overwrite protection, execution, and undo infrastructure. Generic category sorting and MP3/music cleanup are no longer part of the Android application.

## Storage limitations

On Android 11 and newer, the system does not allow apps to select storage roots, the `Download` root, `Android/data`, or `Android/obb`. A document provider may decline a rename or move. When native move is unavailable, the app attempts a copy/delete fallback and reports failures without hiding successful items.

## Protected Media Export (Shizuku)

The **Export** tab adds a protected-source MovieBox media exporter. It uses Shizuku for filesystem access and KP's existing media organization engine to sort selected movies, episodes and subtitles into public `Movies` and `TV Shows` folders. It provides file-by-file destination review, separate copy and completion progress, metadata-only moves where supported, verified-copy fallback, collision protection, persistent history and selective restore. See [`../docs/PROTECTED_EXPORT.md`](../docs/PROTECTED_EXPORT.md) for paths, setup, safety details and test procedures.
