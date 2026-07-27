# KP File Manager UI/UX Implementation Specification

## 1. Product Goal

Design KP File Manager as a focused media-organizing utility for desktop and Android.

The interface should make the primary workflow immediately obvious:

```text
Select Folder
→
Scan
→
Review Changes
→
Apply
→
Done / Undo
```

A first-time user should be able to complete this workflow without documentation, onboarding, tooltips, or tutorials.

The application should feel:

* Clean
* Professional
* Predictable
* Fast
* Safe
* Uncluttered

Prioritize usability and clarity over visual effects.

Avoid decorative UI that does not improve task completion.

---

# 2. Core Navigation

The application has four primary destinations:

```text
Organize Media
Batch Rename
Find & Replace
Settings
```

`Organize Media` is the default screen when the application opens.

Do not include a dashboard unless it provides an actual action. The organizer itself should function as the home screen.

---

# 3. Desktop Navigation

Use a persistent left navigation rail/sidebar.

Recommended width:

```text
Expanded: 220–240 px
Collapsed: 64–72 px
```

Default to expanded on normal desktop window sizes.

Structure:

```text
KP File Manager

Organize Media
Batch Rename
Find & Replace

────────────

Settings
```

Each item contains:

```text
Icon + label
```

Recommended icons:

```text
Organize Media     folder / movie
Batch Rename       edit / rename
Find & Replace     search-replace
Settings           settings
```

The currently selected item uses:

* Slightly tinted background
* Stronger text color
* Primary-color icon
* Optional 3 px selection indicator

Do not use bright filled navigation buttons.

---

# 4. Android Navigation

Use different navigation depending on available width.

## Phones

Use a bottom navigation bar with:

```text
Organize
Rename
Replace
Settings
```

Keep labels visible.

Do not use icon-only bottom navigation.

## Large phones and tablets

Use a navigation rail when horizontal space allows it.

Suggested breakpoint:

```text
>= 600 dp
```

At tablet widths, use the same basic layout model as desktop.

---

# 5. Global Layout

All primary screens use:

```text
Navigation
+
Top App Bar
+
Scrollable Content Area
```

Desktop content should have a sensible maximum width rather than stretching forms across the entire monitor.

Recommended:

```text
General content max width: 1200 px
Form content max width: 760 px
```

Data-heavy review screens may use the full available width.

---

# 6. Spacing System

Use a consistent 4 or 8 unit spacing system.

Recommended values:

```text
4   micro spacing
8   tight spacing
12  related component spacing
16  standard spacing
24  section spacing
32  large section spacing
48  major separation
```

Default page padding:

```text
Desktop: 24–32 px
Tablet: 24 dp
Phone: 16 dp
```

Avoid excessive empty space.

---

# 7. Corner Radius

Keep corner radii modest.

Recommended:

```text
Buttons: 8
Inputs: 8
Cards: 10–12
Dialogs: 12–16
Bottom sheets: 20 top corners
```

Avoid extremely rounded pill-shaped surfaces except for status chips.

---

# 8. Typography

Use the platform's native or primary UI font.

Desktop should use the application's existing system font stack unless a custom font is already part of the project.

Android should use the standard Android system typography unless the application already has an established font.

Recommended hierarchy:

```text
Page title:
24–28 px / semibold

Section title:
18–20 px / semibold

Card title:
16 px / semibold

Body:
14–16 px / regular

Secondary:
13–14 px / regular

Caption:
12 px / regular
```

Do not use ultra-light font weights.

Maintain readable line heights.

---

# 9. Color System

Use semantic colors instead of hardcoding screen-specific colors.

Required tokens:

```text
background
surface
surfaceVariant
surfaceElevated
border
textPrimary
textSecondary
textDisabled
primary
primaryHover
primaryPressed
primaryContainer
success
successContainer
warning
warningContainer
error
errorContainer
info
infoContainer
focus
```

---

# 10. Light Theme

Recommended visual direction:

```text
Background:
soft neutral white / very light gray

Cards:
white or slightly elevated neutral

Borders:
subtle medium-light gray

Primary text:
near-black neutral

Secondary text:
medium gray

Primary accent:
restrained blue or similar professional accent
```

Do not use pure black text against pure white if a slightly softened neutral improves comfort.

---

# 11. Dark Theme

Recommended:

```text
Background:
very dark neutral gray

Surface:
slightly lighter dark gray

Elevated surface:
one level lighter

Primary text:
near-white

Secondary text:
muted light gray

Borders:
visible but subtle gray

Primary accent:
lighter version of the light-theme accent
```

Do not simply invert the light theme.

Do not use pure black for every background surface.

Cards, dialogs, navigation, and content areas must remain visually distinguishable.

---

# 12. Accessibility

All text and interactive controls must meet appropriate contrast requirements.

Minimum interactive touch target on Android:

```text
48 x 48 dp
```

Desktop controls should generally provide at least:

```text
36–40 px height
```

Support:

* Keyboard navigation
* Visible focus states
* Screen readers
* Semantic control labels
* Large system font settings where possible
* High contrast between status states
* Color-independent status communication

Never communicate a conflict using red alone.

Use:

```text
warning icon + "Conflict"
```

instead of only changing the row color.

---

# 13. Primary Button Hierarchy

Only one dominant primary action should normally appear in a view.

Primary actions:

```text
Select Folder
Scan Folder
Review Changes
Apply Changes
Done
```

Secondary actions:

```text
Change Folder
Scan Options
Cancel
Back
Filter
Sort
View Details
Skip
```

Destructive actions:

```text
Cancel Operation
Discard
```

Use destructive styling only when there is actual risk or loss.

---

# 14. Button Placement

Desktop:

Primary actions should normally appear toward the lower-right or upper-right of the relevant content region.

Mobile:

Place critical next-step actions in a sticky bottom action area when appropriate.

Example:

```text
[ Review Changes ]
```

The button must remain above the gesture navigation area.

---

# 15. Organize Media: Initial State

This is the application's main screen.

## Desktop Layout

```text
┌──────────────────────────────────────────────────────────────┐
│ Organize Media                                               │
│ Scan a folder and organize movies and TV shows.              │
│                                                              │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ Selected Folder                                          │ │
│ │                                                          │ │
│ │ No folder selected                                       │ │
│ │                                                          │ │
│ │ [ Select Folder ]                                        │ │
│ └──────────────────────────────────────────────────────────┘ │
│                                                              │
│ Scan Options                                                 │
│ Scan depth                 4 levels                           │
│ Movie destination          Movies                             │
│ TV destination             TV Shows                           │
│                                                              │
│                                      [ Scan Folder ] disabled │
└──────────────────────────────────────────────────────────────┘
```

The first visually dominant control should be:

```text
Select Folder
```

Do not make users configure options before selecting a folder.

---

# 16. Folder Selected State

After folder selection:

```text
Selected Folder

Downloads / Media

/path/to/Downloads/Media

[ Change Folder ]
```

On desktop, show the full path beneath the friendly folder name.

On mobile, truncate long paths intelligently:

```text
/storage/emulated/0/…/Media
```

Provide a way to view the complete path through the item detail sheet or long press if needed.

Primary button becomes enabled:

```text
Scan Folder
```

---

# 17. Scan Options

Keep commonly used options visible but visually secondary.

Default options:

```text
Scan depth        4
Movies folder     Movies
TV Shows folder   TV Shows
```

Advanced or rarely changed options should not clutter the primary flow.

Desktop:

Use an expandable:

```text
Scan Options
```

section.

Mobile:

Use a:

```text
Scan Options
```

row that opens a bottom sheet.

---

# 18. Scan Options Bottom Sheet

Android phone layout:

```text
Scan Options

Maximum scan depth
[ - ] 4 [ + ]

Movie destination
Movies
[ Change ]

TV destination
TV Shows
[ Change ]

[ Reset Defaults ]

────────────

[ Done ]
```

The bottom sheet must:

* Respect bottom safe area
* Scroll if vertical space is insufficient
* Remain usable in landscape orientation
* Never place the primary button behind system navigation

---

# 19. Scanning Screen

After `Scan Folder`, immediately transition to scan progress.

Display:

```text
Scanning Media

Scanning your folders for movies, TV episodes,
subtitles, artwork, and metadata.

████████████░░░░░░

428 files scanned

Currently scanning
…/TV Shows/Lost/Season 02

[ Cancel Scan ]
```

When total file count is unknown, use an indeterminate progress bar initially.

Once progress can be reasonably calculated, switch to determinate progress.

Do not display fake percentages.

---

# 20. Scan Progress Information

Show useful information only.

Recommended:

```text
Files scanned
Movies detected
TV episodes detected
Subtitles detected
```

Example:

```text
1,248 files scanned

24 Movies
186 Episodes
203 Subtitles
```

Do not update the interface so rapidly that numbers visually flicker.

Throttle UI progress updates if required.

---

# 21. Background Scan Behavior

Scanning may take a long time.

The interface must:

* Remain responsive
* Allow cancellation
* Prevent duplicate scan actions
* Prevent accidentally starting another organizer operation
* Continue displaying current progress
* Show the currently processed folder when available

On Android, account for lifecycle changes such as:

```text
screen rotation
temporary backgrounding
activity recreation
```

Do not lose scan state merely because the UI is recreated.

---

# 22. Cancel Scan Dialog

If the user presses `Cancel Scan`:

```text
Cancel scan?

Files have not been changed.
The current scan results will be discarded.

[ Keep Scanning ]   [ Cancel Scan ]
```

Use the safer action as the default focus.

---

# 23. Scan Error State

Recoverable errors should not necessarily stop the entire scan.

Example:

```text
Scan completed with warnings

3 folders could not be accessed.
The rest of the library was scanned normally.

[ View Warnings ]
[ Review Results ]
```

Fatal scan error:

```text
Unable to scan folder

KP File Manager could not access this location.

Check folder permissions or choose another folder.

[ Choose Another Folder ]
[ Try Again ]
```

Do not show raw stack traces.

Technical details may appear under:

```text
Show Details
```

for debugging.

---

# 24. No Media Detected State

If the scan completes successfully but no supported media is found:

```text
No movies or TV episodes found

KP File Manager scanned this folder but could not
identify supported media files.

Try selecting a different folder or increasing
the scan depth.

[ Change Folder ]
[ Scan Options ]
```

Do not present this as an error.

---

# 25. Review Screen

After scanning, transition directly to `Review Changes`.

This is the most important screen in the application.

The user must clearly understand:

* What was detected
* What will move
* What will be renamed
* What will be skipped
* Which items have conflicts

