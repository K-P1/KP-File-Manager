# KP File Manager Refactor Implementation Plan

## Objective

Refactor KP File Manager from a general-purpose file organizer into a dedicated **movie and TV library organizer**.

The refactored application must:

1. Recursively scan a user-selected directory.
2. Detect movies, TV episodes, subtitles, artwork, metadata, and related files.
3. Parse inconsistent media filenames into normalized metadata.
4. Group related files into movies, shows, seasons, and episodes.
5. Generate standardized destination paths and filenames.
6. Show a complete preview before modifying the filesystem.
7. Apply the approved operation safely.
8. Support undo for the most recent organization operation during the current application session.

Desktop and Android should use the same core organization rules wherever platform APIs allow it.

---

# 1. Scope

## Keep

The application should contain these primary features:

* Media library organization
* Movie detection and grouping
* TV series detection and grouping
* Season organization
* Recursive scanning
* Filename parsing and normalization
* Subtitle matching
* Preview before applying changes
* Session-based undo
* Batch rename
* Find and replace

## Remove

Delete or disable all functionality related to:

* Generic file-type sorting
* Documents sorting
* Images sorting
* Audio sorting
* Archive sorting
* Generic media categorization by extension
* Music filename cleanup
* MP3 metadata/tag-based renaming
* Music-related functionality in the Android application

Remove obsolete UI, models, services, settings, tests, and configuration related exclusively to these features.

---

# 2. Refactor Architecture

Separate the organizer into a pipeline.

```text
Folder Selection
      ↓
Directory Scanner
      ↓
Media File Classifier
      ↓
Filename Parser
      ↓
Media Matcher / Grouper
      ↓
Normalization Engine
      ↓
Organization Planner
      ↓
Conflict Detector
      ↓
Preview
      ↓
Apply Operation
      ↓
Undo Journal
```

Filesystem changes must not occur during scanning, parsing, grouping, or preview generation.

Only the final apply stage may modify files.

---

# 3. Core Domain Models

Introduce or refactor toward explicit domain models instead of passing raw filenames between components.

## ScannedFile

Represents a file discovered during scanning.

Suggested fields:

```text
sourcePath
fileName
baseName
extension
parentPath
depth
size
fileType
```

`fileType` should initially classify the file as one of:

```text
video
subtitle
artwork
metadata
unknown
```

Do not determine movie versus TV episode using extension alone.

---

## ParsedMediaName

Represents information extracted from a filename.

Suggested fields:

```text
originalName
normalizedTitle
year
season
episode
episodeEnd
part
language
subtitleQualifier
edition
extension
confidence
```

Examples of `subtitleQualifier`:

```text
forced
sdh
cc
default
```

Fields that cannot be determined must remain null rather than being guessed.

---

## MediaItem

Represent the detected logical media entity.

Possible types:

```text
Movie
TvEpisode
```

Movie example:

```text
title: Avatar
year: 2009
```

TV episode example:

```text
seriesTitle: Lost
season: 1
episode: 5
```

---

## OrganizationGroup

Represents files that should remain together.

Examples:

```text
MovieGroup
EpisodeGroup
SeasonGroup
SeriesGroup
```

A movie group may contain:

```text
video
subtitles
artwork
metadata
other matched companion files
```

An episode group may contain:

```text
video
subtitles
matched companion files
```

---

## PlannedFileOperation

Every proposed filesystem change must be represented explicitly.

Suggested fields:

```text
operationType
sourcePath
destinationPath
originalName
newName
mediaType
reason
status
conflict
```

Supported operation types should initially include:

```text
move
rename
move_and_rename
skip
```

---

# 4. Recursive Directory Scanner

Implement a dedicated recursive scanner.

## Requirements

The scanner must:

* Start from the folder selected by the user.
* Traverse nested directories.
* Respect a configurable maximum depth.
* Default to approximately 4 directory levels.
* Never follow recursion indefinitely.
* Ignore directories beyond the configured depth.
* Return discovered files without modifying them.

