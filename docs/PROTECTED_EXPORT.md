<!--
Path: docs/PROTECTED_EXPORT.md
Purpose: Describes protected export behavior, safety and device validation.
Last changed: 2026-11-24, document direct path-only undo and retained partials.
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

## Transfer safety and performance

1. Check Shizuku authorization and actual source readability. Discover files and batch file sizes. Parse and organize once, then apply occupied-target conflicts to the existing plan.
2. Revalidate all selected targets, check source identities and sizes in batches, and prepare unique destination directories. Reject symlink ancestors, unsafe paths and occupied destinations. Do not require a separate execute-permission probe on every ancestor: Android emulated-storage access probes may disagree with direct checked filesystem operations. Symlink and non-directory checks remain, and actual operations must succeed before completion. Path guard errors identify the rejected path. Undo failures also identify the transfer stage.
3. Persist the entire media group's pending source/destination mappings in an atomic session file before moving anything. Sessions retain file, group, size, method, timestamps, state and error details.
4. Try a native non-clobber rename only when `mv` advertises `--no-copy`. Otherwise try a hard-link and unlink metadata-only transfer (`ln -T`, identity checks, `rm`). If that fails with both paths confirmed unchanged, disable the link attempt for the rest of that operation and use verified copying. Android emulated storage may reject both fast paths. Ordinary `mv` across mounts is not treated as a native move because it may silently copy and delete.
5. The fallback copies into a unique journaled temporary file, checks its size and compares its full contents once. Record the verified temporary identity before publication. Publish using non-clobber `mv -n -T` within the destination directory, confirm the temporary disappeared and the published identity matches, and remove the source only if its device, inode, size and modification time still match. No second unconditional byte comparison is performed. Metadata checks are not a cryptographic checksum and cannot protect against a deliberately concealed edit or all external filesystem races. If metadata cannot be confirmed, keep the files and flag recovery.
6. Copies no longer launch a concurrent Shizuku command to poll their temporary file size. The copying stage remains visible until the copy returns; current-file byte progress updates at copy completion, while overall progress tracks completed transfers. Moving, copying, verifying and publishing remain distinct stages. Cancellation requests stop the active command and do not authorize publication. Journal completions are grouped where possible; verification boundaries remain durable. Older sessions are not rewritten by new transfers.
7. Media groups are preflighted together but file moves are not a filesystem-wide atomic transaction. If a later companion fails or the operation is interrupted, earlier successful files remain in history and uncompleted files remain pending. No speculative group rollback occurs. Only journaled partials with matching intact origins are eligible for explicit cleanup.
8. Media scanning refreshes affected paths. Undo removes empty public media subfolders using `rmdir` only; the public and protected roots remain intact.

## History, undo and interrupted transfers

The Export tab exposes history even when a scan preview is open. Expand a session to see every media group, original path, exported path, file size, method, state, timestamps and errors. Undo the most recent session, a selected session, a media group, or an individual file. Restored records remain visible. Both exported and interrupted restore records can be selected for undo.

Undo is deliberately a direct path-based move, not the verified export engine. Use the exact recorded exported path as the source and the exact original path (including its original filename) as the destination. If the former is not visible, search only its recorded directory for a unique regular file matching either known filename after case and punctuation normalization; exclude paths claimed by other records. Reconstructed mappings are not remapped. No stat identity parsing, size validation, byte comparison or app-managed copy/delete fallback is used for undo. The current file is trusted as selected by the user. The confirmation explains this trade-off. Close MovieBox and stop other file operations before restoring.

The app checks path scope, regular-file visibility, symlinks, occupied originals and competing journal records, saves pending intent, creates the original parent folder and invokes `mv -n -T` once. The system move may internally copy across filesystems. The app records completion only when the command succeeds and the exported path is no longer visible while the original is a regular file. No intermediate rename is required. Existing originals are never intentionally replaced or deleted; if both copies exist, stop and inspect both. Undo stops at the first failed record. Previous successful records remain committed. Cancellation stops between files; the current system move may finish before cancellation is observed.

