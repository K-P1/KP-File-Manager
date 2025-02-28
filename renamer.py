# renamer.py
import os
import logging
import random
from pathlib import Path
from typing import List, Tuple
import time

try:
    from mutagen.easyid3 import EasyID3
except ImportError:
    EasyID3 = None

class Renamer:
    """
    Provides:
      1) Mass file renaming (sequential or scramble).
      2) Music name simplification using ID3 tags.
      3) Keyword-based renaming (replace a substring in filenames).
      4) Undo functionality to revert renames.
    """

    def __init__(self):
        self._history: List[Tuple[Path, Path, Tuple[float, float]]] = []
        logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

    # --------------------------------------------------------------------------
    # 1) Mass File Renaming (with optional scramble)
    # --------------------------------------------------------------------------
    def mass_rename(
        self,
        folder: str,
        file_extension: str,
        prefix: str = "",
        start_index: int = 1,
        zero_padding: int = 0,
        preserve_timestamps: bool = False,
        dry_run: bool = False,
        scramble: bool = False,
        scramble_multiplier: int = 1,
    ):
        """
        Renames files in 'folder'. If a specific file_extension is provided (e.g. "mp4"),
        only files with that extension will be renamed. If file_extension is an empty string
        or "*", then all files in the folder will be renamed, preserving their original extensions
        in the new names.

        Args:
            folder (str): Target folder with files to rename.
            file_extension (str): e.g. "mp4", "jpg", "png", etc. Use "" or "*" to rename all files.
            prefix (str): e.g. "Renamed_"
            start_index (int): Starting number for sequential renaming (ignored if scramble=True).
            zero_padding (int): Number of digits to pad (e.g. 3 -> "007").
            preserve_timestamps (bool): If True, restore original a/mtime after rename.
            dry_run (bool): If True, log only without making changes.
            scramble (bool): If True, use random numbers instead of sequential.
            scramble_multiplier (int): 1..10, determines the max random range as (file_count * multiplier).
        """
        target_folder = Path(folder)
        if not target_folder.is_dir():
            logging.error(f"Folder does not exist or is not a directory: {folder}")
            return

        # Determine the list of files based on the provided extension.
        if not file_extension or file_extension.strip() == "*" or file_extension.strip() == "":
            files_to_rename = [f for f in target_folder.iterdir() if f.is_file()]
            use_original_ext = True
        else:
            file_extension = file_extension.lstrip('.').lower()
            files_to_rename = list(target_folder.glob(f"*.{file_extension}"))
            use_original_ext = False

        file_count = len(files_to_rename)
        if file_count == 0:
            logging.info("No matching files found in the folder.")
            return

        logging.info(
            f"Mass rename in '{folder}', "
            f"{'all files' if use_original_ext else 'extension=.' + file_extension}, prefix='{prefix}', "
            f"start_index={start_index}, zero_padding={zero_padding}, preserve_timestamps={preserve_timestamps}, "
            f"scramble={scramble}, scramble_multiplier={scramble_multiplier}, dry_run={dry_run}"
        )

        # Generate the numbering list:
        if scramble:
            upper_limit = file_count * scramble_multiplier
            if file_count > upper_limit:
                logging.warning("Scramble range is smaller than file count. Some duplicates may occur.")
                effective_limit = file_count
            else:
                effective_limit = upper_limit
            number_list = random.sample(range(1, effective_limit + 1), file_count)
        else:
            number_list = list(range(start_index, start_index + file_count))

        for old_path, num in zip(files_to_rename, number_list):
            num_str = str(num).zfill(zero_padding) if zero_padding > 0 else str(num)
            # Use provided file_extension if specified; otherwise, preserve the original extension.
            if use_original_ext:
                new_name = f"{prefix}{num_str}{old_path.suffix}"
            else:
                new_name = f"{prefix}{num_str}.{file_extension}"
            new_path = old_path.with_name(new_name)

            if dry_run:
                logging.info(f"[DRY RUN] {old_path.name} -> {new_name}")
            else:
                old_times = (0.0, 0.0)
                if preserve_timestamps:
                    stat_info = old_path.stat()
                    old_times = (stat_info.st_atime, stat_info.st_mtime)
                try:
                    old_path.rename(new_path)
                    logging.info(f"Renamed: {old_path.name} -> {new_name}")
                    if preserve_timestamps:
                        os.utime(new_path, (old_times[0], old_times[1]))
                    self._history.append((new_path, old_path, old_times))
                except Exception as e:
                    logging.error(f"Error renaming {old_path.name} to {new_name}: {e}")

        logging.info("Mass rename complete.")


    def _build_new_name(self, prefix: str, counter: int, zero_padding: int, extension: str) -> str:
        if zero_padding > 0:
            counter_str = str(counter).zfill(zero_padding)
        else:
            counter_str = str(counter)
        return f"{prefix}{counter_str}.{extension}"

    # --------------------------------------------------------------------------
    # 2) Music Name Simplification
    # --------------------------------------------------------------------------
    def rename_music(
        self,
        folder: str,
        extensions: List[str] = None,
        dry_run: bool = False,
        preserve_timestamps: bool = False
    ):
        """
        Simplifies music file names using ID3 tags.
        """
        if extensions is None:
            extensions = ["mp3"]

        target_folder = Path(folder)
        if not target_folder.is_dir():
            logging.error(f"Folder does not exist or is not a directory: {folder}")
            return

        music_files = []
        for ext in extensions:
            ext = ext.lstrip('.').lower()
            music_files.extend(target_folder.glob(f"*.{ext}"))

        if not music_files:
            logging.info(f"No matching music files found in {folder} for {extensions}.")
            return

        logging.info(
            f"Music rename in '{folder}', extensions={extensions}, dry_run={dry_run}, preserve_timestamps={preserve_timestamps}"
        )

        for old_path in music_files:
            old_times = (0.0, 0.0)
            if preserve_timestamps:
                stat_info = old_path.stat()
                old_times = (stat_info.st_atime, stat_info.st_mtime)

            new_name = None

            # ... Place your ID3 Tag extraction logic here ...

            if not new_name:
                new_stem = self._cleanup_filename(old_path.stem)
                new_name = f"{new_stem}{old_path.suffix}"

            new_path = old_path.with_name(new_name)
            if dry_run:
                logging.info(f"[DRY RUN] {old_path.name} -> {new_name}")
                continue

            try:
                old_path.rename(new_path)
                logging.info(f"Renamed music: {old_path.name} -> {new_name}")
                if preserve_timestamps:
                    os.utime(new_path, (old_times[0], old_times[1]))
                self._history.append((new_path, old_path, old_times))
            except Exception as e:
                logging.error(f"Error renaming {old_path.name} to {new_name}: {e}")

        logging.info("Music name simplification complete.")

    def _cleanup_filename(self, original_stem: str) -> str:
        name = original_stem.replace('_', ' ')
        name = ' '.join(name.split())
        return name

    # --------------------------------------------------------------------------
    # 3) Keyword-based Renaming
    # --------------------------------------------------------------------------
    def rename_by_keyword(
        self,
        folder: str,
        file_extension: str,
        keyword: str,
        new_keyword: str,
        preserve_timestamps: bool = False,
        dry_run: bool = False
    ):
        """
        Searches for files with the given extension in 'folder' whose names contain
        'keyword' and replaces the keyword with 'new_keyword'.

        Args:
            folder (str): Target folder with files.
            file_extension (str): e.g. "mp4"
            keyword (str): Substring to search for in the file name (without extension).
            new_keyword (str): Replacement substring.
            preserve_timestamps (bool): If True, original timestamps are preserved.
            dry_run (bool): If True, no changes are made; only logs the planned changes.
        """
        target_folder = Path(folder)
        if not target_folder.is_dir():
            logging.error(f"Folder does not exist or is not a directory: {folder}")
            return

        file_extension = file_extension.lstrip('.').lower()
        files_to_rename = list(target_folder.glob(f"*.{file_extension}"))
        if not files_to_rename:
            logging.info(f"No '.{file_extension}' files found in {folder}.")
            return

        logging.info(
            f"Keyword rename in '{folder}', extension='.{file_extension}', "
            f"keyword='{keyword}', new_keyword='{new_keyword}', "
            f"preserve_timestamps={preserve_timestamps}, dry_run={dry_run}"
        )

        for old_path in files_to_rename:
            if keyword not in old_path.stem:
                continue  # Skip files that do not include the keyword

            new_stem = old_path.stem.replace(keyword, new_keyword)
            new_name = f"{new_stem}{old_path.suffix}"
            new_path = old_path.with_name(new_name)

            if dry_run:
                logging.info(f"[DRY RUN] {old_path.name} -> {new_name}")
            else:
                old_times = (0.0, 0.0)
                if preserve_timestamps:
                    stat_info = old_path.stat()
                    old_times = (stat_info.st_atime, stat_info.st_mtime)
                try:
                    old_path.rename(new_path)
                    logging.info(f"Renamed: {old_path.name} -> {new_name}")
                    if preserve_timestamps:
                        os.utime(new_path, (old_times[0], old_times[1]))
                    self._history.append((new_path, old_path, old_times))
                except Exception as e:
                    logging.error(f"Error renaming {old_path.name} to {new_name}: {e}")

        logging.info("Keyword-based renaming complete.")

    # --------------------------------------------------------------------------
    # 4) Undo
    # --------------------------------------------------------------------------
    def undo(self):
        if not self._history:
            logging.info("Nothing to undo for renamer.")
            return

        logging.info("Initiating undo operation for renames...")
        while self._history:
            current_path, old_path, old_times = self._history.pop()
            if current_path.exists():
                try:
                    old_path.parent.mkdir(parents=True, exist_ok=True)
                    current_path.rename(old_path)
                    logging.info(f"Undo rename: {current_path.name} -> {old_path.name}")
                    if old_times != (0.0, 0.0):
                        os.utime(old_path, (old_times[0], old_times[1]))
                except Exception as e:
                    logging.error(f"Error undoing rename {current_path} -> {old_path}: {e}")
            else:
                logging.warning(f"File missing: {current_path}, cannot undo.")

        logging.info("Undo operation finished.")