Recommended configuration:

```text
scanDepth = 4
```

Allow this value to be changed from application settings or the organization screen.

The scanner should work regardless of how meaningful the existing folder names are.

Example:

```text
Downloads/
    TV Shows/
        Random Folder/
            Lost Episodes/
                S01/
```

The parser should primarily determine media identity from files and surrounding context, not assume that existing folder names are correct.

---

# 5. File Classification

Classify discovered files before media parsing.

At minimum support:

## Video

Examples:

```text
.mkv
.mp4
.avi
.mov
.m4v
.webm
```

Use the application's existing supported-video-extension list where appropriate.

## Subtitle

Support at least:

```text
.srt
.ass
.ssa
.sub
.vtt
```

## Artwork

Recognize common image formats when they appear to be associated with media.

Examples:

```text
.jpg
.jpeg
.png
.webp
```

## Metadata

Recognize:

```text
.nfo
```

Files outside recognized categories should be marked `unknown`.

Unknown files must not automatically be deleted.

---

# 6. Media Filename Parser

Create a dedicated parsing engine responsible for converting release-style filenames into structured metadata.

Parsing must be deterministic and independently testable.

The parser should operate on the filename without its extension.

---

# 7. Preprocessing

Before pattern matching:

1. Strip the extension.
2. Normalize separators.
3. Convert `_` and `.` to spaces where appropriate.
4. Normalize repeated whitespace.
5. Preserve meaningful numeric values.
6. Avoid permanently modifying the original string.

Example:

```text
The.Office.US.S03E05.1080p.WEBRip.x265-GROUP
```

should become a parser-friendly representation similar to:

```text
The Office US S03E05 1080p WEBRip x265 GROUP
```

---

# 8. TV Episode Recognition

Support at least the following episode patterns.

## Standard season/episode

```text
S01E01
s01e01
S1E1
```

## x format

```text
1x01
01x01
```

## Verbose format

```text
Season 1 Episode 1
Season 01 Episode 03
```

## Season plus separated episode format

Support forms similar to:

```text
S01 E01
S01_E01
```

## Episode-only patterns

Patterns such as:

```text
Episode 01
E01
```

must be treated with lower confidence because they do not necessarily identify a season.

Use folder context when helpful, for example:

```text
Lost/
    Season 02/
        Episode 03.mkv
```

can reasonably produce:

```text
series = Lost
season = 2
episode = 3
```

Do not allow weak episode-only patterns to override stronger movie evidence.

---

# 9. Multi-Episode Support

Structure the parser so episode ranges can be represented.

Examples:

```text
S01E01E02
S01E01-E02
S01E01-02
```

Represent these as:

```text
season = 1
episode = 1
episodeEnd = 2
```

Even if the initial UI treats these as one media file, the parser must not destroy this information.

---

# 10. Release Metadata Removal

Strip known release metadata from the title portion after episode/year extraction.

Examples include:

```text
480p
720p
1080p
2160p
4K
WEB-DL
WEBRip
BluRay
BDRip
HDRip
DVDRip
REMUX
x264
x265
H264
H265
HEVC
AAC
AC3
EAC3
DTS
Atmos
HDR
HDR10
REPACK
PROPER
EXTENDED
```

Matching should be case-insensitive.

Keep this list centralized so additional release tags can be added later without modifying parser logic.

Do not use a massive single regular expression for the entire parser.

Use sequential parsing stages.

---

# 11. Release Group Handling

Recognize common trailing release-group patterns.

Example:

```text
The.Office.US.S03E05.1080p.WEBRip.x265-GROUP.mkv
```

should produce approximately:

```text
title = The Office US
season = 3
episode = 5
extension = mkv
```

`GROUP` should not become part of the normalized show title.

Release-group removal should occur only when the parser has reasonable evidence that the suffix is release metadata.