Interrupted restore records with a visible exported file and absent original can be retried directly. Old restore partials are retained and listed for manual review rather than deleted. Explicit **Recover interrupted transfers** uses path visibility for restores: exported-only returns the record to the undoable state, original-only marks it restored without verifying bytes, and both/neither require manual review. Review the actual files before confirming; path visibility cannot prove content identity or rule out hidden files. Export recovery retains its existing metadata-based verification and cleanup rules. Pending paths claimed by other records cannot be reused. The v1 journal remains compatible. App data removal deletes history; history is not a backup of media bytes.

History is stored in private `noBackupFilesDir/protected_export_sessions/` as atomic per-session JSON files (version 2). The v1 file is retained unchanged as a migration source; the committed `legacy-v1.json` prevents repeat migration. An interrupted or unreadable journal is not silently reset.

## Previously reconstructed records

The temporary unrecorded Movies recovery scanner and adoption controls have been removed. Undo now uses existing journal mappings only. Previously reconstructed records remain readable and recoverable through normal history and interrupted-transfer recovery; their targets are labeled as recovery targets rather than exact original paths. No journal migration or deletion is needed.

## Running and validation

Run the Android project from `mobile/`. With a working Android SDK and Gradle, run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`. The protected export feature requires the Shizuku app and an active Shizuku service. The code uses `dev.rikka.shizuku:api:13.1.5` and `provider:13.1.5`.

On a fully updated POCO X8 Pro, start Shizuku using wireless debugging and grant KP File Manager access when requested. POCO/HyperOS Android 16 builds may stop or block the Shizuku service, especially across reboots. A successful permission grant does not prove that the restricted MovieBox folder can be read. Run the scanner on the device to verify. Some firmware combinations may prevent the operation.

The primary command transport is a Shizuku UserService. If that service cannot start, KP tries the legacy `IShizukuService.newProcess` interface exposed by the pinned Shizuku 13.1.5 AIDL dependency. This compatibility path uses the same authorized Shizuku server and is cached for that server connection; it does not grant additional permissions. No already-started command is replayed. The legacy interface is deprecated and may be removed in a future Shizuku server release. Both transports are isolated in `ShizukuShell.kt`.

If authorization succeeds but scanning still fails, inspect Shizuku's service logs for `ShizukuServiceStarter`, `UserServiceManager`, or `KpShizukuShell`. Android 16/HyperOS and some MediaTek firmware have reported UserService startup crashes. The fallback may avoid that startup path, but neither transport can guarantee that the OS permits the shell UID to access the source. If both fail, restart Shizuku and KP, retry the scan, and capture the device logs. The connection card reports authorization only; a successful scan is the filesystem access check.

**Device verification is essential:** test a movie and episode with multiple subtitles, native move availability, fallback copying and visible byte progress, collisions and duplicate subtitle names, a file still being downloaded, storage exhaustion, the Shizuku binder disconnecting mid-transfer, interrupted copies, process termination, cleanup and undo/restore. Only after these tests should it be considered verified on a POCO X8 Pro.

## Known boundaries

The source package and output root are deliberately fixed. Arbitrary app-private folders and SAF destination URIs are out of scope. Export cleanup can leave a verified destination copy alongside an original. Export metadata identity must remain stable across same-directory publication. Direct undo does not verify content and depends on the platform move implementation and path visibility. A failed cross-filesystem move may leave both paths occupied, requiring manual review. Clearing KP File Manager's app data will remove its recovery journal even though exported files remain on disk. This operation is not a persistent background service, but the journaled partial files remain tracked for explicit recovery.

## Foreground transfers

Long exports and restoration start a `dataSync` foreground service and show a persistent low-priority notification with current file and progress. This helps on POCO/HyperOS background management. The actual transfer is controlled by the lifecycle-aware view model, not by a persistent worker: if Android terminates the process anyway, the recovery journal records intent, export verification identities, and direct-restore paths for reconciliation. The journal is stored in the app's `noBackupFilesDir` to prevent accidental cross-device restoration of absolute paths.
