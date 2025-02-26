import os
import shutil
import threading
from pathlib import Path
import json
import logging
from typing import Dict, List, Tuple, Optional

# If you want to integrate caching or advanced logging from utils, 
# uncomment the following and implement those methods in utils.py
# from utils import cache_result, log_action

class FileSorter:
    """
    Handles the logic for sorting files (and folders) based on user-defined or
    default classification rules. Includes dry-run previews and undo functionality.
    """

    def __init__(self, config_path: str = 'config.json'):
        """
        Initialize the FileSorter with an optional config file for user-defined rules.
        """
        self.config_path = config_path
        self.sort_rules = self._load_config(config_path)  # Default or user-defined
        # This list will store move operations for possible undo actions:
        self._history: List[Tuple[Path, Path]] = []
        # Create a lock for threading operations if needed:
        self._lock = threading.Lock()

    def _load_config(self, config_path: str) -> Dict[str, List[str]]:
        """
        Load the sorting config from a JSON file.
        The config could map categories to lists of file extensions.
        Example config.json:
            {
                "Images": ["jpg", "jpeg", "png"],
                "Documents": ["pdf", "doc", "docx"],
                ...
            }
        """
        if not os.path.exists(config_path):
            logging.warning(f"Config file {config_path} not found. Using default rules.")
            return {
                "Images": ["jpg", "jpeg", "png", "gif"],
                "Documents": ["pdf", "doc", "docx", "txt", "xls", "xlsx"],
                "Audio": ["mp3", "wav", "ogg"],
                "Video": ["mp4", "mov", "avi"],
                "Archives": ["zip", "rar", "7z"],
            }
        else:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)
            return user_config

    def _classify_file(self, file_path: Path) -> str:
        """
        Determine the file category based on its extension and the loaded config.
        If no match is found, return 'Others' as default.
        """
        ext = file_path.suffix.lower().lstrip('.')
        for category, exts in self.sort_rules.items():
            if ext in exts:
                return category
        return "Others"

    def _classify_folder(self, folder_path: Path) -> Optional[str]:
        """
        Classify a folder by checking the most relevant file type inside it.
        Returns a category name or None if the folder is empty/unclassifiable.
        """
        file_extensions_count = {}
        for root, _, files in os.walk(folder_path):
            for file in files:
                ext = Path(file).suffix.lower().lstrip('.')
                file_extensions_count[ext] = file_extensions_count.get(ext, 0) + 1

        if not file_extensions_count:
            # Empty folder
            return None

        # Find the extension with the maximum count
        most_common_ext = max(file_extensions_count, key=file_extensions_count.get)
        # Map it to one of the configured categories
        for category, exts in self.sort_rules.items():
            if most_common_ext in exts:
                return category
        # If we can't match the extension, default to "Others"
        return "Others"

    def _scan_directory(self, source_path: Path) -> List[Path]:
        """
        Recursively scan the source directory for files and sub-folders.
        Returns a list of all items (files and folders).
        """
        items_to_sort = []
        for root, dirs, files in os.walk(source_path):
            # Collect file paths
            for file in files:
                file_path = Path(root) / file
                items_to_sort.append(file_path)
            # Collect subfolder paths
            for d in dirs:
                folder_path = Path(root) / d
                items_to_sort.append(folder_path)
        return items_to_sort

    def _sort_item(self, item_path: Path, base_dest: Path, dry_run: bool = False):
        """
        Sort a single file or folder into the appropriate category.
        Moves entire folders if applicable.
        """
        if item_path.is_dir():
            # Classify folder
            category = self._classify_folder(item_path)
            if category is None:
                category = "EmptyFolders"
            dest_folder = base_dest / category
            target_path = dest_folder / item_path.name
        else:
            # Classify file
            category = self._classify_file(item_path)
            dest_folder = base_dest / category
            target_path = dest_folder / item_path.name

        if not dry_run:
            dest_folder.mkdir(parents=True, exist_ok=True)

        # Handle duplicates (simple approach: rename if conflict)
        final_path = target_path
        counter = 1
        while not dry_run and final_path.exists():
            final_path = target_path.parent / f"{target_path.stem}_{counter}{target_path.suffix}"
            counter += 1

        if dry_run:
            logging.info(f"[DRY RUN] {item_path} -> {final_path}")
        else:
            try:
                shutil.move(str(item_path), str(final_path))
                self._history.append((final_path, item_path))
                logging.info(f"Moved: {item_path} -> {final_path}")
            except Exception as e:
                logging.error(f"Error moving {item_path} to {final_path}: {e}")

    def sort_directory(self, source_path: str, destination_path: str, dry_run: bool = False):
        """
        Public method to sort files and folders from the source to the destination.
        Supports a dry-run mode for previewing. Utilizes multithreading for efficiency.
        """
        source = Path(source_path)
        dest = Path(destination_path)

        if not source.exists():
            logging.error(f"Source path does not exist: {source_path}")
            return

        logging.info(f"Sorting from {source} to {dest} (dry_run={dry_run})")

        items_to_sort = self._scan_directory(source)
        threads = []
        for item in items_to_sort:
            t = threading.Thread(target=self._sort_item, args=(item, dest, dry_run))
            threads.append(t)
            t.start()

        for t in threads:
            t.join()

        logging.info("Sorting complete.")

    def undo(self):
        """
        Undo the most recent sorting operation by moving items back
        to their original locations. Looks up the stored move history.
        """
        logging.info("Initiating undo operation for sort...")
        while self._history:
            moved_path, original_path = self._history.pop()
            if moved_path.exists():
                try:
                    original_path.parent.mkdir(parents=True, exist_ok=True)
                    shutil.move(str(moved_path), str(original_path))
                    logging.info(f"Undo move: {moved_path} -> {original_path}")
                except Exception as e:
                    logging.error(f"Error undoing move {moved_path} -> {original_path}: {e}")
            else:
                logging.warning(f"File/folder missing: {moved_path}, cannot undo.")
        logging.info("Undo operation finished.")