---

# 12. Movie Recognition

Files without credible episode information should be evaluated as movies.

Recognize titles with optional years.

Examples:

```text
Avatar.2009.1080p.mkv
Avatar (2009).mkv
Interstellar.2014.BluRay.x265.mkv
```

should become:

```text
Avatar
year = 2009
```

and:

```text
Interstellar
year = 2014
```

Do not require a year for movie detection.

A filename such as:

```text
Arrival.mkv
```

may still be treated as a movie if no TV episode pattern exists.

---

# 13. Year Recognition

Recognize realistic four-digit movie/show years.

A year should normally be interpreted from a value resembling:

```text
19xx
20xx
```

Avoid treating unrelated numbers as years.

After extraction, remove the year from the normalized title while retaining it as metadata.

---

# 14. Title Normalization

Normalize titles after structural metadata has been extracted.

Rules:

* Replace release separators with spaces.
* Collapse repeated spaces.
* Trim surrounding punctuation.
* Remove release metadata.
* Remove season/episode tokens.
* Remove extracted year from title text.
* Preserve meaningful words and numbers.
* Produce readable capitalization.

Examples:

```text
the-office_us_s03e05.mp4
```

becomes:

```text
The Office US
```

and:

```text
The.Office.US.S03E05.1080p.x264.mkv
```

becomes:

```text
The Office US
```

Avoid aggressive normalization that could incorrectly alter legitimate titles.

---

# 15. TV Filename Generation

Normalized TV episode filenames should use:

```text
{Series Title} - S{season:02}E{episode:02}.{extension}
```

Example:

```text
Lost - S01E01.mkv
```

Season and episode values should use at least two digits.

Examples:

```text
1 → 01
5 → 05
12 → 12
```

For multi-episode files, use a deterministic format such as:

```text
Lost - S01E01-E02.mkv
```

Keep filename formatting centralized in one formatter.

---

# 16. Movie Filename Generation

Movie folders and primary media filenames should use:

```text
{Movie Title} ({Year})
```

when a year exists.

Example:

```text
Avatar (2009)/Avatar (2009).mp4
```

When no year is available:

```text
{Movie Title}/{Movie Title}.ext
```

Do not invent a year.

---

# 17. Media Grouping Engine

After parsing, group files by normalized identity.

## TV identity

Primary grouping key:

```text
normalizedSeriesTitle
season
episode
episodeEnd
```

Example:

```text
Lost.S01E01.mkv
Lost.S01E01.en.srt
Lost - 1x01.forced.srt
```

should be candidates for the same episode group.

## Movie identity

Primary grouping key:

```text
normalizedMovieTitle
year
```

when the year exists.

Files without years may use normalized title matching with lower confidence.

---

# 18. Subtitle Matching

Subtitles must remain beside their matching video.

Recognize filenames such as:

```text
Movie.srt
Movie.en.srt
Movie.forced.srt
Movie.en.forced.srt
Movie.ass
Movie.sub
```

For TV:

```text
Lost.S01E01.srt
Lost.S01E01.en.srt
Lost.S01E01.forced.srt
```

must match the corresponding episode.

Matching priority should be:

```text
exact parsed media identity
↓
same normalized title + season + episode
↓
same basename
↓
same directory context
```

Do not attach a subtitle to a media file when multiple equally plausible matches exist.

Mark ambiguous subtitles for review in the preview.

---

# 19. Subtitle Naming

Preserve meaningful subtitle qualifiers.

Example:

```text
Lost.S01E01.en.srt
```

becomes:

```text
Lost - S01E01.en.srt
```

Example:

```text
Lost.S01E01.forced.srt
```

becomes:

```text
Lost - S01E01.forced.srt
```

Example:

```text
Avatar.2009.en.forced.srt
```

becomes:

```text
Avatar (2009).en.forced.srt
```

Do not rename every subtitle to exactly the video basename when doing so would discard language or qualifier information.