---

# 26. Review Screen Header

Desktop:

```text
Review Changes

246 files detected
231 changes planned
8 unchanged
7 need attention

[ Rescan ]                      [ Apply Changes ]
```

Mobile:

```text
Review Changes

231 changes
7 need attention
```

Place `Apply Changes` in a persistent bottom action area.

If unresolved blocking conflicts exist, disable the button and label the reason:

```text
Resolve 7 Conflicts
```

---

# 27. Review Summary Cards

Show a compact summary:

```text
Movies       24
TV Shows     12
Episodes     186
Conflicts     7
```

Desktop:

Display horizontally.

Mobile:

Use either a 2 x 2 grid or horizontally scrollable compact cards.

Keep these cards small. They are summaries, not dashboard widgets.

---

# 28. Review Tabs

Use:

```text
All
Movies
TV Shows
Conflicts
Skipped
```

Desktop tabs can remain across the top of the result area.

Mobile may use horizontally scrollable tabs.

Show counts where useful:

```text
Conflicts 7
Skipped 8
```

---

# 29. Desktop Review Layout

Use a split view when sufficient width exists.

```text
┌────────────────────────────────────────────────────────────────────┐
│ Review Changes                                                     │
├───────────────────────────────┬────────────────────────────────────┤
│ Detected Library              │ Proposed Structure                 │
│                               │                                    │
│ ▼ Movies                      │ Movies                             │
│   Avatar (2009)               │ └ Avatar (2009)                    │
│   Interstellar (2014)         │   ├ Avatar (2009).mkv              │
│                               │   ├ Avatar (2009).en.srt           │
│ ▼ TV Shows                    │   └ Poster.jpg                      │
│   ▼ Lost                      │                                    │
│     Season 01                 │ TV Shows                           │
│     Season 02                 │ └ Lost                             │
│                               │   └ Season 01                      │
│                               │     ├ Lost - S01E01.mkv            │
│                               │     └ Lost - S01E01.en.srt         │
└───────────────────────────────┴────────────────────────────────────┘
```

Recommended split:

```text
Left: 38–42%
Right: 58–62%
```

Make the divider draggable where practical.

---

# 30. Detected Library Tree

The left pane should show logical media entities, not raw filesystem noise.

Structure:

```text
Movies
    Avatar (2009)
    Interstellar (2014)

TV Shows
    Lost
        Season 01
        Season 02
    Breaking Bad
        Season 01
```

Show warnings beside affected items.

Example:

```text
⚠ Lost
```

Use expansion arrows for nested items.

Remember expanded/collapsed state during the current review session.

---

# 31. Proposed Structure Pane

Display the destination tree.

Example:

```text
Movies
└── Avatar (2009)
    ├── Avatar (2009).mkv
    ├── Avatar (2009).en.srt
    └── Poster.jpg
```

Icons:

```text
folder
video
subtitle
image
metadata
warning
```

Do not assign unique bright colors to every file type.

Use consistent neutral icons with semantic accents for warnings/errors.

---

# 32. File Operation Details

Selecting an item should reveal details.

Desktop can use:

* Detail panel below the tree
* Right-side inspector
* Expandable row

Recommended detail content:

```text
Avatar.2009.1080p.x265.mkv

Detected as
Movie

Title
Avatar

Year
2009

Original
/Downloads/Avatar.2009.1080p.x265.mkv

New location
/Movies/Avatar (2009)/Avatar (2009).mkv

Operation
Move and rename

Confidence
High
```

---

# 33. Mobile Review Layout

Do not attempt to reproduce the desktop split view on a narrow phone.

Use a single-column grouped list:

```text
Review Changes

[ All ] [ Movies ] [ TV ] [ Conflicts ]

Movies

Avatar (2009)
3 files
Move to Movies/Avatar (2009)
                              >

Interstellar (2014)
2 files
Move to Movies/Interstellar (2014)
                              >

TV Shows

Lost
18 episodes · 2 seasons
Move to TV Shows/Lost
                              >
```

Tapping an item opens its detail screen or bottom sheet.

---

# 34. Mobile Media Detail Screen

For a movie:

```text
← Avatar (2009)

Movie
High confidence

Proposed structure

Avatar (2009)
├ Avatar (2009).mkv
├ Avatar (2009).en.srt
└ Poster.jpg

3 file operations

Original location
Downloads/Movies/Random

Destination
Movies/Avatar (2009)

[ View File Operations ]
```

For TV:

```text
← Lost

TV Show
18 episodes · 2 seasons

Season 01
10 episodes

Season 02
8 episodes
```

Each season can expand to show its operations.

---

# 35. File Operations List

Use clear operation labels:

```text
MOVE
RENAME
MOVE + RENAME
UNCHANGED
SKIP
CONFLICT
```

Example:

```text
Lost.S01E01.1080p.mkv
↓
Lost - S01E01.mkv

Move + Rename
```

Use destination path as secondary text.

---

# 36. Filters

Review filters should include:

```text
Operation type
Media type
Status
Confidence
```

Desktop:

Use a compact filter popover.

Mobile:

Use a bottom sheet.

Do not permanently occupy large screen areas with advanced filters.

---

# 37. Review Filter Bottom Sheet

Example:

```text
Filter Results

Media
☑ Movies
☑ TV Shows
☑ Subtitles
☑ Artwork

Operations
☑ Move
☑ Rename
☑ Move + Rename
☑ Unchanged

Status
☑ Ready
☑ Conflict
☑ Skipped

[ Clear ]
[ Apply Filters ]
```

Use proper checkbox/radio semantics.

---

# 38. Sort Options

Support:

```text
Title
Media type
Season
Operation type
Status
Original location
Destination
```

Default sort should emphasize media hierarchy rather than raw filenames.

---

# 39. Conflict Presentation

Conflicts must be highly visible but not alarming.

Example:

```text
⚠ Destination already exists

Lost - S01E01.mkv

Existing:
TV Shows/Lost/Season 01/Lost - S01E01.mkv

Proposed:
Downloads/Lost.S01E01.1080p.mkv
```

Possible resolution actions should depend on the conflict type.

Prefer safe options:

```text
Skip this file
Keep existing file
Choose another destination name
```

Do not default to overwrite.

If overwrite is not supported by the operation engine, do not display it.

---

# 40. Conflict Resolution Screen

Desktop:

Use a side panel or dialog for a single conflict.

For multiple conflicts, provide a dedicated conflict list.

Mobile:

Use a dedicated full-screen conflict resolver for complicated conflicts.

Example:

```text
Resolve Conflict

Destination file already exists

Proposed file
Lost.S01E01.1080p.mkv
1.4 GB

Existing file
Lost - S01E01.mkv
1.6 GB

Destination
TV Shows/Lost/Season 01/

Choose an action

○ Keep existing and skip proposed file
○ Rename proposed file

[ Cancel ]
[ Save Resolution ]
```

Do not imply two files are equivalent simply because their names match.

---

# 41. Apply Button Behavior

`Apply Changes` is enabled only when:

* A valid organization plan exists
* No unresolved blocking conflict remains
* The filesystem state has not invalidated the plan

When no operations are required:

```text
Library already organized
```

Replace the button with:

```text
Done
```

---

# 42. Confirmation Before Apply

Always require a final confirmation.

Desktop:

Use a modal dialog.

Mobile:

Use a bottom sheet or dialog.

Content:

```text
Apply 231 changes?

KP File Manager will:

• Move 146 files
• Rename 82 files
• Leave 8 files unchanged
• Skip 3 files

No existing files will be overwritten.

You can undo this operation until the app is closed.

[ Cancel ]
[ Apply 231 Changes ]
```

The primary button should include the operation count.

---

# 43. Apply Confirmation Safety

If the source or destination includes removable/external storage, optionally show:

```text
Keep the storage connected until the operation finishes.
```

Do not use overly dramatic warnings.

---

# 44. Applying Changes Screen

Once the operation begins, replace the review screen with clear progress.

```text
Organizing Library

████████████████░░░░

178 of 231 operations completed

Currently moving
Lost.S02E04.1080p.mkv

Destination
TV Shows/Lost/Season 02/

53 remaining
```

Show operation count rather than only file count when those values differ.

---

# 45. Applying Changes Controls

Do not allow normal navigation during a critical filesystem operation unless the operation architecture explicitly supports it.

Provide:

```text
Cancel
```

only if cancellation can be implemented safely.

If safe cancellation is not supported, do not present a fake cancel button.

The UI may instead show:

```text
Please keep KP File Manager open until the operation completes.
```

---

# 46. Operation Failure Handling

A failure should not automatically discard successful work.

Show progress and record errors.

Example:

```text
Organizing Library

218 of 231 operations completed

1 operation failed
```

Do not interrupt the entire workflow with a modal for every failed file.

Collect recoverable errors and present them on the results screen.

---

# 47. Results Screen

After completion:

```text
Library Organized

231 changes processed

✓ 226 completed
↷ 3 skipped
⚠ 2 failed

Movies
24 organized

TV Episodes
186 organized

[ View Details ]

[ Undo Changes ]      [ Done ]
```

`Done` is the primary action.

`Undo Changes` should be secondary but clearly discoverable.

---

# 48. Partial Success Results

Example:

```text
Completed with Issues

226 changes completed successfully.
2 files could not be moved.

[ View Failed Files ]
[ Undo Completed Changes ]
[ Done ]
```

Do not describe partial completion as complete success.

---

# 49. Failure Detail Screen

Display:

```text
Failed Operations

Lost.S02E03.mkv

Could not move file
Permission denied

Original
/path/...

Destination
/path/...

[ Retry ]
```

Support retry only when technically safe.

Otherwise show:

```text
Return to Review
```

---

# 50. Undo Confirmation

Before undo:

```text
Undo organization?

This will move successfully organized files back
to their original names and locations.

[ Cancel ]
[ Undo Changes ]
```

---

# 51. Undo Progress

Use a dedicated progress state:

```text
Undoing Changes

84 of 231 operations restored

Restoring
Lost.S01E04.1080p.mkv
```

Do not allow another organization operation during undo.

---

# 52. Undo Complete

```text
Changes Undone

Files were restored to their original
names and locations.

[ Done ]
```

If some undo operations fail:

```text
Undo completed with issues

224 files were restored.
2 files could not be restored.

[ View Issues ]
[ Done ]
```

---

# 53. Batch Rename Screen

Keep this utility simple.

Desktop:

```text
Batch Rename

Folder
[ /TV/Lost/Season 01                  ] [ Browse ]

Naming

Prefix
[ Episode_                            ]

Starting number
[ 1 ]

Digit padding
[ 3 ]

Suffix
[                                    ]

Extension
[ Keep original ▼ ]

Preview

Original                    New
video1.mp4                  Episode_001.mp4
video2.mp4                  Episode_002.mp4
video3.mp4                  Episode_003.mp4

                          [ Rename 3 Files ]
```

Do not hide the preview behind another step.

Update preview as fields change.

---

# 54. Batch Rename Mobile

Use vertically stacked fields.

Display the first several preview items directly.

Example:

```text
Preview

video1.mp4
→ Episode_001.mp4

video2.mp4
→ Episode_002.mp4

View all 32 files
```

Sticky bottom action:

```text
Rename 32 Files
```

---

# 55. Batch Rename Conflicts

Display conflicts before execution.

Example:

```text
2 naming conflicts

Episode_003.mp4 already exists.
```

Disable the final rename action until blocking conflicts are resolved.

Never overwrite silently.

---

# 56. Find & Replace Screen

Desktop:

```text
Find & Replace

Folder
[ /Movies                         ] [ Browse ]

Find
[ 1080p                           ]

Replace with
[ BluRay                          ]

File types
(•) All files
( ) Specific extension [ .mkv ]

Preview

Movie.1080p.mkv
→ Movie.BluRay.mkv

Movie.1080p.en.srt
→ Movie.BluRay.en.srt

                        [ Apply to 2 Files ]
```

Show:

```text
Leave "Replace with" blank to remove matching text.
```

as small helper text.

---

# 57. Find & Replace Mobile

Use the same form in a single column.

Place preview beneath the inputs.

Use a sticky primary action.

---

# 58. Settings Screen

Keep settings minimal.

Sections:

```text
Organization

Default scan depth
4 levels

Movie folder name
Movies

TV folder name
TV Shows


Appearance

Theme
System default / Light / Dark


Behavior

Confirm before applying changes
On
```

The confirmation-before-apply setting should preferably remain mandatory if filesystem safety depends on it.

Do not expose internal parser implementation details as settings.

---

# 59. Theme Setting

Options:

```text
System default
Light
Dark
```

Default:

```text
System default
```

Theme changes should apply immediately.

No restart required where technically practical.

---

# 60. Android Storage Permission State

When storage access is required, explain the need before launching the system picker or permission flow.

Example:

```text
Choose a Media Folder

KP File Manager needs access to the folder containing
your movies and TV shows so it can scan and organize them.

You choose exactly which folder the app can access.

[ Choose Folder ]
```

Do not use threatening language.

Use the Android Storage Access Framework or appropriate supported platform mechanism.

---

# 61. Android Permission Error

If access is revoked:

```text
Folder Access Required

KP File Manager no longer has permission to access:

TV Shows

Choose the folder again to continue.

[ Choose Folder Again ]
```

---

# 62. Native Folder Picker

Use the platform's native folder picker rather than building a custom filesystem browser unless the existing architecture requires otherwise.

After returning from the picker, show the selected path inside KP File Manager.

---

# 63. Empty States

Every screen must have an intentional empty state.

## Organizer

```text
No folder selected

Choose the folder containing your movies
and TV shows to get started.

[ Select Folder ]
```

## Batch Rename

```text
Choose a folder to preview rename operations.
```

## Find & Replace

```text
Choose a folder to find text in filenames.
```

## Conflict tab

```text
No conflicts

All proposed changes are ready to apply.
```

## Skipped tab

```text
Nothing skipped
```

Do not leave blank panels.

---

# 64. Loading States

Use loading indicators only when work is actually happening.

For short operations:

```text
small inline spinner
```

For scanning and file organization:

```text
progress bar + operation information
```

Avoid full-screen spinners with no explanation.

---

# 65. Skeleton Loading

Skeleton placeholders are optional.

Use them only if actual content is being loaded asynchronously and the expected layout is known.

Do not use decorative skeletons during filesystem scanning. Show real progress information instead.

---

# 66. Toasts and Snackbars

Use transient messages for non-critical confirmation.

Examples:

```text
Scan options updated
Filters cleared
Theme changed to Dark
```

Do not use toast/snackbar messages for critical filesystem failures.

Critical errors must remain visible until acknowledged.

---

# 67. Long Paths

Filesystem paths can be very long.

Rules:

Desktop:

* Use middle ellipsis when possible
* Show full path on hover or details view
* Allow copying the path

Example:

```text
C:\Users\Alex\…\Lost\Season 01
```

Mobile:

```text
/storage/emulated/0/…/Season 01
```

Never allow long paths to push primary actions off-screen.

---

# 68. Long Filenames

Long filenames should:

* Use up to two lines when space allows
* Otherwise truncate with ellipsis
* Preserve the extension when practical
* Reveal full name in details

Do not horizontally scroll the entire screen because of one filename.

---

# 69. Tables on Desktop

For file-level operation views, use a table where appropriate.

Columns:

```text
Status
Original Name
New Name
Type
Operation
Destination
```

Optional:

```text
Confidence
Size
```

Do not show every possible metadata field by default.

Allow column sorting.

Keep the first column visible if horizontal scrolling is required.

---

# 70. Lists on Mobile

Do not compress desktop tables onto mobile.

Convert rows into cards/list items.

Example:

