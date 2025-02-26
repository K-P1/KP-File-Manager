# File Manager (Sorting & Renaming)

A Python-based File Manager that offers:
1. **Sort Files** into categorized folders (based on file type).
2. **Mass Rename** of files (supports **sequential** or **random** numbering).
3. **Music Rename** using ID3 tags (e.g., `01. Artist - Title (feat. X)`).

You can run it in:
- A **GUI** (using Tkinter with radio buttons to select one operation).
- A **CLI** (via `main.py`, if desired, though this README focuses on the GUI usage).

## Features

1. **Sort Files**  
   - Reads extensions from `config.json` (or default rules) to determine categories (e.g., Images, Documents, Audio, etc.).  
   - Moves files from a **source** folder into a **destination** folder, creating subfolders for each category.  
   - Can run in “preview” (dry-run) mode to show what would happen, or “run” mode to actually move files.  
   - **Undo** feature that tries to move files back to their original locations and remove any empty directories created.

2. **Mass Rename**  
   - Renames all files of a specific **extension** in the **source** folder.  
   - Allows specifying:
     - **Prefix** (e.g., `Renamed_`).  
     - **Start Index** (e.g., `1`, `10`, etc.).  
     - **Zero Padding** (e.g., `2` → `01, 02`; `3` → `001, 002`).  
   - **Scramble** mode generates **unique random** numbers instead of sequential counters.  
     - **Scramble Multiplier** (1..10) → If there are `N` files, the random numbers are taken from `1..(N * multiplier)` without duplicates.  
   - **Undo** reverts to original filenames.

3. **Music Rename**  
   - Specifically targets **audio files** (commonly `.mp3`), reading **ID3 tags** (artist, title, tracknumber).  
   - Renames them to a format such as:  
     ```
     01. MainArtist - SongTitle (feat. OtherArtist).mp3
     ```  
   - If no ID3 data exists, falls back to a simple cleanup of the original filename.  
   - **Undo** reverts these renames as well.

4. **Logging**  
   - All operations (Preview, Run, Undo) are logged to a file (`operation_logs.txt`), **overwritten** each time an operation is performed.  
   - This helps track exactly what changed, what was previewed, and any errors.

5. **Undo** (for all operations)  
   - Reverses file movements and/or renames.  
   - Tries to restore original paths or filenames.  
   - For sorted files, it also removes any newly created **empty** directories in the destination folder.

---

## Requirements

- **Python 3.7+** (recommended).
- **Mutagen** (optional, but needed for Music Rename to read ID3 tags). Install via:
  ```bash
  pip install mutagen
  ```
- **Tkinter** (included in most Python distributions by default):
  - On Linux, you may need to install `python3-tk`.

---

## Installation

1. **Clone or Download** this project into a local directory:
   ```bash
   git clone https://github.com/KP-1/file_manager.git
   cd file_manager
   ```