---

# 20. Movie Companion Files

Group movie artwork and metadata with the movie when confidently matched.

Example input:

```text
Avatar (2009).mp4
Avatar.2009.srt
Avatar (2009) Poster.jpg
Avatar-2009.nfo
```

Desired output:

```text
Avatar (2009)/
├── Avatar (2009).mp4
├── Avatar (2009).srt
├── Poster.jpg
└── Movie.nfo
```

Use deterministic naming for recognized special companion files.

Examples:

```text
poster → Poster.jpg
movie metadata → Movie.nfo
```

Do not rename arbitrary images unless they can confidently be classified as media artwork.

---

# 21. Target TV Folder Structure

Generate:

```text
TV Shows/
└── {Series Title}/
    └── Season {season:02}/
        ├── {Series Title} - S01E01.mkv
        ├── {Series Title} - S01E01.srt
        └── ...
```

Example:

```text
TV Shows/
└── Lost/
    ├── Season 01/
    │   ├── Lost - S01E01.mkv
    │   ├── Lost - S01E01.srt
    │   ├── Lost - S01E02.mkv
    │   └── Lost - S01E02.srt
    ├── Season 02/
    └── Season 03/
```

---

# 22. Target Movie Folder Structure

Generate:

```text
Movies/
└── {Movie Title} ({Year})/
    ├── {Movie Title} ({Year}).mkv
    ├── {Movie Title} ({Year}).srt
    ├── Poster.jpg
    └── Movie.nfo
```

Example:

```text
Movies/
├── Avatar (2009)/
│   ├── Avatar (2009).mp4
│   ├── Avatar (2009).srt
│   └── Poster.jpg
└── Interstellar (2014)/
    ├── Interstellar (2014).mkv
    └── Interstellar (2014).srt
```

---

# 23. Organization Planner

Do not directly move files from parser results.

Build an organization plan first.

For every file, calculate:

```text
source path
detected media identity
normalized filename
destination directory
destination path
operation required
warnings
conflicts
```

The planner must be a pure planning stage whenever practical.

Running the planner twice against the same filesystem state should produce the same result.

---

# 24. Conflict Detection

Before applying operations, detect:

* Destination file already exists
* Two source files map to the same destination
* Duplicate video files for the same episode
* Duplicate movie files
* Ambiguous subtitle matches
* Unknown media identity
* Invalid filesystem filename
* Destination path equals source path
* Case-only filename collisions on case-insensitive filesystems

Do not silently overwrite existing files.

Conflicting operations must appear in the preview and should default to skipped until explicitly resolved.

---

# 25. Preview Model

The organization screen must show the planned result before modifying the filesystem.

Display at minimum:

```text
Detected title
Media type
Season
Episode
Original path
New path
Original filename
New filename
Operation
Warning/conflict status
```

Group preview entries logically by:

```text
Movies
TV Shows
Skipped
Conflicts
```

Nothing should be modified by opening, refreshing, or regenerating the preview.

---

# 26. Apply Operation

Only execute operations after explicit user confirmation.

Execution should use the previously generated organization plan rather than reparsing files while the operation is running.

Before each operation:

1. Verify the source still exists.
2. Verify the destination remains valid.
3. Verify no unexpected overwrite will occur.
4. Record undo information.
5. Execute the operation.
6. Record success or failure.

A failure on one file should not corrupt the operation history for successfully processed files.

Return a final result containing:

```text
successful operations
failed operations
skipped operations
warnings
```

---

# 27. Undo Journal

Maintain an in-memory undo journal for the latest completed organization operation.

For every successful filesystem change, record enough information to reverse it.

Example:

```text
beforePath
afterPath
operationType
```

Undo should process operations in reverse order.

Example:

```text
A → B
B → C
```

must undo as:

```text
C → B
B → A
```

Undo must restore:

* Original filenames
* Original file locations

