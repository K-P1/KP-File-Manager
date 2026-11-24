<!--
Path: docs/PROTECTED_EXPORT_VALIDATION.md
Purpose: Records protected export validation and remaining device checks.
Last changed: 2026-11-24, document direct path-only undo validation and remaining device checks.
-->

# Protected Export implementation validation

Implementation date: 2026-11-23

## Performed in the provided source snapshot

- Ran `./gradlew :app:testDebugUnitTest :app:assembleDebug` against Android SDK 36. The full debug unit-test suite passed and the debug APK assembled successfully.
- Compiled the Protected Export UI and its AIDL-backed Shizuku UserService bridge against Shizuku API/provider 13.1.5.
- Added and passed a JVM regression test covering binary standard output, standard error and exit-code collection in the privileged command executor.
- Compiled the original KP media-organizer domain together with the new Protected Export planner using a local Kotlin compiler (Java 17 target).
- Executed five new Kotlin regression cases: multi-language subtitles across distinct source directories, occupied target conflicts, duplicate subtitle conflicts, video without subtitles, and strict path validation. All five passed.
- Compiled the new export repository with local stand-ins for unavailable Android services.
- Ran a live local Unix filesystem simulation of the export shell commands. A video and subtitle were copied, byte-verified, published into a shared movie folder, and restored to their sources.
- Simulated a subtitle copy failure after video copying. The group rolled back, the original files remained in place, and published targets were absent.
- Simulated a destination file created after the preview. The export rejected the collision without overwriting the new file.
- Locally type-checked the journal and foreground service layers with small Android API stand-ins.

## Not yet verified

- Android instrumentation and live Shizuku communication were not run because no device or emulator was used for this validation pass.
- Device-side operation on the fully updated POCO X8 Pro and its HyperOS/Android 16 access restrictions is **unverified**.
- Actual foreground process survival, system storage indexing, large-media I/O, recovery after power loss, and OS-specific access policies require on-device checks.

Before transferring irreplaceable downloads, build with `cd mobile && ./gradlew :app:testDebugUnitTest :app:assembleDebug`, run an end-to-end test with disposable files, try each restore and collision scenario, and confirm Shizuku can actually read the restricted source folder and write the public media library.

## 2026-11-24 compatibility follow-up

The compatibility fallback requires new device validation. On a device where UserService startup fails, scan and confirm the legacy transport works, then scan again and confirm there is no repeated startup delay. On a device where UserService works, confirm it remains the primary transport. Test cancellation during a disposable export, Shizuku disconnect/restart, failed legacy startup, source permission denial, release build, and undo. Confirm no command is retried after it has started and no original is deleted on a failed transfer. Earlier validation above does not establish that the new transport passes these checks.

## 2026-11-24 move-first and history follow-up

Previous validation does not validate this upgrade. The revised journal, repository and planner were type-checked with a local Kotlin compiler using Android/Shizuku/JSON API stand-ins; this is not an Android build or a runtime test of AtomicFile or Shizuku. Ten planner/history regression cases passed using the local Kotlin runner and assertion stand-ins, including reused planning, history selection, identity changes and completion progress. A disposable local Unix filesystem simulation using the real revised repository with stand-ins for Shizuku, Android AtomicFile and JSON serialization passed metadata-only link export, selective undo, verified-copy fallback (one full comparison), same-size modification rejection, interruption after publication, explicit recovery and verified reverse transfer. These checks do not validate Android AtomicFile durability, the actual JSON implementation, the Compose UI, Android storage behavior or device performance. The snapshot lacks the Gradle wrapper JAR and this environment does not have a configured Android SDK; build and run the actual Android test suite on the development machine.

Device checklist using disposable files:

- Confirm history records actual transfer method, duration, exact paths and file sizes. Test both native and fallback paths, including a device that rejects hard links. Time a large video before/after.
- Undo last export, an older session, one media group, and one file. Restart the app after export and restore; confirm restored records are retained and unrelated sessions are untouched.
- Upgrade with a v1 journal and verify migration is idempotent. Verify legacy warnings and duplicate handling. Do not delete the legacy journal manually.
- Create an occupied original path, same-size modified destination, symlink ancestor and destination collision; confirm no overwrite or unexpected deletion.
- Stop during copy, verification, publish and restore. Kill the app after publication and before journal completion. Reopen, inspect the history, then explicitly recover interrupted operations. Check partial group state and retained files.
- Disconnect Shizuku during transfer, then reconnect and recover. Test inaccessible folders and storage exhaustion. Verify no unknown files are removed.
- Confirm same-directory publication preserves the recorded identity on the target Android firmware. Check indexing and cleanup of empty public subfolders. Do not use irreplaceable media until this passes.

## 2026-11-24 temporary unrecorded recovery follow-up

Four local Kotlin recovery model regression cases passed (flat video paths, subtitle mapping, rejection of unsafe/out-of-scope files, and blocked-candidate selection). A disposable Unix filesystem simulation of the revised repository using Android/JSON/Shizuku stand-ins passed Movies-only discovery, metadata-only return, verified-copy return with one comparison, occupied and duplicate target blocking, retained unsupported and TV files, stale preview rejection, retained empty folders and reconstructed session history. This is not an Android build or device validation.

The temporary scanner has since been removed; its former device checklist is superseded by the history-only undo checks below.

## 2026-11-24 undo path guard follow-up

The device screenshots show exit 30 from the ancestor/leaf path guard and exit 130 on a pending restore. The screenshots do not establish which guard predicate failed or which operation was interrupted. Removed the independent execute-access probe, retaining symlink and non-directory checks, non-clobber publication and identity checks. Added path and undo-stage diagnostics.

