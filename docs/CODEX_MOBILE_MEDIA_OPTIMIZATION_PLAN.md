# Codex Implementation Plan: Mobile Media Workflow Optimization

## Objective

Refactor and optimize the media-organization workflow **in one implementation pass**. Do not stop after auditing or produce a separate findings report. Inspect the existing code, apply the changes below as issues are confirmed, run validation, and leave only concise maintenance notes where useful.

The end state must be faster, non-blocking, storage-efficient, and predictable for large media libraries.

## Non-Negotiable Constraints

- Do not create temporary full-file copies.
- Prefer same-provider **move/rename** operations.
- Use copy-and-delete only when the Storage Access Framework/provider cannot move the document directly.
- Never perform filesystem/provider work on the main thread.
- Do not add extra full-tree scans just to calculate progress or cleanup.
- Preserve existing organization accuracy, naming rules, collision policy, and user data.
- Cleanup must never delete a non-empty directory, the selected root, or destination library roots.

---

## 1. Refactor the Workflow Into One Indexed Pipeline

Replace repeated scanning and ad hoc file operations with this pipeline:

`Scan once -> Parse/index -> Resolve library context -> Build deterministic plan -> Execute -> Verify -> Cleanup`

### Implement

Create or refactor toward these responsibilities, adapting names to the existing architecture:

- `MediaScanner`: one recursive traversal of the selected tree.
- `MediaIndex`: in-memory snapshot of discovered files/directories and parsed metadata.
- `LibraryContextResolver`: determines whether the selection is already inside a Movies, TV/Series, movie, or series structure.
- `OrganizationPlanner`: computes every target directory/name before mutations begin.
- `FileOperationExecutor`: performs move/rename operations with SAF-aware fallbacks.
- `ProgressReporter`: exposes a single observable progress state.
- `EmptyDirectoryCleaner`: removes verified-empty source directories bottom-up after successful organization.

The UI/ViewModel should orchestrate these components, not contain file-processing logic.

### Scanner optimization

For SAF trees, avoid repeated `DocumentFile` property calls that can trigger separate provider queries. Prefer `ContentResolver.query()` against child-document URIs with a projection containing the fields actually needed, such as:

- document ID
- display name
- MIME type
- size
- last modified
- flags

Cache the resulting metadata in `MediaIndex`. Use `DocumentFile` only as a compatibility fallback where the provider does not support the direct query path.

Do not re-query unchanged metadata during matching, planning, execution, or cleanup unless an operation requires verification.

---

## 2. Remove Redundant Work and Expensive Algorithms

### Implement

- Normalize each filename/title once and store the normalized value in the index.
- Compile filename/season/episode regexes once, not per file.
- Parse season/episode/title metadata once per item.
- Replace repeated linear searches with maps keyed by normalized title, parent ID, season, episode, and destination path.
- Cache created/resolved destination directory URIs so the same folder is never looked up or created repeatedly.
- Skip no-op operations when an item is already at its final URI/name.
- Build the complete operation plan before mutating files so collisions and duplicate destination creation are resolved once.
- Preserve the application's existing collision policy, but resolve collisions during planning instead of discovering them during each move.

Avoid unbounded concurrency. CPU-only parsing/matching may use bounded parallelism. SAF mutation operations should remain serialized per provider/root unless the existing provider is proven safe with limited parallelism.

---

## 3. Implement Efficient SAF Move/Rename Behavior

Centralize all file mutations behind one executor.

### Operation order

1. If source and destination are within the same compatible document provider, use the provider's direct move operation where supported.
2. Rename the moved document only when the final filename differs.
3. Cache provider capability failures by authority so unsupported move operations are not retried for every file.
4. Only when direct move is unsupported or impossible, stream-copy directly to the final destination and then delete the source after verification.

### Copy-delete fallback safety

- Do not create a separate temporary file.
- Stream directly from source to final destination.
- Verify the destination exists and, when size metadata is available, matches the source size before deleting the source.
- If copy or verification fails, retain the source and report the item as failed.
- Never overwrite an existing destination unless the existing application explicitly allows that behavior.

---

## 4. Fix Redundant Library/Series Folder Creation

Implement `LibraryContextResolver` and make destination planning context-aware.

### Resolve these contexts

- `MOVIES_LIBRARY_ROOT`
- `TV_LIBRARY_ROOT`
- `MOVIE_FOLDER`
- `SERIES_FOLDER`
- `GENERIC_SOURCE`
- `AMBIGUOUS`

### Detection rules

Use both path structure and content signals:

1. Normalize available path segments and detect established library anchors such as the application's configured Movies and TV/Series folder names.
2. If the selected directory is already below a TV/Series library root, reuse that root instead of creating another `TV Shows` directory.
3. If the selected directory itself represents a series, reuse it as the series root. Detect this from strong signals such as:
   - season directories (`Season 01`, etc.)
   - episode filename patterns (`S01E01`, equivalent supported formats)
   - multiple episode files sharing the same parsed series title
4. If the selected directory itself represents a movie folder, keep it as the movie root rather than nesting another Movies/title structure inside it.
5. Only use the normal top-level Movies/TV destination creation for a generic source directory.
6. Prompt once before execution only when the resolver cannot confidently distinguish the intended library context. Do not prompt for cases with strong structural/content evidence.

### Required example

Given:

```text
Movies/
└── Series/
    └── Game of Thrones/
```

Do **not** produce:

```text
Game of Thrones/TV Shows/Game of Thrones/Season 01/
```