Delete empty directories created by the organizer after their contents have been restored, where safe.

Do not delete directories that contain unrelated files.

Undo history must be cleared when the application closes.

Persistent multi-session undo is explicitly out of scope.

---

# 28. Batch Rename

Retain the existing batch rename feature but separate it from media organization.

Inputs:

```text
target directory
prefix
suffix
starting number
digit padding
extension replacement
```

Example configuration:

```text
prefix = Episode_
start = 1
padding = 3
```

Result:

```text
Episode_001.mp4
Episode_002.mp4
Episode_003.mp4
```

Generate a preview before applying batch rename operations.

Detect filename collisions before execution.

Reuse the same operation planning/execution infrastructure where practical.

---

# 29. Find and Replace

Retain filename find-and-replace as a separate tool.

Inputs:

```text
target directory
find text
replacement text
extension filter
```

An empty replacement means removal.

Example:

```text
Find: 1080p
Replace: BluRay
```

Result:

```text
Movie 1080p.mkv
```

becomes:

```text
Movie BluRay.mkv
```

Support:

```text
all file types
specific extension
```

Generate a preview and conflict check before modifying files.

---

# 30. Shared Filesystem Operation Layer

Batch rename, find-and-replace, and media organization should reuse a shared filesystem operation layer.

Suggested responsibilities:

```text
plan operation
validate source
validate destination
detect conflicts
execute move
execute rename
record undo entry
report failure
```

Do not duplicate filesystem mutation logic across features.

---

# 31. Parser Confidence

Add a simple confidence mechanism to reduce dangerous automatic matches.

Suggested levels:

```text
High
Medium
Low
Unknown
```

Examples:

High:

```text
Lost.S01E05.1080p.mkv
```

Medium:

```text
Lost Episode 05.mkv
```

when the surrounding folder provides season information.

Low:

```text
Episode 05.mkv
```

with little useful folder context.

Unknown:

```text
video123.mkv
```

Low-confidence and unknown items should be clearly visible in preview and should not be aggressively renamed or grouped.

---

# 32. Parsing Priority

Use a predictable parsing order.

Recommended sequence:

```text
1. Extract extension
2. Normalize separators
3. Detect TV season/episode pattern
4. Detect multi-episode pattern
5. Detect year
6. Detect subtitle language/qualifiers
7. Remove known release metadata
8. Remove likely release group
9. Extract remaining title
10. Normalize title
11. Calculate confidence
```

Do not run unrelated regular expressions in arbitrary order.

---

# 33. Folder Context

Filename parsing remains the primary source of identity, but directory names may provide secondary evidence.

Useful folder patterns include:

```text
Season 1
Season 01
S01
Series title
Movie title + year
```

Example:

```text
Lost/
    Season 02/
        Episode 03.mkv
```

may resolve to:

```text
Lost
Season 02
Episode 03
```

Folder evidence should supplement filename parsing rather than automatically overriding strong filename metadata.

---

# 34. Idempotency

Organizing an already-organized library should produce little or no work.

Example input:

```text
TV Shows/
└── Lost/
    └── Season 01/
        └── Lost - S01E01.mkv
```

should produce:

```text
No change
```

The organizer must not repeatedly rename correctly normalized files.

---

# 35. Filesystem Safety

The implementation must follow these rules:

* Never silently overwrite a file.
* Never delete an unknown file.
* Never mutate files during preview.
* Never assume a move succeeded without checking the filesystem result.
* Never remove non-empty directories containing unrelated files.
* Preserve file extensions unless the user explicitly uses the batch extension replacement feature.
* Keep failed operations visible to the user.
* Prefer skipping uncertain items over performing destructive guesses.

---

# 36. UI Refactor

Rework navigation around the narrower media-management scope.

Primary application areas should be approximately:

```text
Organize Media
Batch Rename
Find & Replace
Settings
```

Remove navigation entries belonging to:

```text
Generic file sorting
Music cleanup
MP3 tag tools
Generic extension categorization
```

The main organizer flow should be:

```text
Select Folder
↓
Configure Scan
↓
Scan
↓
Review Detected Library
↓
Preview Changes
↓
Resolve Conflicts
↓
Apply
↓
Results / Undo
```

Desktop and Android should expose equivalent behavior where possible, even if their filesystem picker implementations differ.

---

# 37. Configuration

Add or retain configuration for:

```text
maximum scan depth
movie destination root
TV destination root
preview behavior
```

Default scan depth:

```text
4
```

Keep parser rules and release-tag lists in application code rather than exposing every parsing rule to the user.

---

# 38. Testing Strategy

The parser and planner require comprehensive automated tests before filesystem execution is connected.

## TV parser tests

Include:

```text
Lost.S01E01.1080p.x264.mkv
Lost - 1x02.mp4
Lost Season 1 Episode 03.mkv
LOST_s01_e04.mp4
Lost (2004) S01E05 WEB-DL.mkv
The.Office.US.S03E05.1080p.WEBRip.x265-GROUP.mkv
```

Expected normalized results should include the correct:

```text
title
season
episode
year when present
extension
```

---

## Movie parser tests

Include:

```text
Avatar (2009).mp4
Avatar.2009.1080p.x265.mkv
Interstellar.2014.BluRay.mkv
Arrival.mkv
```

Verify title and year extraction.

---

## Subtitle tests

Include:

```text
Lost.S01E01.srt
Lost.S01E01.en.srt
Lost.S01E01.forced.srt
Lost.S01E01.en.forced.srt
Avatar.2009.srt
Avatar.2009.en.srt
```

Verify matching and qualifier preservation.

---

## Separator tests

Equivalent filenames should resolve to the same identity:

```text
the.office.us.s03e05.mkv
the-office-us-s03e05.mkv
the_office_us_s03e05.mkv
the office us s03e05.mkv
```

---

## Folder-context tests

Test:

```text
Lost/
    Season 01/
        Episode 01.mkv
```

and:

```text
Downloads/
    Random/
        Lost/
            S01/
                E01.mkv
```

---

## Conflict tests

Test:

```text
two files targeting same destination
existing destination file
duplicate subtitles
duplicate episodes
case-only filename differences
```

---

## Undo tests

Verify:

```text
move
rename
move + rename
multiple operations
partially failed operations
created directory cleanup
```

Undo must restore the original filesystem layout.

---

# 39. Representative Acceptance Test

Given:

```text
Input/
├── Lost.S01E01.1080p.x264.mkv
├── Lost - 1x01.en.srt
├── Lost Season 1 Episode 02.mp4
├── Lost_s01_e02.forced.srt
├── Avatar.2009.1080p.mkv
├── Avatar.2009.en.srt
└── Avatar.2009.Poster.jpg
```

the preview should propose approximately:

```text
Movies/
└── Avatar (2009)/
    ├── Avatar (2009).mkv
    ├── Avatar (2009).en.srt
    └── Poster.jpg

TV Shows/
└── Lost/
    └── Season 01/
        ├── Lost - S01E01.mkv
        ├── Lost - S01E01.en.srt
        ├── Lost - S01E02.mp4
        └── Lost - S01E02.forced.srt
```

No filesystem changes may occur until the preview is confirmed.

After applying, invoking Undo during the same application session must restore every successfully moved or renamed file to its original path.

---

# 40. Implementation Order

Implement the refactor in this order.

## Phase 1: Remove obsolete scope

* Remove generic file-type organization.
* Remove music cleanup.
* Remove MP3 metadata functionality.
* Remove associated screens and settings.
* Remove dead code and tests.

Do not begin by adding more behavior to the old generic sorter.

---

## Phase 2: Introduce domain models

Implement:

```text
ScannedFile
ParsedMediaName
MediaItem
OrganizationGroup
PlannedFileOperation
UndoEntry
```