```text
🎬 Lost.S01E01.1080p.mkv

Move + Rename

Lost - S01E01.mkv
TV Shows/Lost/Season 01/

Ready
```

Use a tap target for details.

---

# 71. Status Chips

Use consistent status chips.

Supported statuses:

```text
Ready
Conflict
Skipped
Unchanged
Failed
Completed
Low confidence
```

Chips should include text.

Do not rely exclusively on chip colors.

---

# 72. Icons

Use one consistent icon library.

Do not mix unrelated icon styles.

Recommended icon concepts:

```text
folder
movie
tv
subtitles
image
document
warning
error
check
search
edit
undo
settings
filter
sort
chevron
more
```

Use recognizable icons, not abstract custom symbols.

Always pair unfamiliar icons with labels.

---

# 73. Animations

Animations should be restrained.

Allowed:

```text
150–250 ms button/state transitions
subtle tab indicator movement
expand/collapse transitions
progress bar movement
bottom sheet transition
dialog fade/scale
```

Avoid:

```text
page flying transitions
spring-heavy motion
decorative parallax
animated backgrounds
large scale effects
confetti
```

Respect the operating system's reduced-motion setting.

---

# 74. Desktop Window Sizes

Recommended minimum usable window:

```text
900 x 600
```

The UI should degrade gracefully below the preferred width.

At narrower widths:

* Collapse navigation labels
* Stack summary sections
* Replace split review with single-pane navigation if necessary

Do not allow important controls to overlap.

---

# 75. Android Safe Areas

All Android screens must respect system insets.

Account for:

```text
status bar
display cutouts
camera holes/notches
navigation bar
gesture navigation indicator
IME / keyboard
```

Do not position content using hardcoded top or bottom offsets.

Use system-provided window insets.

---

# 76. Android Edge-to-Edge

If using edge-to-edge layouts:

* Background surfaces may extend behind system bars
* Interactive content must remain inside safe insets
* Top app bar content must remain below status bar/cutout
* Bottom actions must remain above navigation/gesture areas

Example conceptual structure:

```text
System Status Area
────────────────────
Safe Top Inset

Top App Bar

Scrollable Content

Sticky Action Area
Safe Bottom Inset
────────────────────
Gesture Navigation Area
```

---

# 77. Android Keyboard Handling

When the software keyboard opens:

* Keep the currently edited field visible
* Move or resize bottom actions as appropriate
* Allow forms to scroll
* Do not let the keyboard cover confirmation buttons

This is especially important on:

```text
Batch Rename
Find & Replace
Settings fields
Conflict rename fields
```

---

# 78. Android Orientation Changes

Portrait is the primary phone layout.

Landscape must remain functional.

In landscape:

* Use available width efficiently
* Consider two-column forms
* Ensure bottom sheets do not exceed usable height
* Allow scrolling
* Preserve scan/review state

Do not restart scans due to orientation changes.

---

# 79. Small Screens

For unusually small Android displays:

* Reduce horizontal padding from 16 dp to approximately 12 dp if required
* Stack buttons vertically where two buttons no longer fit
* Preserve 48 dp touch targets
* Allow text wrapping
* Keep sticky bottom actions accessible

Never shrink text below readable sizes merely to fit content.

---

# 80. Large Screens and Tablets

On tablets:

Use:

```text
Navigation rail
+
multi-pane content
```

The review screen may use the desktop-style split layout.

Do not stretch a single narrow phone column across a large tablet display.

Use maximum content widths.

---

# 81. Responsive Layout Model

Suggested conceptual breakpoints:

```text
Compact
< 600

Medium
600–839

Expanded
>= 840
```

Compact:

```text
Bottom navigation
Single-column content
Full-screen detail navigation
Bottom sheets
```

Medium:

```text
Navigation rail
Wider forms
Optional two-column content
```

Expanded:

```text
Expanded navigation
Split views
Desktop-style review tools
```

Exact breakpoint implementation may follow the application's UI framework.

---

# 82. Top App Bar

Desktop:

Usually show:

```text
Page Title
optional contextual actions
```

Do not duplicate navigation labels unnecessarily.

Mobile:

Use a standard top app bar.

Examples:

Root:

```text
KP File Manager
```

or:

```text
Organize Media
```

Nested:

```text
← Avatar (2009)
```

Nested screens must provide clear back navigation.

---

# 83. Dialog Rules

Use dialogs for:

* Final confirmation
* Cancel confirmations
* Small conflict resolution tasks
* Destructive actions

Do not use dialogs for large datasets or complex workflows.

Complex conflict review should be a dedicated screen.

Desktop dialog width:

```text
400–560 px
```

Mobile dialog width should respect platform margins.

---

# 84. Bottom Sheet Rules

Use bottom sheets on mobile for:

* Filters
* Sort options
* Scan options
* Small item details
* Small action menus
* Simple conflict choices

Bottom sheets must:

* Have a clear title
* Support drag dismissal only where accidental dismissal is harmless
* Respect safe areas
* Scroll internally when needed
* Keep primary actions visible

---

# 85. Unsaved Review Navigation

If the user leaves the review screen before applying changes:

No confirmation is required if leaving only discards a generated preview and no manual conflict resolutions need preservation.

If the user has manually resolved conflicts or customized operations, show:

```text
Leave review?

Your review changes will be discarded.

[ Stay ]
[ Leave Review ]
```

