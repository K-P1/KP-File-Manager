# File Manager (Sorting & Renaming)

A Python-based File Manager that offers:
1. **Sort Files** into categorized folders (based on file type).
2. **Mass Rename** of files (supports **sequential** or **random** numbering).
3. **Music Rename** using ID3 tags (e.g., `01. Artist - Title (feat. X)`).
4. **Keyword Rename** that replaces a specified keyword in filenames with a user-provided string.
   - Also, if no file extension or '*' is provided in Mass Rename, the script renames **all files** in the source folder, preserving each file’s original extension.

You can run it in:
- A **GUI** – choose between two interfaces:
  - **Tkinter GUI** (file: `gui_old.py`)
  - **PyQt GUI** (file: `gui_new.py`)
- A **CLI** (via `main.py`, if desired; this README focuses on the GUI usage).

---

## Features

1. **Sort Files**  
   - Reads extensions from `config.json` (or default rules) to determine categories (e.g., Images, Documents, Audio, etc.).  
   - Moves files from a **source** folder into a **destination** folder, creating subfolders for each category.  
   - Can run in “preview” (dry-run) mode to show what would happen, or “run” mode to actually move files.  
   - **Undo** feature that tries to move files back to their original locations and remove any empty directories created.

2. **Mass Rename**  
   - Renames all files of a specific **extension** in the **source** folder.  
   - If the file extension is left blank or specified as `*`, the script renames **all files** in the folder and preserves their original extensions.
   - Allows specifying:
     - **Prefix** (e.g., `Renamed_`).
     - **Start Index** (e.g., `1`, `10`, etc.).
     - **Zero Padding** (e.g., `2` → `01, 02`; `3` → `001, 002`).
   - **Scramble** mode generates **unique random** numbers instead of sequential counters.
     - **Scramble Multiplier** (1..10) determines the max range of random numbers.  
       For example, if you have 10 files and choose multiplier 2, random numbers are taken from 1..20 without duplicates.
   - **Undo** reverts to original filenames.

3. **Music Rename**  
   - Specifically targets **audio files** (commonly `.mp3`), reading **ID3 tags** (artist, title, tracknumber).
   - Renames them to a format such as:
     ```
     01. MainArtist - SongTitle (feat. OtherArtist).mp3
     ```
   - If no ID3 data exists, falls back to a simple cleanup of the original filename.
   - **Undo** reverts these renames as well.

4. **Keyword Rename**  
   - Searches for files (by a given extension) in the **source** folder that contain a specific keyword in their filename.
   - Replaces the keyword with a user-specified new string.
   - Works with any file type (e.g., renaming `"house md s01e12.mp4"` to `"MyHouse md s01e12.mp4"` when replacing `"house"` with `"MyHouse"`).
   - **Undo** reverts keyword-based renames.

5. **Logging**  
   - All operations (Preview, Run, Undo) are logged to a file (`operation_logs.txt`), which is overwritten each time an operation is performed.
   - This helps track exactly what changed, what was previewed, and any errors.

6. **Undo** (for all operations)  
   - Reverses file movements and/or renames.
   - Tries to restore original paths or filenames.
   - For sorted files, it also removes any newly created empty directories in the destination folder.

---

## New Features

- **Keyword Rename:**  
  A new renaming mode that enables users to search for a specific substring in filenames and replace it with a new string.  
  This feature provides flexibility in bulk editing filenames based on keywords.

- **Mass Rename Extension Flexibility:**  
  If no file extension is provided or if the extension is set to `"*"` (wildcard), the script now renames **all files** in the source folder while preserving their original extensions.

- **Two GUI Options:**  
  Users now have a choice between a classic Tkinter interface and a modern PyQt interface.  
  - **Tkinter GUI** is available in `gui_old.py`.
  - **PyQt GUI** is available in `gui_new.py`.

---

## Requirements

- **Python 3.7+** (recommended).
- **Mutagen** (optional, but needed for Music Rename to read ID3 tags). Install via:
  ```bash
  pip install mutagen
  ```
- **Tkinter** (included in most Python distributions by default):
  - On Linux, you may need to install `python3-tk`.
- **PyQt5** (for the modern GUI option). Install via:
  ```bash
  pip install PyQt5
  ```

---

## Installation

1. **Clone or Download** this project into a local directory:
   ```bash
   git clone https://github.com/K-P1/KP-File-Manager.git
   cd file_manager
   ```
