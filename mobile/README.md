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

## Use

1. Choose a source folder using Android’s system picker.
2. Select Sort, Batch, Music, or Replace.
3. Configure the visible options.
4. Tap Preview and review the proposed changes.
5. Tap Run changes.
6. Use Undo before closing the app if you need to restore the last completed operation.

The destination defaults to the source folder for sorting. Folder grants are persisted by Android, while undo history intentionally remains session-only to match the desktop app.

## Storage limitations

On Android 11 and newer, the system does not allow apps to select storage roots, the `Download` root, `Android/data`, or `Android/obb`. A document provider may decline a rename or move. When native move is unavailable, the app attempts a copy/delete fallback and reports failures without hiding successful items.