Update existing code to use these models.

---

## Phase 3: Build scanner

Implement:

* recursive traversal
* maximum depth
* file classification
* platform-specific filesystem adapters where required

Add scanner tests.

---

## Phase 4: Build parser

Implement in small independent stages:

```text
separator normalization
TV pattern recognition
movie/year recognition
release tag removal
release group removal
title normalization
subtitle qualifier parsing
confidence calculation
```

Write unit tests for every parsing rule.

Do not connect parser development directly to file moves.

---

## Phase 5: Build matching/grouping

Implement:

```text
movie grouping
series grouping
season grouping
episode grouping
subtitle matching
artwork matching
metadata matching
```

Add ambiguity detection.

---

## Phase 6: Build destination formatter

Implement centralized functions for:

```text
movie folder names
movie filenames
series folder names
season folder names
episode filenames
subtitle filenames
artwork filenames
metadata filenames
```

Add idempotency tests.

---

## Phase 7: Build organization planner

Convert grouped media into `PlannedFileOperation` objects.

Implement:

```text
destination calculation
conflict detection
skip handling
warning generation
```

The planner must not modify the filesystem.

---

## Phase 8: Refactor preview UI

Display the organization plan.

Show:

```text
Movies
TV Shows
Skipped
Conflicts
```

Allow the user to inspect the original and destination path for each operation.

---

## Phase 9: Build safe operation executor

Implement the shared filesystem mutation layer.

Support:

```text
move
rename
move + rename
failure reporting
```

Block overwrites.

---

## Phase 10: Implement undo

Record successful operations.

Reverse them in the correct order.

Clear undo history when the application session ends.

---

## Phase 11: Refactor Batch Rename and Find & Replace

Keep their existing user-facing purpose but migrate them to:

```text
plan
preview
conflict detection
execute
```

Reuse the shared operation layer.

---

## Phase 12: Integration testing

Test complete libraries containing:

* multiple movies
* multiple shows
* multiple seasons
* mixed video formats
* multiple subtitles
* nested directories
* malformed filenames
* already-organized media
* conflicting files
* unrelated files

Verify the organizer remains safe when detection fails.

---

# 41. Definition of Done

The refactor is complete when all of the following are true:

* General-purpose file sorting has been removed.
* Music and MP3 cleanup functionality has been removed.
* Movie and TV detection uses structured filename parsing.
* Nested folders are scanned to a configurable depth.
* Movie files are grouped by normalized movie identity.
* TV episodes are grouped by series and season.
* Common release tags do not pollute normalized titles.
* Common episode naming formats are recognized.
* Subtitle files are matched to their corresponding media.
* Subtitle language and qualifier suffixes are preserved.
* Movie artwork and metadata can remain with their movie.
* Destination filenames and directories follow one centralized naming scheme.
* The app generates a complete plan before touching the filesystem.
* Existing destination files are never silently overwritten.
* Ambiguous items are surfaced instead of aggressively guessed.
* Already-organized media produces no unnecessary changes.
* Users can preview all relevant moves and renames.
* Users must explicitly confirm before operations are applied.
* The latest organization operation can be undone during the current session.
* Batch Rename uses preview and conflict detection.
* Find & Replace uses preview and conflict detection.
* Core parsing, grouping, planning, conflict, and undo behavior is covered by automated tests.

---

# 42. Implementation Principle

Treat KP File Manager as a **media library normalization engine**, not a generic file sorter.

Keep these concerns separate:

```text
Detection
Parsing
Matching
Naming
Planning
Filesystem Mutation
UI
```

The parser determines what a file appears to represent.

The matcher determines which files belong together.

The formatter determines what the normalized result should look like.

The planner determines what filesystem operations would be required.

The preview allows the user to inspect those operations.

The executor performs only the approved operations.

This separation is required so filename-recognition rules can evolve without increasing the risk of destructive filesystem behavior.
