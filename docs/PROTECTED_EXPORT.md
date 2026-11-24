<!--
Path: docs/PROTECTED_EXPORT.md
Purpose: Describes protected export behavior, safety and device validation.
Last changed: 2026-11-24, document the authorized legacy fallback and startup troubleshooting.
-->

# Protected Media Export: KP File Manager Mobile

Protected Media Export is an optional tab inside KP File Manager for moving MovieBox downloads into the public media library. The desktop utilities and existing SAF organizer are unchanged.

## Input and output

Sources, read using Shizuku:

- Video: `/storage/emulated/0/Android/data/com.community.oneroom/files/Download/d` (top level only)
- Subtitles: `/storage/emulated/0/Android/data/com.community.oneroom/files/Download/subtitle` (recursive)

Outputs in general device storage:

- `/storage/emulated/0/Movies/Film (2026)/Film (2026).mkv`
- `/storage/emulated/0/Movies/Film (2026)/Film (2026).eng.srt`
- `/storage/emulated/0/TV Shows/Series/Season 01/Series - S01E01.mp4`
- `/storage/emulated/0/TV Shows/Series/Season 01/Series - S01E01.eng.srt`

These are examples. Real names use the existing KP `MediaNameParser`, `MediaGrouper`, `MediaOrganizationPlanner` and `MediaPathFormatter`. Ambiguous or conflicting groups are blocked from export. Unmatched subtitles stay at the source. The user can select individual media groups and review the exact destination for each file.

## Transfer safety

1. Test the Shizuku binder and authorization, then check actual readability of the restricted video folder. Shizuku permission alone does not guarantee read access.
2. Discover files using NUL-delimited shell `find` and batch file sizes with `stat`. Index, parse and organize with KP's existing pipeline. Check destination collisions before preview and again immediately before export.
3. For each selected media group, check that its source sizes have not changed, create the destination directories and start unique `.kp-export-<uuid>.partial` copies. A pending-temp record is atomically committed in private app storage before the copy begins.
4. Show progress based on the size of the file currently being copied, polling approximately every 450 ms. Display the exact file and active phase: scan, parse, copy, verify, publish, cleanup or restore.
5. Check the completed temporary file's length and byte-for-byte equality with the original. All subtitle and video files in the group must pass validation before promoting any file.
6. Publish each verified file using `mv -n`; verify that the temporary file no longer exists and that the destination exists. No existing file should be overwritten. The caller checks for a race or collision at every promotion.
7. Atomically write the published source/destination mapping to the recovery journal **before** removing any original files. Recheck equality before removal. If deletion fails or the original has changed, preserve both and report a cleanup warning.
8. On an interrupted or killed operation, the next scan removes only journaled `.kp-export-*.partial` and `.kp-restore-*.partial` files with approved paths. The app never sweeps or deletes unknown files, and never deletes MovieBox's source directories.
9. Restore is copy-first: copy a published file back to a temporary source path, verify it, publish it only if the original does not already exist, check equality and only then remove the published file. If anything goes wrong, keep the published copy and the journal entry for retry.
10. Clean up empty public media *subfolders* using `rmdir`. Leave the `Movies`, `TV Shows`, `d` and `subtitle` roots intact.

## Running and validation

Run the Android project from `mobile/`. With a working Android SDK and Gradle, run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`. The protected export feature requires the Shizuku app and an active Shizuku service. The code uses `dev.rikka.shizuku:api:13.1.5` and `provider:13.1.5`.

On a fully updated POCO X8 Pro, start Shizuku using wireless debugging and grant KP File Manager access when requested. POCO/HyperOS Android 16 builds may stop or block the Shizuku service, especially across reboots. A successful permission grant does not prove that the restricted MovieBox folder can be read. Run the scanner on the device to verify. Some firmware combinations may prevent the operation.

The primary command transport is a Shizuku UserService. If that service cannot start, KP tries the legacy `IShizukuService.newProcess` interface exposed by the pinned Shizuku 13.1.5 AIDL dependency. This compatibility path uses the same authorized Shizuku server and is cached for that server connection; it does not grant additional permissions. No already-started command is replayed. The legacy interface is deprecated and may be removed in a future Shizuku server release. Both transports are isolated in `ShizukuShell.kt`.

If authorization succeeds but scanning still fails, inspect Shizuku's service logs for `ShizukuServiceStarter`, `UserServiceManager`, or `KpShizukuShell`. Android 16/HyperOS and some MediaTek firmware have reported UserService startup crashes. The fallback may avoid that startup path, but neither transport can guarantee that the OS permits the shell UID to access the source. If both fail, restart Shizuku and KP, retry the scan, and capture the device logs. The connection card reports authorization only; a successful scan is the filesystem access check.

**Device verification is essential:** test a movie and episode with multiple subtitles, large-file copying and visible byte progress, collisions and duplicate subtitle names, a file still being downloaded, storage exhaustion, the Shizuku binder disconnecting mid-transfer, interrupted copies, process termination, cleanup and undo/restore. Only after these tests should it be considered verified on a POCO X8 Pro.

## Known boundaries

The source package and output root are deliberately fixed. Arbitrary app-private folders and SAF destination URIs are out of scope. A cleanup failure can leave a verified destination copy alongside an original, intentionally favoring retaining files over destructive cleanup. Clearing KP File Manager's app data will remove its recovery journal even though exported files remain on disk. This operation is not a persistent background service, but the journaled partial files will be handled at the next scan.

## Foreground transfers

Long exports and restoration start a `dataSync` foreground service and show a persistent low-priority notification with current file and progress. This helps on POCO/HyperOS background management. The actual transfer is controlled by the lifecycle-aware view model, not by a persistent worker: if Android terminates the process anyway, the recovery journal protects the source files and permits safe rescan or restore. The journal is stored in the app's `noBackupFilesDir` to prevent accidental cross-device restoration of absolute paths.