Local Kotlin compilation with Android/JSON/Shizuku stand-ins and two new guard-generation tests passed. A disposable Unix filesystem simulation with execute probes forced to fail and the first copy forced to return 130 passed v1 journal migration, continuation of the batch, explicit pending recovery and exact-name retry with one full comparison per successful copy. Actual shell commands rejected symlink and non-directory ancestors with path-specific diagnostics. This is not a reproduction of HyperOS storage behavior. Android build and device verification remain required. On the device, preserve application data, close MovieBox, recover interrupted transfers, test undo of one migrated file, then undo the remaining session if successful.

## 2026-11-24 temporary recovery removal

Verify that the Export tab no longer offers Movies recovery scanning or selection. Existing sessions, selective undo, interrupted-transfer recovery and the latest path-guard diagnostics must remain available. Existing reconstructed mappings must still be validated and labeled accurately. Install over the existing app without clearing its data. Android build and device verification remain required.


## 2026-11-24 interrupted undo and missing-path diagnostics

The latest device screenshot reports a copy returning 130 followed by many missing exported-path reports. This does not establish whether the paths actually disappeared or Shizuku lost visibility. The previous metadata probe used shell test predicates that could misclassify access failures as absence. The revised probe uses checked stat with C locale, differentiates explicit not-found diagnostics from other failures, rejects non-regular files, and checks both journal paths together. Copy progress polling no longer launches concurrent Shizuku commands. Undo stops at the first failure, and the UI does not assert that a published copy exists when its presence has not been verified.

Local Kotlin core type-checking with Android/JSON/Shizuku stand-ins and 17 planner/history tests passed. A disposable Unix filesystem simulation passed interrupted legacy undo (exit 130), no later file attempts, explicit pending recovery, exact original filename restoration, single comparison per completed copied file, and checked metadata for absent files, empty files, directories and simulated permission denial. Symlink and non-directory ancestor rejection also passed. These results are not proof of Android stat output compatibility, actual Shizuku transport behavior or the cause of the device interruption. Full Android build and device validation remain outstanding. Test recovery of pending files, then undo one migrated record before retrying a session. If a recorded path is not visible, inspect both reported paths and the Shizuku service before moving more files. Do not clear app data.


## 2026-11-24 journal-name and metadata follow-up

The device screenshots show an exported path with the standardized filename (`13 Hours.mp4`), its exact original path (`13_Hours_1080P.mp4`) and `Invalid file metadata` at the path/identity check, before transfer. They do not show the raw stat output. The previous parser required decimal digits only for device and inode IDs. The updated parser supports signed, hexadecimal and padded IDs while preserving exact metadata for mutation and recovery checks. Invalid metadata now includes its path and bounded raw output. If both recorded paths are absent, only a unique name/size match in the recorded export directory can be resolved; other journal paths and identity mismatches are excluded. No temporary scanner or global Movies search is introduced.

Local Kotlin compilation with Android/JSON/Shizuku stand-ins and 20 planner/history regression cases passed. A disposable Unix filesystem simulation with a signed device ID in stat output verified exact standardized-name restoration, a uniquely renamed file in its recorded folder, persistent correction of the exported path, no transfer of ambiguous or unrelated files, and one comparison per copied file. These are not HyperOS tests. The actual malformed device payload and exit 130 cause are still unconfirmed. Preserve app data, recover pending transfers, then test undo of one migrated record before undoing a session. If the metadata error persists, capture its full new message (path and raw payload) before further retries.


## 2026-11-24 device stat framing follow-up

The device screenshot reports the actual row `regular file|185:370708:360916857:2026-11-24 01:05:17.263565939 +0000` for the recorded exported path `/storage/emulated/0/Movies/Forever My Girl/Forever My Girl.mp4`. The previous decoder expected only the identity payload after the pipe. The new command deliberately emits the complete typed row, the decoder removes the recognized regular-file prefix, and mutation-time identity checks compare against the complete typed row. Prefix-free persisted identities remain compatible; no journal migration is needed. The reason the previous shell pattern removal failed on this device has not been established.

Local Kotlin compilation with Android/JSON/Shizuku stand-ins and 22 planner/history regression cases passed, including the exact device payload, old prefix-free identities, empty regular files and rejection of non-regular types. Disposable Unix filesystem simulations passed typed metadata checks, exact original-name and renamed-file restoration, ambiguous-name rejection, single-comparison fallback, an injected copy exit 130, fail-fast behavior, explicit pending recovery and retry, and rejection of changed metadata. These do not validate actual HyperOS behavior, Shizuku transport, or journal durability. The earlier device copy interruption remains unresolved. Install without clearing app data, reconcile pending transfers, and try one migrated undo before the session. Capture any new error and stop if recovery cannot reconcile a record.

## 2026-11-24 direct path-only undo

The device reached the old copy comparison stage (exit 37). At the user's request undo now uses the journal's two paths and ordinary non-clobber `mv -n -T`, without stat parsing, size checks or content comparisons. The export engine is unchanged. Prior pending restore records remain selectable; retained partial paths are listed rather than deleted. Explicit recovery can mark an original-only restore complete based on visibility alone, not content verification. Test disposable files first, including occupied originals, apostrophes and spaces, interrupted moves, retained partials, and exact-name restoration. Android build and HyperOS validation remain required.

Local Kotlin core compilation with Android/Shizuku/JSON stand-ins and all 25 planner/history regression cases passed. A disposable Unix filesystem simulation using the revised repository passed direct restore, normalized filename fallback, exact original names with spaces/apostrophes, retry of old pending records with invalid metadata, retention of old partials, occupied-original protection, zero content comparisons, and explicit original-only interrupted-move reconciliation. Actual Android storage, shell transport, Compose UI and cross-filesystem behavior were not tested.
