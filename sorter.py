import os
import shutil
import threading
import re
import psutil
from pathlib import Path
from typing import Dict, List, Tuple, Optional
import json
import logging
import time
from concurrent.futures import ThreadPoolExecutor

class FileSorter:
    def __init__(self, config_path: str = 'config.json'):
        self.config_path = config_path
        self.sort_rules = self._load_config(config_path)
        self._history: List[Tuple[Path, Path]] = []
        self._lock = threading.Lock()
        self._grouped_folders: set = set()
        self.series_mode: bool = False

    def _load_config(self, config_path: str) -> Dict[str, List[str]]:
        if not os.path.exists(config_path):
            logging.warning(f"Config file {config_path} not found. Using default rules.")
            return {
                "Images": ["jpg", "jpeg", "png", "gif"],
                "Documents": ["pdf", "doc", "docx", "txt", "xls", "xlsx"],
                "Audio": ["mp3", "wav", "ogg"],
                "Video": ["mp4", "mov", "avi", "mkv"],
                "Archives": ["zip", "rar", "7z"],
            }
        else:
            with open(config_path, 'r', encoding='utf-8') as f:
                return json.load(f)

    def _has_enough_space(self, path: Path, required_bytes: int) -> bool:
        usage = psutil.disk_usage(path.drive)
        return usage.free >= required_bytes

    def _extract_title(self, name: str) -> str:
        name = Path(name).stem.lower()
        name = re.sub(r"[\[\(\{].*?[\]\)\}]", "", name)
        name = re.sub(r"[\[\(\{].*", "", name)
        name = re.sub(r"[.\\/_-]+", " ", name)
        name = re.sub(r"\s+", " ", name).strip()

        blacklist_tokens = {
            "season", "episode", "ep", "e", "s", "part", "complete", "final", "finale",
            "brrip", "webrip", "hdrip", "bluray", "x264", "x265", "1080p", "720p", "galaxytv",
            "sujaidr", "xvid", "afg", "web", "rip", "brip", "dvdrip", "cam", "hd", "uhd",
            "yify", "hdtv", "memento", "aac", "hevc", "ac3", "amzn", "dl", "subs", "internal",
            "10bit", "5.1", "esubs", "mp4", "mkv", "tgx", "nf"
        }

        tokens = name.split()
        clean_tokens = []
        for token in tokens:
            if token.isdigit():
                continue
            if re.fullmatch(r"(19|20)\d{2}", token):
                continue
            if re.fullmatch(r"s\d{1,2}e\d{1,2}", token, re.IGNORECASE):
                continue
            if re.fullmatch(r"[se]\d{1,2}", token, re.IGNORECASE):
                continue
            if re.fullmatch(r"season\s*\d{1,2}", token, re.IGNORECASE):
                continue
            if token.lower() in blacklist_tokens:
                continue
            clean_tokens.append(token)

        title = " ".join(clean_tokens).strip()
        title = re.sub(r"^[\-_.\s]+|[\-_.\s]+$", "", title)
        return title.title()

    def _group_similar_items(self, items: List[Path], base_dest: Path, dry_run: bool = False, group_files: bool = True) -> List[Path]:
        groups: Dict[str, List[Path]] = {}
        for item in items:
            if item.is_file() and not group_files:
                continue
            title = self._extract_title(item.name)
            if not title:
                continue
            groups.setdefault(title, []).append(item)

        moved = []
        for title, group_items in groups.items():
            if len(group_items) <= 1:
                continue
            group_folder = base_dest / title
            if not dry_run:
                group_folder.mkdir(parents=True, exist_ok=True)
            else:
                logging.info(f"[DRY RUN] Would create group folder: {group_folder}")
            for item in group_items:
                target_path = group_folder / item.name
                counter = 1
                while not dry_run and target_path.exists():
                    target_path = group_folder / f"{item.stem}_{counter}{item.suffix}"
                    counter += 1
                try:
                    with self._lock:
                        if not dry_run:
                            if item.drive == target_path.drive:
                                os.rename(str(item), str(target_path))
                            else:
                                shutil.move(str(item), str(target_path))
                            self._history.append((target_path, item))
                        else:
                            logging.info(f"[DRY RUN] Would move {item} -> {target_path}")
                        self._grouped_folders.add(item)
                    moved.append(item)
                except Exception as e:
                    logging.error(f"Error moving {item} -> {target_path}: {e}")
        return moved

    def _scan_directory(self, source_path: Path) -> List[Path]:
        return [source_path / p for p in os.listdir(source_path)]

    def _classify_file(self, file_path: Path) -> str:
        ext = file_path.suffix.lower().lstrip('.')
        for category, exts in self.sort_rules.items():
            if ext in exts:
                return category
        return "Others"

    def _classify_folder(self, folder_path: Path) -> Optional[str]:
        file_extensions_count = {}
        for i, file in enumerate(folder_path.rglob("*")):
            if file.is_file():
                ext = file.suffix.lower().lstrip('.')
                file_extensions_count[ext] = file_extensions_count.get(ext, 0) + 1
            if i > 50:
                break
        if not file_extensions_count:
            return None
        most_common_ext = max(file_extensions_count, key=file_extensions_count.get)  # type: ignore
        for category, exts in self.sort_rules.items():
            if most_common_ext in exts:
                return category
        return "Others"

    def _sort_item(self, item_path: Path, base_dest: Path, dry_run: bool = False):
        if item_path in self._grouped_folders:
            return
        if self.series_mode:
            return

        if item_path.is_dir():
            category = self._classify_folder(item_path)
            if category is None:
                category = "EmptyFolders"
            dest_folder = base_dest / category
            target_path = dest_folder / item_path.name
        else:
            category = self._classify_file(item_path)
            dest_folder = base_dest / category
            target_path = dest_folder / item_path.name

        if not dry_run:
            dest_folder.mkdir(parents=True, exist_ok=True)

        final_path = target_path
        counter = 1
        while not dry_run and final_path.exists():
            final_path = target_path.parent / f"{target_path.stem}_{counter}{target_path.suffix}"
            counter += 1

        try:
            if not dry_run:
                if item_path.drive == final_path.drive:
                    os.rename(str(item_path), str(final_path))
                else:
                    shutil.move(str(item_path), str(final_path))
                with self._lock:
                    self._history.append((final_path, item_path))
            else:
                logging.info(f"[DRY RUN] Would move {item_path} -> {final_path}")
        except Exception as e:
            logging.error(f"Error moving {item_path} to {final_path}: {e}")

    def sort_directory(self, source_path: str, destination_path: str, dry_run: bool = False, series_mode: bool = False):
        source = Path(source_path)
        dest = Path(destination_path)
        if not source.exists():
            logging.error(f"Source path does not exist: {source_path}")
            return

        self.series_mode = series_mode

        logging.info(f"Sorting from {source} to {dest} (dry_run={dry_run})")
        t0 = time.time()
        all_items = self._scan_directory(source)

        grouped_items = self._group_similar_items(all_items, dest, dry_run, group_files=True)
        all_items = self._scan_directory(source)
        remaining_items = [i for i in all_items if i not in grouped_items]

        with ThreadPoolExecutor(max_workers=10) as executor:
            futures = [executor.submit(self._sort_item, item, dest, dry_run) for item in remaining_items]
            for future in futures:
                future.result()

        logging.info(f"Sorting complete in {time.time() - t0:.2f} seconds.")

    def undo(self):
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