2. (Optional) **Create & activate** a virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```
3. **Install Mutagen** if you plan to use Music Rename:
   ```bash
   pip install mutagen
   ```
4. You now have everything needed to run the GUI.

---

## Directory Structure

```
KP-File_Manager/
├── main.py          # CLI entry point (optional usage)
├── sorter.py        # Sorting logic
├── renamer.py       # Mass & Music renaming logic
├── gui.py           # Tkinter GUI
├── config.json      # Category definitions for sorting
├── utils.py         # Utility functions (optional placeholders)
└── README.md        # This documentation
```

---

## Usage (GUI)

**Launch** the GUI by running:
```bash
python gui.py
```
You’ll see a window with the following sections:

1. **Operation Selection (Radio Buttons)**  
   - **Sort Files**  
   - **Mass Rename**  
   - **Music Rename**  
   Only **one** can be selected at a time.

2. **Source Folder**  
   - Click “Browse” to pick the folder containing the files to sort/rename.  

3. **Destination Folder**  
   - **Only enabled** when “Sort Files” is selected.  
   - Click “Browse” to choose where sorted files should go.

4. **Mass Rename Fields**  
   - **Only visible/enabled** when “Mass Rename” is selected.  
   - **File Extension**: e.g., `mp4`, `jpg`.  
   - **Prefix**: (X string) to prepend to each new filename.  
   - **Start Index**: (Y integer) from which numbering begins (e.g., 1 → `001`, 2 → `002` if padded).  
   - **Zero Padding**: how many digits to pad.  
   - **Scramble** (checkbox): if checked, files receive **unique random** numbers.  
   - **Scramble Multiplier (1..10)**: determines the **max range** of those random numbers.  
     - If you have 10 files and choose multiplier 2, random numbers are from 1..20 without duplicates.

5. **Music Rename**  
   - **Only** requires a **Source Folder**.  
   - Renames `.mp3` files using ID3 tags:
     - Format: `TrackNo. Artist - Title (feat. OtherArtists).mp3`.  
   - If metadata is missing, does a **basic cleanup** of the filename.

6. **Buttons**  
   - **Preview**:  
     - Runs a **dry-run** mode to **show** (in `operation_logs.txt`) which files **would** be sorted or renamed, **without** actually doing it.  
   - **Run**:  
     - Executes the chosen operation for real (sort / rename / music rename).  
   - **Undo**:  
     - Attempts to revert the last operation:
       - **Sort** → moves files back to their original location, removes empty destination folders.  
       - **Mass Rename** & **Music Rename** → reverts filenames to original.  
   - **Quit**:  
     - Closes the GUI.

### Log File
After each operation (Preview, Run, or Undo), the GUI writes logs to **`operation_logs.txt`**, located in the same directory. The file is **overwritten** each time you do a new operation.

---

## Usage (CLI)

If you prefer command-line usage, you can run:
```bash
python main.py --help
```
This script supports arguments like `--sort`, `--mass-rename`, `--music-rename`, `--source`, `--dest`, `--dry-run`, `--undo`, etc.  
*(You’ll need to adapt the code in `main.py` if you want to incorporate scramble or advanced prefix/padding fields. The GUI is the simpler interface for that.)*

---

## Configuration for Sorting

- **`config.json`** defines how file extensions map to categories:
  ```json
  {
    "Images": ["jpg", "jpeg", "png", "gif"],
    "Documents": ["pdf", "doc", "docx", "txt", "xls", "xlsx"],
    "Audio": ["mp3", "wav", "ogg"],
    "Video": ["mp4", "mov", "avi"],
    "Archives": ["zip", "rar", "7z"]
  }
  ```
- When you choose **Sort Files**, the **sorter.py** module reads this JSON (if present) to see where each extension belongs.  
  - Files with extensions not listed go into **“Others”** by default.

---

## Advanced Notes

1. **Scramble in Mass Rename**  
   - The application picks a **random unique** integer for each file, in `[1..(file_count * scramble_multiplier)]`.  
   - Example: If you have 5 `.mp4` files and **scramble_multiplier=3**, random numbers are from `1..15` (no duplicates).  
   - Zero padding is still applied, so if you set 2, you might see filenames like `Renamed_07.mp4`.

2. **Zero Padding**  
   - If you enter `3`, you’ll get `001, 002, 010`, etc.  
   - If you enter `0`, you’ll get `1, 2, 10, etc.` (no padding).

3. **Undo Limitations**  
   - The application tries to remember **original** paths (for sorting) or filenames (for renaming) in memory.  
   - Once you exit the program, that **history** is gone, so you can’t undo previous sessions.  
   - **If** you do multiple operations in one session, the last undo tries to revert the most recent operation first.

4. **Music Rename**  
   - Requires **Mutagen** for ID3 reading. If you run into import errors, install via `pip install mutagen`.  
   - Only tested with `.mp3` files. To support `.flac`, `.m4a`, etc., you’d extend the metadata reading logic (still in `renamer.py`).

5. **Error Handling**  
   - If something goes wrong (e.g., file conflicts, permissions issues), you’ll see **error logs** in the console and `operation_logs.txt`.  
   - For random numbering, if the **file_count** is larger than the scramble range, you’ll see a warning in the logs (some fallback approach might apply).

---

## Troubleshooting

1. **No “Tkinter”**:
   - On some minimal Linux distros, you might need to install it:
     ```bash
     sudo apt-get install python3-tk
     ```
2. **No “Mutagen”** or “ImportError”**:
   - Install via `pip install mutagen`.
3. **Zero Padding Doesn’t Show**:
   - Ensure you’re entering a valid **integer** (e.g., `2`) in the Zero Padding field.  
   - Check `operation_logs.txt` to see if the code is receiving `zero_pad=0`.
4. **Scramble Multiplier** is **ignored**:
   - Make sure it’s between **1** and **10**. Anything outside this range is clamped to **1**.  
   - Verify the logs for `scramble_multiplier=...`.
5. **Undo** Doesn’t Restore**:
   - Undo only works immediately after an operation. If you close the app and reopen, the memory of old paths/names is lost.  
   - If some files were manually altered after the operation, undo might fail.

---

## Contributing

1. Fork the repository and create a new branch.  
2. Make your changes.  
3. Submit a pull request with a detailed explanation of your work.

---

### Contact

For questions, suggestions, or to report a bug, feel free to open an **issue** or submit a **pull request**.