Instead reuse the detected series folder and create only missing season structure, for example:

```text
Movies/
└── Series/
    └── Game of Thrones/
        └── Season 01/
```

Add tests covering selection at the library root, series root, season root, generic downloads folder, and ambiguous mixed-content folder.

---

## 5. Replace the Generic Loader With Real Progress State

Create a single immutable progress model exposed as `StateFlow`/the project's equivalent.

### Suggested model

```text
phase
isIndeterminate
processedItems
totalItems
remainingItems
percent
currentItem
movedCount
renamedCount
skippedCount
failedCount
eta
startedAt
```

Use explicit phases such as:

- Scanning
- Matching Titles
- Planning
- Organizing Files
- Renaming
- Cleaning Up
- Complete

### UI behavior

Show:

- current phase
- determinate progress percentage when the total is known
- processed / total
- remaining
- current file or folder
- skipped and failed counts
- ETA when enough samples exist for a stable estimate

Do **not** perform a second scan just to obtain a scanning percentage. During the initial single-pass discovery, show an indeterminate scanning state plus files/directories discovered. Once the index is complete, all remaining work must use exact totals and determinate progress.

Calculate ETA from recent completed-operation durations using a smoothed moving average. Hide ETA until enough samples exist. Throttle UI progress emissions so rapid per-file updates do not cause excessive Compose recomposition.

Long-running work must live outside Composables and remain responsive across normal UI recomposition/navigation/lifecycle changes using the application's existing background/task architecture.

---

## 6. Add Safe Empty-Folder Cleanup

Cleanup runs **only after all planned move/rename operations have completed without relocation failures**.

### Implement

- Reuse the original scan/index to identify candidate source directories.
- Process candidates deepest-first.
- Re-check each candidate immediately before deletion because provider state has changed.
- Delete only when the provider confirms the directory has zero children.
- After deleting a child directory, allow its parent to become a cleanup candidate naturally through bottom-up traversal.
- Never delete:
  - the user-selected root
  - resolved Movies/TV library roots
  - final movie/series/season destination directories
  - any directory containing a file or undeleted child

If any relocation fails, skip destructive cleanup for that run and surface the reason in the completion summary.

---

## 7. Simplify the User Workflow

Refactor the flow from folder selection to completion so the user performs only necessary decisions.

### Implement

- After folder selection, immediately start the single scan and context detection.
- Avoid separate rescans between preview, matching, and organization.
- Reuse the same `MediaIndex` and `OrganizationPlan` through completion.
- Only interrupt the user for genuinely ambiguous destination/library context or an existing conflict that the current collision policy requires them to resolve.
- Keep the progress screen visible during processing and show a final summary with moved, renamed, skipped, failed, and cleaned-folder counts.
- On failure, identify the affected item and keep successfully completed work intact. Do not restart the entire library unnecessarily.

---

## 8. Performance Instrumentation and Verification

Add lightweight timing around each pipeline phase and counters for provider operations. Keep this behind debug logging/telemetry appropriate to the project.

Capture at minimum:

- scan duration
- matching/planning duration
- organization duration
- cleanup duration
- number of provider queries
- moves
- renames
- copy-delete fallbacks
- failed/skipped operations

Before finalizing the task, run the same representative dataset through the old baseline if available and the refactored flow. Confirm that provider queries/scans are reduced and wall-clock performance does not regress.

Do not leave verbose audit logging enabled in release builds.

---

## 9. Tests to Add or Update

Add focused tests for:

- single-scan/index reuse across matching and planning
- title normalization/parsing occurs once per indexed item
- same-provider direct move path
- unsupported-move fallback to verified copy-delete
- source retained when fallback copy fails
- existing destination/collision behavior
- selected TV library root does not create nested `TV Shows`
- selected series root does not duplicate the series title
- movie folder detection
- season folder creation only when missing
- no-op item already at destination
- progress counters/percentage/remaining counts
- failures reflected in progress state
- cleanup deletes nested empty folders bottom-up
- cleanup never deletes non-empty/root/destination folders
- cleanup is skipped when relocation failures exist

Also run the existing organization, storage, ViewModel, and UI test suites.

---

## 10. One-Pass Completion Checklist

Codex should consider the task complete only when all of the following are true:

- [ ] The organization workflow uses one scan/index instead of repeated full-tree scans.
- [ ] Blocking filesystem/provider work is off the main thread.
- [ ] Matching/planning avoids obvious repeated parsing and O(n²) lookups.
- [ ] Destination directories are resolved/created once and cached.
- [ ] Same-provider moves are preferred over copying.
- [ ] Copy-delete exists only as a verified fallback and creates no temporary intermediate file.
- [ ] Existing library/series selections do not create redundant `TV Shows/<series>` nesting.
- [ ] Empty source directories are safely removed bottom-up after a fully successful relocation phase.
- [ ] The progress UI shows meaningful phase, counts, current item, failures/skips, determinate percentage when knowable, and ETA when stable.
- [ ] Long-running processing does not block the UI.
- [ ] Existing behavior and collision safety remain intact.
- [ ] New regression tests pass.
- [ ] Existing relevant tests pass.
- [ ] Build succeeds.
- [ ] Any remaining provider-specific limitation is documented briefly in code/docs, not deferred as a general future audit.

## Implementation Rule for Codex

Do not return with an audit-only report or a list of recommendations. Inspect the relevant code, implement the above changes in the same task, test the finished workflow, and report only the files changed, validations run, measurable before/after findings available from the repository/test fixture, and any concrete limitation that could not be safely removed.
