# KP File Manager features

KP File Manager helps organise and rename the files in a folder. It is available as a Windows desktop app and an Android app. The main workflow is the same on both: choose a folder, choose an operation, review the proposed changes, and then apply them.

## What it can do

### Sort files into folders

The app can move the files in a selected folder into category folders. By default, the category folders are created inside the source folder, but sorting can use a different destination folder.

Files are sorted by their extension. The current categories are:

| Category | File types |
| --- | --- |
| Images | JPG, JPEG, PNG, GIF, TIFF, BMP (Android also includes WEBP and HEIC) |
| Documents | PDF, DOC, DOCX, TXT, XLS, XLSX, PPT, PPTX (Android also includes CSV) |
| Audio | MP3, WAV, OGG, FLAC (Android also includes M4A and AAC) |
| Video | MP4, MOV, AVI, MKV (Android also includes WEBM) |
| Archives | ZIP, RAR, 7Z, TAR, GZ |
| Others | Files whose type is not in a category above |

Folders are sorted based on the most common file type found inside them. Empty folders go into an `EmptyFolders` folder. If a target name is already taken on the desktop app, it adds a number such as `_1` to avoid overwriting it.

### Group series or similarly named files

Instead of sorting by file type, turn on **Series mode**. The app looks for files with the same cleaned-up title and puts matching groups into a folder named after that title.

It ignores common episode, season, year, resolution, codec, and release-label text. For example, files named `Example.Show.S01E01.1080p.mkv` and `Example Show S01E02.mkv` can be grouped under `Example Show`. A title must match at least two items before a group is made.

### Batch rename files with numbers

The **Batch** or **Mass Rename** option renames matching files to a prefix followed by a number.

You can choose:

- A file extension to include, such as `mp4`. Use `*` or leave it blank to include every file type.
- A prefix, such as `Holiday_`.
- The starting number.
- How many digits to show. For example, three digits creates `Holiday_001.mp4`.

Files keep their original extension when every file type is included. When a single extension is selected, the new names use that extension.

### Use scrambled numbers for batch renames

Batch renaming can use unique random numbers instead of a simple sequence. The scramble-range multiplier controls how wide the random-number pool is: a larger value gives numbers more spread. The available multiplier range is 1 to 10.

### Rename music files

The **Music** option processes MP3 files.

On Android, it uses the embedded track number, artist, and title when all needed tag information is available, producing names such as `01. Artist - Song.mp3`. If that information is incomplete, it cleans up the existing filename by replacing underscores with spaces and removing extra spaces.

On the current desktop app, music renaming uses that filename-cleanup fallback: it replaces underscores with spaces and removes extra spaces. It does not currently apply embedded MP3 tags to construct the name.

### Find and replace text in filenames

The **Replace** or **Keyword Rename** option finds text in the filename (not the extension) and replaces it with new text. It only changes files that match the chosen extension; use `*` or leave the extension blank to consider all file types.

The text to find is required. Leave the replacement blank to remove the matching text. Matching is case-sensitive, so `Draft` and `draft` are treated differently.

### Review changes before making them

Both apps have a review step that does not change files.

- On Android, the app shows a list of planned moves and renames on screen, showing up to 30 changes at once.
- On the desktop app, the review is written to `desktop/runtime/operation_logs.txt`. The app tells you where to find the log when the review finishes.

Use this step to check the destination folders and new names before selecting **Apply changes**.

### See results and errors

Android shows an operation status and reports failed items without hiding the files that did succeed. It also keeps an internal operation log. The desktop app writes operation details and errors to `desktop/runtime/operation_logs.txt` and shows completion or error messages in the window.

### Undo the last operation

Both versions can try to reverse changes made during the current app session. Undo moves files back to their original location and restores prior names when the affected files are still available.

Undo is not permanent history: it is cleared when the app is closed, and it can fail if files were moved, renamed, deleted, or blocked outside the app after the original operation.

### Keep timestamps on desktop renames

When the desktop GUI applies a batch, music, or find-and-replace rename, it records and restores the file's access and modified timestamps where the operating system permits it.

## How to use the app

1. Choose the source folder containing the files you want to process.
2. Choose **Sort**, **Batch**, **Music**, or **Replace**.
3. Set any options for that operation. For sorting, choosing a destination is optional; leaving it empty uses the source folder.
4. Select **Review changes** and inspect the plan.
5. Select **Apply changes** only when the plan looks right.
6. Use **Undo** before closing the app if you need to reverse the most recent session changes.

## Where the app works

### Windows desktop

The desktop app works with normal local folders. It has a PyQt graphical interface, an older Tk interface, and a command-line entry point. Sorting rules are stored in `desktop/config/categories.json`, so the desktop categories can be changed by editing that file.

### Android

The Android app uses Android's system folder picker. You grant it access to the folder you select; it does not need root access, broad storage permission, a separate file-manager app, or network access. Android remembers the folder permission, but undo history still lasts only for the current session.

For media organization, the output root defaults to the selected source folder. A different output root can be selected before scanning; configured Movies and TV folder paths are reused when present and created when missing. Existing Movies and TV library folders can also be selected separately, which is useful when Android does not allow their shared storage root to be selected.

Undo first restores every available file, then removes empty destination folders that the operation created. Existing folders are never treated as cleanup candidates. Cleanup that a provider temporarily refuses can be retried without repeating successful file restores.

Android 11 and newer do not allow the system picker to grant access to a storage root, the `Download` root, `Android/data`, or `Android/obb`. Some storage providers may also refuse moves or renames. When a normal move is unavailable, the app can try copying and then deleting the original; that fallback may change the file's modified date.

## Important limits

- Operations work on the direct contents of the folder you choose. They do not rename every file throughout all subfolders.
- Always review the plan first, especially when using all file types or a broad find-and-replace term.
- The Android preview and desktop log are plans, not guarantees: a file can still fail when the change is applied because of permissions, a collision, or a storage-provider restriction.

## Mobile: Protected Media Export

The Export tab adds Shizuku-powered discovery of MovieBox's restricted video and subtitle folders. The selected files are normalized using the same KP media organizer as the normal SAF workflow, then safely exported into the device's shared Movies and TV Shows libraries. It includes a responsive selection/review interface, per-file transfer progress, verification, guarded source cleanup and crash-recovery restore. More information: [Protected Media Export](PROTECTED_EXPORT.md).
