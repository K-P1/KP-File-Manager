<!--
Path: docs/PROTECTED_EXPORT_VALIDATION.md
Purpose: Records protected export validation and remaining device checks.
Last changed: 2026-11-24, add fallback transport device checks.
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