---

# 86. Rescan

Provide `Rescan` from the review screen.

If the current preview contains manual resolutions:

```text
Rescan folder?

Rescanning will replace the current preview
and clear your manual conflict resolutions.

[ Cancel ]
[ Rescan ]
```

---

# 87. Search Within Review

For large libraries, desktop should support search.

Placeholder:

```text
Search titles or filenames
```

Search should match:

```text
movie title
series title
filename
destination filename
```

Mobile can expose search from the top app bar.

Do not require search for normal-sized libraries.

---

# 88. Proposed Folder Structure View

The user should be able to toggle between:

```text
Library
Files
```

`Library` shows the hierarchy:

```text
Lost
└ Season 01
```

`Files` shows individual operations.

Desktop can implement this as segmented controls.

Mobile can use tabs.

Default:

```text
Library
```

---

# 89. Confidence Display

Do not show confidence scores such as:

```text
87.3%
```

unless they provide genuine user value.

Prefer understandable labels:

```text
High confidence
Medium confidence
Low confidence
```

Only emphasize low-confidence items.

High-confidence results may omit the label in dense list views.

---

# 90. Low Confidence Review

Example:

```text
Low confidence

Episode 05.mkv

Detected as:
Lost
Season 01
Episode 05

Based partly on folder structure.

[ View Details ]
```

The user should understand why an item needs attention.

---

# 91. Keyboard Shortcuts on Desktop

Recommended:

```text
Ctrl/Cmd + O     Select folder
Ctrl/Cmd + F     Search review
Ctrl/Cmd + Enter Apply changes when available
Esc              Close dialog/sheet
Ctrl/Cmd + Z     Undo latest completed organization
```

Do not allow `Ctrl/Cmd + Enter` to bypass confirmation.

It may open the confirmation dialog.

---

# 92. Tooltips

Use tooltips only for icon-only desktop controls.

Examples:

```text
Filter
Sort
Refresh
Copy path
```

Primary actions should not require tooltips.

---

# 93. Context Menus

Optional desktop context menus may provide:

```text
View details
Copy original path
Copy destination path
Skip file
```

Do not hide critical workflow actions exclusively inside context menus.

---

# 94. Visual Density

Use moderate density.

KP File Manager is a utility application, so information density can be higher than a consumer entertainment app.

However:

* Avoid cramped rows
* Maintain clear grouping
* Use at least 8 px between unrelated controls
* Keep table row height around 40–48 px desktop
* Keep mobile rows at least 56 dp

---

# 95. Error Copy Style

Errors should answer:

```text
What happened?
What does it affect?
What can the user do?
```

Good:

```text
Could not move 2 files

KP File Manager does not have permission to write
to the destination folder.

Choose another destination or update folder access.
```

Bad:

```text
ERROR CODE FS_MOVE_EXCEPTION
```

Technical codes may appear under expandable details.

---

# 96. Success Copy Style

Keep success states concise.

Good:

```text
Library Organized

226 files were moved or renamed successfully.
```

Avoid celebratory effects.

---

# 97. Destructive Safety

Never use a large red primary button for standard organization.

`Apply Changes` should use the normal primary color.

Red/destructive styling is reserved for genuine destructive actions.

The organizer is primarily moving and renaming files, not deleting them.

---

# 98. No Silent Operations

The UI must enforce:

```text
Scan
→
Preview
→
Confirm
→
Apply
```

No automatic organization immediately after scanning.

No setting should bypass preview.

---

# 99. Shared Components

Create reusable UI components for:

```text
PageHeader
SectionHeader
PrimaryButton
SecondaryButton
DestructiveButton
FolderSelector
PathDisplay
ProgressPanel
StatusChip
EmptyState
ErrorState
MediaIcon
MediaSummaryCard
OperationRow
OperationList
MediaTree
DestinationTree
ConflictCard
FilterSheet
SortSheet
ConfirmationDialog
ResultSummary
StickyBottomAction
```

Avoid implementing separate slightly different versions for each screen.

---

# 100. Shared Operation Status Model

The UI should map operation state consistently.

Suggested states:

```text
pending
ready
running
completed
skipped
conflict
failed
unchanged
```

Map these centrally to:

```text
label
icon
semantic color
accessibility description
```

---

# 101. Suggested Organize Media State Machine

Implement the primary screen around explicit workflow states.

```text
idle
folderSelected
scanning
scanFailed
scanComplete
reviewing
resolvingConflicts
confirming
applying
applyComplete
applyPartialFailure
undoing
undoComplete
```

Do not represent major workflow state through a large collection of unrelated boolean flags.

---

# 102. Primary Workflow

The ideal first-time flow should require approximately:

```text
1. Select Folder
2. Scan Folder
3. Review Changes
4. Apply Changes
5. Confirm
```

Do not insert setup screens between these steps.

Default settings should be sufficient for most users.

---

# 103. Desktop Primary Flow Example

## Step 1

```text
Organize Media

No folder selected.

[ Select Folder ]
```

## Step 2

```text
Selected Folder
D:\Media Downloads

Scan depth: 4

[ Scan Folder ]
```

## Step 3

```text
Scanning Media...

842 files scanned
```

## Step 4

```text
Review Changes

23 Movies
164 Episodes
4 Conflicts

[ Resolve Conflicts ]
```

## Step 5