2. (Optional) **Create & activate** a virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```
3. **Install Mutagen and PyQt5** if you plan to use Music Rename or the PyQt GUI:
   ```bash
   pip install mutagen PyQt5
   ```
4. You now have everything needed to run either GUI.

---

## Directory Structure

```
KP-File_Manager/
├── main.py           # CLI entry point (optional usage)
├── sorter.py         # Sorting logic
├── renamer.py        # Mass, Music & Keyword renaming logic
├── gui_old.py        # Tkinter GUI (old interface)
├── gui_new.py        # PyQt GUI (modern interface)
├── config.json       # Category definitions for sorting
├── utils.py          # Utility functions (optional placeholders)
└── README.md         # This documentation
```

---

## Usage (GUI)

**Launch** the GUI by running one of the following commands:

- **Tkinter GUI (Old Interface):**
  ```bash
  python gui_old.py
  ```

- **PyQt GUI (Modern Interface):**
  ```bash
  python gui_new.py
  ```

In the GUI window you will see the following sections:

1. **Operation Selection (Radio Buttons)**
   - **Sort Files**
   - **Mass Rename**
   - **Music Rename**
   - **Keyword Rename**  
   Only one can be selected at a time.

2. **Source Folder**
   - Click “Browse” to pick the folder containing the files to sort/rename.

3. **Destination Folder**
   - **Only enabled** when “Sort Files” is selected.
   - Click “Browse” to choose where sorted files should go.

4. **Mass Rename Fields**
   - **Only visible/enabled** when “Mass Rename” is selected.
   - **File Extension**: e.g., `mp4`, `jpg`.  
     *Leave blank or use `*` to rename all files in the folder.*
   - **Prefix**: A string to prepend to each new filename.
   - **Start Index**: The starting number for sequential numbering.
   - **Zero Padding**: How many digits to pad.
   - **Scramble** (checkbox): If checked, files receive **unique random** numbers.
   - **Scramble Multiplier (1..10)**: Determines the **max range** of random numbers.

5. **Music Rename**
   - **Only** requires a **Source Folder**.
   - Renames `.mp3` files using ID3 tags:
     - Format: `TrackNo. Artist - Title (feat. OtherArtists).mp3`
   - If metadata is missing, a basic cleanup of the filename is performed.

6. **Keyword Rename**
   - **Only visible/enabled** when “Keyword Rename” is selected.
   - Allows entering:
     - **File Extension**: Specify the file type (e.g., `mp4`) or leave it as needed.
     - **Keyword**: The substring to search for in filenames.
     - **New Keyword**: The string to replace the keyword with.

7. **Buttons**
   - **Preview**:
     - Runs a dry-run mode to show (via `operation_logs.txt`) which files would be processed, without making any changes.
   - **Run**:
     - Executes the chosen operation for real (sort / rename / music rename / keyword rename).
   - **Undo**:
     - Attempts to revert the last operation.
   - **Quit**:
     - Closes the GUI.

### Log File
After each operation (Preview, Run, or Undo), logs are written to **`operation_logs.txt`** located in the same directory. This file is overwritten with each new operation.

---

## Usage (CLI)

If you prefer command-line usage, you can run:
```bash
python main.py --help
```
This script supports arguments like `--sort`, `--mass-rename`, `--music-rename`, `--source`, `--dest`, `--dry-run`, `--undo`, etc.  
*(You’ll need to adapt the code in `main.py` if you want to incorporate scramble, keyword renaming, or advanced prefix/padding fields. The GUI is the simpler interface for that.)*

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
   - The application picks a **random unique** integer for each file in `[1..(file_count * scramble_multiplier)]`.
   - Example: With 5 `.mp4` files and **scramble_multiplier=3**, random numbers are taken from 1 to 15 (with no duplicates).  
   - Zero padding is applied as specified.

2. **Zero Padding**
   - A value of `3` produces filenames like `001, 002, 010`, etc.
   - A value of `0` produces numbers without padding.

3. **Undo Limitations**
   - The application stores original paths/names in memory, so undo works only within the same session.
   - Once the program is closed, the undo history is lost.

4. **Music Rename**
   - Requires **Mutagen** for ID3 tag reading. If you encounter import errors, install it via `pip install mutagen`.
   - Currently tested with `.mp3` files. To support other audio types, extend the metadata reading logic.

5. **Error Handling**
   - Errors (e.g., file conflicts or permission issues) are logged in the console and in `operation_logs.txt`.
   - For scramble mode, if the file count exceeds the scramble range, a warning is logged and a fallback is applied.

---

## Troubleshooting

1. **No “Tkinter”**:
   - On some minimal Linux distros, you might need to install it:
     ```bash
     sudo apt-get install python3-tk
     ```
2. **No “Mutagen” or Import Errors**:
   - Install via:
     ```bash
     pip install mutagen
     ```
3. **PyQt Issues**:
   - Ensure PyQt5 is installed:
     ```bash
     pip install PyQt5
     ```
4. **Zero Padding Doesn’t Show**:
   - Verify that you are entering a valid integer in the Zero Padding field and check `operation_logs.txt`.
5. **Scramble Multiplier Ignored**:
   - Ensure the value is between **1** and **10**.
6. **Undo Doesn’t Restore**:
   - Undo works only immediately after an operation. If files were manually changed after an operation, undo may fail.

---

## Contributing

1. Fork the repository and create a new branch.
2. Make your changes.
3. Submit a pull request with a detailed explanation of your work.

---

### Contact

For questions, suggestions, or to report a bug, feel free to open an **issue** or submit a **pull request**.