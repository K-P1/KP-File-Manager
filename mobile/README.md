# KP File Manager for Android

This is a native, single-activity Android app built with Kotlin and Jetpack Compose. It uses the system Storage Access Framework, so it requires no root access, broad storage permission, network access, or companion file-manager app.

## Requirements

- Android Studio with JDK 17 or newer.
- Android SDK 36.
- Android 6.0 (API 23) or newer device.

## Build and verify

From this directory:

```powershell
run_checks.bat
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Core workflow

1. Choose a source folder using Android’s system picker.
2. Select Organize, Batch Rename, or Find & Replace.
3. Configure the scan or filename options.
4. Generate and review the complete plan.
5. Apply the retained plan only after conflicts are resolved.
6. Use Undo before closing the app to restore successful changes.

Media organization recursively scans to a configurable depth (default 4), detects movies, TV episodes, subtitles, artwork, and NFO metadata, then proposes normalized `Movies/` and `TV Shows/` paths. Scanning and preview never mutate files. Folder grants are persisted by Android, while undo history intentionally remains in memory for the current app session.

Batch Rename and Find & Replace use the same preview, conflict detection, overwrite protection, execution, and undo infrastructure. Generic category sorting and MP3/music cleanup are no longer part of the Android application.

## Storage limitations

On Android 11 and newer, the system does not allow apps to select storage roots, the `Download` root, `Android/data`, or `Android/obb`. A document provider may decline a rename or move. When native move is unavailable, the app attempts a copy/delete fallback and reports failures without hiding successful items.