```text
Review Changes

All changes ready.

[ Apply Changes ]
```

## Step 6

```text
Apply 212 changes?

[ Cancel ]
[ Apply 212 Changes ]
```

## Step 7

```text
Library Organized

[ Undo Changes ]
[ Done ]
```

---

# 104. Mobile Primary Flow Example

Screen 1:

```text
Organize

Select the folder containing your
movies and TV shows.

[ Select Folder ]
```

Screen 2:

```text
Organize

Media Downloads

/storage/emulated/0/Media

Scan depth
4 levels

[ Scan Folder ]
```

Screen 3:

```text
Scanning Media

428 files scanned

24 Movies
86 Episodes

[ Cancel ]
```

Screen 4:

```text
Review Changes

110 ready
2 conflicts

Movies
24

TV Shows
8

[ Resolve 2 Conflicts ]
```

Screen 5:

```text
Review Changes

112 changes ready

[ Apply Changes ]
```

Screen 6:

```text
Apply 112 changes?

[ Cancel ]
[ Apply ]
```

Screen 7:

```text
Library Organized

112 changes completed

[ Undo Changes ]

[ Done ]
```

---

# 105. Implementation Priorities

Codex should implement the interface in this order.

## Phase 1: Design Foundation

Implement:

```text
theme tokens
light theme
dark theme
typography
spacing
buttons
inputs
cards
status components
safe-area handling
responsive breakpoints
```

---

## Phase 2: Navigation

Implement:

```text
desktop sidebar
Android bottom navigation
tablet navigation rail
top app bars
responsive switching
```

---

## Phase 3: Organizer Initial State

Implement:

```text
folder selector
selected folder state
scan options
Scan Folder action
empty state
permission state
```

---

## Phase 4: Scanning UI

Implement:

```text
progress
statistics
current path
cancel handling
error state
no-media state
```

Connect UI to the scanner state rather than simulating progress.

---

## Phase 5: Review UI

Implement:

```text
summary
tabs
detected library
proposed structure
file operations
search
filters
sort
item details
confidence indicators
```

Implement desktop split view and mobile single-column views separately where appropriate.

---

## Phase 6: Conflicts

Implement:

```text
conflict list
conflict cards
resolution dialog
mobile conflict screen
resolved/unresolved state
Apply button gating
```

---

## Phase 7: Confirmation and Apply

Implement:

```text
confirmation
operation progress
partial failures
results
failure details
```

---

## Phase 8: Undo

Implement:

```text
undo confirmation
undo progress
undo result
partial undo errors
```

---

## Phase 9: Additional Tools

Implement:

```text
Batch Rename
Batch Rename preview
Find & Replace
Find & Replace preview
conflict states
```

Reuse organizer UI components where appropriate.

---

## Phase 10: Settings

Implement:

```text
scan defaults
destination defaults
theme setting
system theme
```

---

## Phase 11: Responsive and Accessibility Pass

Test:

```text
small Android phone
large Android phone
phone landscape
tablet portrait
tablet landscape
small desktop window
1080p desktop
1440p desktop
high-DPI displays
large font sizes
light mode
dark mode
keyboard-only desktop navigation
screen reader labels
Android gesture navigation
Android button navigation
display cutouts
software keyboard
```

---

# 106. Final UX Acceptance Criteria

The UI is complete when:

* Organize Media is the obvious default destination.
* A first-time user can identify the primary workflow without instructions.
* Selecting a folder requires one obvious action.
* Scanning never modifies files.
* Scan progress provides meaningful feedback.
* Long scans do not make the application appear frozen.
* Results clearly distinguish movies and TV shows.
* Users can view the proposed destination structure before applying changes.
* Original and destination paths can be inspected.
* Conflicts are clearly identified.
* Blocking conflicts prevent unsafe application.
* No existing file is silently overwritten.
* Low-confidence detections are visible.
* Final confirmation appears before filesystem changes.
* Apply progress shows real operation status.
* Partial failures are clearly reported.
* Undo is available after successful organization during the current session.
* Batch Rename shows a preview before execution.
* Find & Replace shows a preview before execution.
* Light mode is fully supported.
* Dark mode is fully supported.
* Neither theme contains unreadable or low-contrast controls.
* Desktop supports mouse and keyboard operation.
* Android controls meet minimum touch-target requirements.
* Android layouts respect status bars, cutouts, gesture areas, navigation bars, and keyboards.
* Phone layouts never rely on desktop-style tables.
* Tablet layouts make use of additional screen width.
* Long paths and filenames do not break layouts.
* Empty states explain the next action.
* Errors explain both the problem and recovery action.
* Animations are subtle and functional.
* There are no decorative transitions that slow down the workflow.
* Critical actions remain clearly separated from secondary actions.
* The organizer can be completed with as few steps as reasonably possible.

---

# 107. Core Design Principle

Every major screen should answer one question immediately:

```text
Organize screen:
What folder should I organize?

Scanning screen:
What is happening?

Review screen:
What is going to change?

Conflict screen:
What needs my attention?

Confirmation:
Am I ready to apply this?

Progress screen:
How far along is the operation?

Results screen:
What happened?
```

Whenever a screen contains controls that do not help answer its current question, move them to a secondary menu, expandable section, settings screen, or remove them.

KP File Manager should feel like a focused professional utility rather than a general-purpose file explorer.
