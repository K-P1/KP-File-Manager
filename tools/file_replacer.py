#!/usr/bin/env python3
# Path: tools/file_replacer.py
# Purpose: Safely install manifest-defined replacement packages from a folder or ZIP.
# Last changed: 2026-09-28, require self-contained package manifests for every replacement and deletion.
"""Preview or install self-contained replacement packages without guessing destinations.

Place this script at <repository>/tools/file_replacer.py (Python 3.11+; stdlib only).
The default input is tools/replacement_downloads/, or the current directory if
launched from inside a tools/ subfolder. Pass --source to choose another input
folder or a ZIP. The default downloads folder can contain individual package
folders OR one ZIP; mixed inputs require an explicit --source.

    python tools/file_replacer.py
    python tools/file_replacer.py --source tools/replacement_downloads/phase.zip
    python tools/file_replacer.py --apply
    python tools/file_replacer.py --source . --apply  # from a tools/ downloads subfolder

Every package must contain a root replacement_plan.tsv manifest. It is excluded
from normal replacement discovery and is the only authority for all package
source paths, repository destinations, and deletions. Entries may be tab- or
pipe-separated (source names are exact paths inside the folder or ZIP):

    AUTO   | NewService.kt | shared/src/commonMain/.../NewService.kt
    MODIFY | OldFile.kt    | shared/src/commonMain/.../OldFile.kt
    CREATE | NewFile.kt    | shared/src/commonMain/.../NewFile.kt
    DELETE | -             | shared/src/commonMain/.../ObsoleteFile.kt

AUTO infers CREATE versus MODIFY from the destination's existence. A two-column
'SOURCE | DESTINATION' row is equivalent to AUTO. Every non-manifest package
file must have exactly one manifest row; source basenames, file headers, and
repository destinations are never guessed. DELETE requires --apply
--allow-delete.

Every invocation previews by default. --apply validates the entire set and
backs up BOTH original files and incoming replacements in a unique separate
folder under tools/replacement_backups/, plus a JSON change report. Created
files are recorded in that report; a failed apply attempts rollback, restoring
originals and removing files created by the failed run. A successful --apply
cleans the managed source after its backups are complete: using the default
tools/replacement_downloads/ source clears that directory, while an explicit
file, ZIP, or subfolder inside it removes only that selected source. Previews,
failed runs, and sources outside the default downloads folder remain intact.
A process kill, disk error or concurrent repository modifications may require
manual restoration. Inspect downloaded code before applying; no secrets scan
can establish that arbitrary code is safe.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import sys
import tempfile
import uuid
import zipfile

KIB = 1024
MIB = 1024 * KIB
MAX_SOURCE_FILE = 2 * MIB
MAX_SOURCE_TOTAL = 20 * MIB
MAX_ZIP_MEMBERS = 1500
MAX_EXISTING_FILE = 20 * MIB
MAX_BACKUP_TOTAL = 50 * MIB
DEFAULT_DOWNLOADS = "replacement_downloads"
DEFAULT_BACKUPS = "replacement_backups"
PACKAGE_PLAN = "replacement_plan.tsv"

WINDOWS_RESERVED = re.compile(r"^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$", re.I)
UNSAFE_PARTS = frozenset({".git", ".hg", ".svn", ".ssh", "node_modules", "__pycache__", "build", "dist", "target", "venv", ".venv"})
PRIVATE_NAME = re.compile(r"^(?:\.env(?:\..*)?|id_rsa(?:\..*)?|id_ed25519(?:\..*)?|credentials\.json|secrets\.json|local\.properties)$", re.I)
PRIVATE_SUFFIXES = frozenset({".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".kdbx"})


class ReplacementError(Exception):
    """A planning, safety, or application error that does not require a traceback."""


@dataclass(frozen=True)
class Entry:
    action: str
    source: str | None
    destination: str


@dataclass(frozen=True)
class Prepared:
    entry: Entry
    content: bytes | None
    source_sha256: str | None
    previous_sha256: str | None
    previous_size: int


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize_path(text: str, label: str) -> str:
    """Validate a repository-relative path before joining it to a trusted root."""
    value = text.strip().replace("\\", "/")
    if not value or value.startswith("/") or value.startswith("//"):
        raise ReplacementError(f"{label}: expected a nonempty relative path: {text!r}")
    if re.match(r"^[A-Za-z]:", value) or "\x00" in value:
        raise ReplacementError(f"{label}: drive/absolute/NUL paths are forbidden: {text!r}")
    parts = value.split("/")
    if any(not part or part in (".", "..") for part in parts):
        raise ReplacementError(f"{label}: empty, dot or parent components are forbidden: {text!r}")
    if any(part.endswith((".", " ")) or WINDOWS_RESERVED.match(part) for part in parts):
        raise ReplacementError(f"{label}: invalid Windows path component: {text!r}")
    if any(any(ord(char) < 32 or char in ':*?"<>|' for char in part) for part in parts):
        raise ReplacementError(f"{label}: invalid path characters: {text!r}")
    return "/".join(parts)


def safe_existing_path(path: Path, boundary: Path, label: str) -> None:
    """Reject an escape or symlink component, even when the last path does not exist."""
    try:
        relative = path.relative_to(boundary)
    except ValueError as exc:
        raise ReplacementError(f"{label}: path escapes its allowed directory: {path}") from exc
    if boundary.is_symlink():
        raise ReplacementError(f"{label}: symlinked root is forbidden: {boundary}")
    current = boundary
    for part in relative.parts:
        current = current / part
        if current.is_symlink():
            raise ReplacementError(f"{label}: symlink component is forbidden: {current}")


def reject_sensitive(path: str, label: str) -> None:
    for part in PurePosixPath(path).parts:
        if part.lower() in UNSAFE_PARTS or part.startswith("."):
            raise ReplacementError(f"{label}: hidden/protected directory or file is forbidden: {path}")
        if PRIVATE_NAME.fullmatch(part) or Path(part).suffix.lower() in PRIVATE_SUFFIXES:
            raise ReplacementError(f"{label}: credential-like filename is forbidden: {path}")


def read_bounded_file(path: Path, boundary: Path, label: str, limit: int) -> bytes:
    safe_existing_path(path, boundary, label)
    if not path.is_file():
        raise ReplacementError(f"{label}: not a regular file: {path}")
    if path.stat().st_size > limit:
        raise ReplacementError(f"{label}: exceeds {limit:,} bytes: {path}")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ReplacementError(f"{label}: grew beyond {limit:,} bytes: {path}")
    return data


class DownloadSource:
    """Enumerate ALL downloaded files, with ZIP and directory bounds enforced."""

    def __init__(self, location: Path, tools: Path):
        safe_existing_path(location, tools, "Source")
        if location == tools or location == tools / DEFAULT_BACKUPS or tools / DEFAULT_BACKUPS in location.parents:
            raise ReplacementError("Source must be a downloads folder/file beneath tools/, not tools/ or the backups folder")
        self.location = location
        self.tools = tools
        self.archive: zipfile.ZipFile | None = None
        self.members: dict[str, zipfile.ZipInfo | Path] = {}
        try:
            if location.is_file() and location.suffix.lower() == ".zip":
                self._load_zip(location)
            elif location.is_file():
                raise ReplacementError("Source must be a package folder or ZIP containing replacement_plan.tsv")
            elif location.is_dir():
                self._load_directory(location)
            else:
                raise ReplacementError(f"Source must be a folder, file or ZIP: {location}")
            if not self.members:
                raise ReplacementError(f"No replacement files found in {location}")
            self._check_members()
        except BaseException:
            self.close()
            raise

    def _load_zip(self, location: Path) -> None:
        if location.stat().st_size > MAX_SOURCE_TOTAL:
            raise ReplacementError("Compressed ZIP exceeds 20 MiB")
        self.archive = zipfile.ZipFile(location)
        infos = self.archive.infolist()
        if len(infos) > MAX_ZIP_MEMBERS:
            raise ReplacementError(f"ZIP contains more than {MAX_ZIP_MEMBERS} entries")
        seen: set[str] = set()
        total = 0
        for info in infos:
            name = normalize_path(info.filename.rstrip("/") if info.is_dir() else info.filename, "ZIP entry")
            reject_sensitive(name, "ZIP entry")
            mode = (info.external_attr >> 16) & 0xFFFF
            kind = stat.S_IFMT(mode)
            if kind not in (0, stat.S_IFDIR if info.is_dir() else stat.S_IFREG):
                raise ReplacementError(f"ZIP contains symlink or special entry: {name}")
            if info.flag_bits & 1:
                raise ReplacementError(f"ZIP contains encrypted entry: {name}")
            if name.casefold() in seen:
                raise ReplacementError(f"ZIP contains duplicate or case-colliding entry: {name}")
            seen.add(name.casefold())
            if info.is_dir():
                continue
            if info.file_size > MAX_SOURCE_FILE:
                raise ReplacementError(f"ZIP entry exceeds 2 MiB: {name}")
            total += info.file_size
            if total > MAX_SOURCE_TOTAL:
                raise ReplacementError("Uncompressed ZIP exceeds 20 MiB")
            self.members[name] = info

    def _load_directory(self, location: Path) -> None:
        if location == self.tools / DEFAULT_DOWNLOADS:
            location.mkdir(exist_ok=True)
        paths: list[Path] = []
        for base, directories, files in os.walk(location, followlinks=False):
            parent = Path(base)
            for dirname in directories:
                child = parent / dirname
                safe_existing_path(child, location, "Download directory")
            for filename in files:
                child = parent / filename
                safe_existing_path(child, location, "Download file")
                relative = normalize_path(child.relative_to(location).as_posix(), "Download filename")
                reject_sensitive(relative, "Download filename")
                paths.append(child)
                if len(paths) > MAX_ZIP_MEMBERS:
                    raise ReplacementError(f"Download folder contains over {MAX_ZIP_MEMBERS} files")
        archives = [path for path in paths if path.suffix.lower() == ".zip"]
        if archives:
            if len(paths) != 1:
                raise ReplacementError("Folder contains ZIP(s) and other files. Use --source with one ZIP or a folder of loose files")
            self._load_zip(archives[0])
            return
        for path in paths:
            self.members[path.relative_to(location).as_posix()] = path

    def _check_members(self) -> None:
        total = 0
        seen: set[str] = set()
        for name, item in self.members.items():
            if name.casefold() in seen:
                raise ReplacementError(f"Duplicate or case-colliding source path: {name}")
            seen.add(name.casefold())
            if isinstance(item, Path):
                safe_existing_path(item, self.tools, "Download")
                size = item.stat().st_size
                if size > MAX_SOURCE_FILE:
                    raise ReplacementError(f"Downloaded file exceeds 2 MiB: {name}")
                total += size
            else:
                total += item.file_size
            if total > MAX_SOURCE_TOTAL:
                raise ReplacementError("Source files exceed 20 MiB total")

    def get(self, source: str) -> bytes:
        item = self.members.get(source)
        if item is None:
            raise ReplacementError(f"Missing download: {source}")
        if isinstance(item, Path):
            return read_bounded_file(item, self.tools, f"Download {source}", MAX_SOURCE_FILE)
        assert self.archive is not None
        try:
            with self.archive.open(item) as stream:
                data = stream.read(MAX_SOURCE_FILE + 1)
        except (RuntimeError, OSError, zipfile.BadZipFile) as exc:
            raise ReplacementError(f"Cannot read ZIP entry {source}: {exc}") from exc
        if len(data) != item.file_size or len(data) > MAX_SOURCE_FILE:
            raise ReplacementError(f"ZIP entry size mismatch/limit exceeded: {source}")
        return data

    def close(self) -> None:
        if self.archive is not None:
            self.archive.close()
            self.archive = None

    def __enter__(self) -> DownloadSource:
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()


def parse_plan(content: bytes, label: str) -> list[Entry]:
    """Parse the required self-contained replacement package manifest."""
    if len(content) > 256 * KIB:
        raise ReplacementError(f"Replacement plan exceeds 256 KiB: {label}")
    try:
        lines = content.decode("utf-8-sig").splitlines()
    except UnicodeError as exc:
        raise ReplacementError(f"Plan is not UTF-8: {label}") from exc
    entries: list[Entry] = []
    for index, line in enumerate(lines, 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        fields = [piece.strip() for piece in line.split("\t" if "\t" in line else "|")]
        if len(fields) == 2:
            action = "AUTO"
            raw_source, raw_destination = fields
        elif len(fields) == 3:
            action, raw_source, raw_destination = fields
            action = action.upper()
        else:
            raise ReplacementError(
                f"Plan {label} line {index}: expected SOURCE | DESTINATION or ACTION | SOURCE | DESTINATION"
            )
        if action not in ("AUTO", "MODIFY", "CREATE", "DELETE"):
            raise ReplacementError(f"Plan {label} line {index}: unsupported action {action!r}")
        destination = normalize_path(raw_destination, f"plan {label} line {index} destination")
        reject_sensitive(destination, f"plan {label} line {index} destination")
        if action == "DELETE":
            if raw_source not in ("-", ""):
                raise ReplacementError(f"Plan {label} line {index}: DELETE source must be '-'")
            source = None
        else:
            source = normalize_path(raw_source, f"plan {label} line {index} source")
            reject_sensitive(source, f"plan {label} line {index} source")
        entries.append(Entry(action, source, destination))
    if len(entries) > MAX_ZIP_MEMBERS:
        raise ReplacementError(f"Replacement plan has too many entries: {label}")
    return entries


def read_package_plan(source: DownloadSource) -> list[Entry]:
    """Read the mandatory root package manifest."""
    if PACKAGE_PLAN not in source.members:
        raise ReplacementError(f"Package must contain a root {PACKAGE_PLAN} manifest")
    return parse_plan(source.get(PACKAGE_PLAN), f"package {PACKAGE_PLAN}")


def resolve_entries(source: DownloadSource, plan_entries: list[Entry], repo: Path) -> list[Entry]:
    """Resolve every package file from its exact, manifest-defined source path."""
    names = set(source.members) - {PACKAGE_PLAN}
    by_name: dict[str, Entry] = {}
    deletions: list[Entry] = []
    for mapped in plan_entries:
        if mapped.source is None:
            deletions.append(mapped)
            continue
        if mapped.source == PACKAGE_PLAN:
            raise ReplacementError(f"Package manifest cannot install itself: {PACKAGE_PLAN}")
        if mapped.source not in names:
            raise ReplacementError(f"Manifest source path not found in package: {mapped.source}")
        name = mapped.source
        if name in by_name:
            raise ReplacementError(f"Source mapped twice in package manifest: {name}")
        by_name[name] = Entry(mapped.action, name, mapped.destination)
    unmapped = sorted(names - set(by_name))
    if unmapped:
        lines = "\n".join(f"  - {name}" for name in unmapped)
        raise ReplacementError(
            f"Every package file must have a manifest row; missing {len(unmapped)}:\n{lines}"
        )
    results: list[Entry] = []
    destinations: set[str] = set()
    for name in sorted(names):
        mapped = by_name[name]
        destination = mapped.destination
        reject_sensitive(destination, f"Destination for {name}")
        action = mapped.action
        if action == "AUTO":
            target = repo.joinpath(*destination.split("/"))
            action = "MODIFY" if target.exists() or target.is_symlink() else "CREATE"
        if destination.casefold() in destinations:
            raise ReplacementError(f"Multiple downloads target the same destination: {destination}")
        destinations.add(destination.casefold())
        results.append(Entry(action, name, destination))
    for item in deletions:
        if item.destination.casefold() in destinations:
            raise ReplacementError(f"DELETE collides with another destination: {item.destination}")
        destinations.add(item.destination.casefold())
        results.append(item)
    return results


def prepare(entries: list[Entry], source: DownloadSource, repo: Path, tools: Path) -> list[Prepared]:
    result: list[Prepared] = []
    total_source = 0
    total_backup = 0
    protected = [tools / DEFAULT_DOWNLOADS, tools / DEFAULT_BACKUPS, source.location, Path(__file__).absolute()]
    for entry in entries:
        target = repo.joinpath(*entry.destination.split("/"))
        safe_existing_path(target, repo, "Destination")
        if target in (repo, tools):
            raise ReplacementError(f"Cannot replace repository/tool root: {entry.destination}")
        for item in protected:
            if target == item or (item.is_dir() and item in target.parents):
                raise ReplacementError(f"Destination overlaps tools inputs/outputs: {entry.destination}")
        exists = target.exists()
        if entry.action == "CREATE" and exists:
            raise ReplacementError(f"CREATE destination already exists: {entry.destination}")
        if entry.action in ("MODIFY", "DELETE") and not exists:
            raise ReplacementError(f"{entry.action} destination does not exist: {entry.destination}")
        if exists and not target.is_file():
            raise ReplacementError(f"Destination is not a regular file: {entry.destination}")
        current_hash = None
        current_size = 0
        if exists:
            current = read_bounded_file(target, repo, "Existing destination", MAX_EXISTING_FILE)
            current_size = len(current)
            total_backup += current_size
            if total_backup > MAX_BACKUP_TOTAL:
                raise ReplacementError("Existing files exceed the 50 MiB backup limit")
            current_hash = sha256(current)
        content = None
        new_hash = None
        if entry.source is not None:
            content = source.get(entry.source)
            total_source += len(content)
            if total_source > MAX_SOURCE_TOTAL:
                raise ReplacementError("Selected source content exceeds 20 MiB")
            new_hash = sha256(content)
        result.append(Prepared(entry, content, new_hash, current_hash, current_size))
    return result


def atomic_write(target: Path, data: bytes) -> None:
    """Install bytes using a same-directory atomic rename, preserving previous mode."""
    tmp: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(prefix=".replacement-", dir=target.parent, delete=False) as stream:
            tmp = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        if target.is_file():
            shutil.copymode(target, tmp)
        os.replace(tmp, target)
        tmp = None
    finally:
        if tmp is not None:
            tmp.unlink(missing_ok=True)


def assert_targets_unchanged(prepared: list[Prepared], repo: Path) -> None:
    for item in prepared:
        target = repo.joinpath(*item.entry.destination.split("/"))
        safe_existing_path(target, repo, "Destination")
        if item.previous_sha256 is None:
            if target.exists():
                raise ReplacementError(f"CREATE destination appeared after preview: {item.entry.destination}")
        else:
            if not target.is_file():
                raise ReplacementError(f"Existing destination disappeared: {item.entry.destination}")
            current = read_bounded_file(target, repo, "Existing destination", MAX_EXISTING_FILE)
            if sha256(current) != item.previous_sha256:
                raise ReplacementError(f"Destination changed since preview: {item.entry.destination}")


def apply(prepared: list[Prepared], repo: Path, tools: Path, allow_delete: bool) -> Path | None:
    if any(item.entry.action == "DELETE" for item in prepared) and not allow_delete:
        raise ReplacementError("DELETE requires --apply --allow-delete")
    changed = [item for item in prepared if item.entry.action == "DELETE" or item.source_sha256 != item.previous_sha256]
    if not changed:
        print("All downloaded files already match the repository. No changes needed.")
        return None
    backups = tools / DEFAULT_BACKUPS
    safe_existing_path(backups, tools, "Backup folder")
    backups.mkdir(exist_ok=True)
    if not backups.is_dir():
        raise ReplacementError(f"Backup location is not a directory: {backups}")
    folder = backups / (datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S_") + uuid.uuid4().hex[:8])
    folder.mkdir()
    record = {
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "repository": str(repo),
        "status": "backup_prepared",
        "entries": [
            {"action": item.entry.action, "source": item.entry.source,
             "destination": item.entry.destination, "original_sha256": item.previous_sha256,
             "replacement_sha256": item.source_sha256,
             "original_backup": "originals/" + item.entry.destination if item.previous_sha256 else None,
             "incoming_backup": "incoming/" + item.entry.destination if item.content is not None else None}
            for item in changed
        ],
    }
    # Preserve and verify originals and the incoming bytes before writing any destination.
    for item in changed:
        if item.previous_sha256 is not None:
            original = repo.joinpath(*item.entry.destination.split("/"))
            safe_existing_path(original, repo, "Backup source")
            copy_to = folder / "originals" / item.entry.destination
            copy_to.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(original, copy_to, follow_symlinks=False)
            if sha256(read_bounded_file(copy_to, folder, "Original backup", MAX_EXISTING_FILE)) != item.previous_sha256:
                raise ReplacementError(f"Original backup verification failed: {item.entry.destination}")
        if item.content is not None:
            incoming = folder / "incoming" / item.entry.destination
            incoming.parent.mkdir(parents=True, exist_ok=True)
            with incoming.open("xb") as stream:
                stream.write(item.content)
                stream.flush()
                os.fsync(stream.fileno())
            if sha256(read_bounded_file(incoming, folder, "Incoming backup", MAX_SOURCE_FILE)) != item.source_sha256:
                raise ReplacementError(f"Incoming backup verification failed: {item.entry.destination}")
    (folder / "report.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    assert_targets_unchanged(prepared, repo)
    touched: list[Prepared] = []
    try:
        for item in changed:
            target = repo.joinpath(*item.entry.destination.split("/"))
            safe_existing_path(target, repo, "Destination")
            if item.entry.action == "CREATE":
                if target.exists():
                    raise ReplacementError(f"CREATE target appeared during apply: {item.entry.destination}")
                target.parent.mkdir(parents=True, exist_ok=True)
                safe_existing_path(target, repo, "Destination")
            elif item.previous_sha256 is not None:
                original = read_bounded_file(target, repo, "Destination", MAX_EXISTING_FILE)
                if sha256(original) != item.previous_sha256:
                    raise ReplacementError(f"Destination changed during apply: {item.entry.destination}")
            touched.append(item)
            if item.entry.action == "DELETE":
                target.unlink()
            else:
                assert item.content is not None
                atomic_write(target, item.content)
            print(f"  {item.entry.action}: {item.entry.destination}")
        record["status"] = "applied"
        (folder / "report.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        return folder
    except BaseException as exc:
        rollback_errors: list[str] = []
        for item in reversed(touched):
            target = repo.joinpath(*item.entry.destination.split("/"))
            try:
                safe_existing_path(target, repo, "Rollback destination")
                if item.previous_sha256 is None:
                    if target.exists():
                        target.unlink()
                else:
                    old = folder / "originals" / item.entry.destination
                    data = read_bounded_file(old, folder, "Rollback original", MAX_EXISTING_FILE)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    atomic_write(target, data)
                    shutil.copystat(old, target)
            except Exception as rollback_exc:
                rollback_errors.append(f"{item.entry.destination}: {rollback_exc}")
        record["status"] = "rollback_incomplete" if rollback_errors else "rolled_back"
        record["failure"] = f"{type(exc).__name__}: {exc}"
        record["rollback_errors"] = rollback_errors
        (folder / "report.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        if rollback_errors:
            raise ReplacementError(f"Apply failed; rollback INCOMPLETE. Check {folder}. " + "; ".join(rollback_errors)) from exc
        raise ReplacementError(f"Apply failed; prior changes rolled back. Backups: {folder}. Cause: {exc}") from exc


def empty_default_downloads(downloads: Path, tools: Path) -> int:
    """Clear only the verified default download directory after a completed apply."""
    safe_existing_path(downloads, tools, "Default downloads")
    if not downloads.is_dir():
        raise ReplacementError(f"Default downloads is not a directory: {downloads}")
    removed = 0
    for child in downloads.iterdir():
        if child.is_symlink() or child.is_file():
            child.unlink()
        elif child.is_dir():
            shutil.rmtree(child)
        else:
            raise ReplacementError(f"Refusing to remove special download entry: {child}")
        removed += 1
    return removed


def cleanup_managed_source(source_path: Path, default_downloads: Path, tools: Path) -> str | None:
    """Remove only a successfully applied source managed by the default downloads folder."""
    safe_existing_path(default_downloads, tools, "Default downloads")
    if source_path == default_downloads:
        removed = empty_default_downloads(default_downloads, tools)
        return f"Cleared {removed} downloaded item(s) from {default_downloads}."
    if default_downloads not in source_path.parents:
        return None
    safe_existing_path(source_path, default_downloads, "Managed source")
    if source_path.is_symlink():
        raise ReplacementError(f"Refusing to remove symlinked managed source: {source_path}")
    if source_path.is_file():
        source_path.unlink()
    elif source_path.is_dir():
        shutil.rmtree(source_path)
    else:
        raise ReplacementError(f"Managed source disappeared or is not removable: {source_path}")
    return f"Removed applied source from managed downloads: {source_path}"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Preview or apply a self-contained, manifest-defined replacement package.")
    parser.add_argument("--source", help="Input package folder or ZIP (relative paths from current working directory)")
    parser.add_argument("--apply", action="store_true", help="Back up then apply the checked replacements")
    parser.add_argument("--allow-delete", action="store_true", help="Allow DELETE actions declared by the package manifest with --apply")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    repo = Path(__file__).absolute().parent.parent
    tools = repo / "tools"
    if tools.is_symlink() or not tools.is_dir() or Path(__file__).absolute().parent != tools:
        raise ReplacementError("Install the script as <repository>/tools/file_replacer.py")
    cwd = Path.cwd().absolute()
    if args.source:
        source_path = Path(args.source).absolute()
    elif cwd != repo and cwd != tools and tools in cwd.parents:
        source_path = cwd
    else:
        source_path = tools / DEFAULT_DOWNLOADS
        safe_existing_path(source_path, tools, "Default downloads")
        source_path.mkdir(exist_ok=True)
    safe_existing_path(source_path, tools, "Source")
    with DownloadSource(source_path, tools) as source:
        package_plan_entries = read_package_plan(source)
        entries = resolve_entries(source, package_plan_entries, repo)
        if not entries:
            raise ReplacementError("No replacement files or DELETE entries to apply")
        prepared = prepare(entries, source, repo, tools)
    print(f"Repository: {repo}\nDownloads:  {source_path}")
    print(f"Package manifest: {PACKAGE_PLAN} ({len(package_plan_entries)} explicit entries)")
    print(f"Checked {len(prepared)} entries:")
    for item in prepared:
        identical = " (identical; skip)" if item.entry.action == "MODIFY" and item.source_sha256 == item.previous_sha256 else ""
        print(f"  {item.entry.action:<6} {item.entry.source or '-'} -> {item.entry.destination}{identical}")
    if not args.apply:
        print("PREVIEW ONLY. Review mappings, then rerun with --apply.")
        if any(item.entry.action == "DELETE" for item in prepared):
            print("DELETE entries also require --allow-delete.")
        return 0
    backup_path = apply(prepared, repo, tools, args.allow_delete)
    if backup_path is not None:
        print(f"Applied changes. Original and incoming backups plus report: {backup_path}")
    default_downloads = tools / DEFAULT_DOWNLOADS
    cleanup_message = cleanup_managed_source(source_path, default_downloads, tools)
    if cleanup_message is not None:
        print(cleanup_message)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ReplacementError, OSError, zipfile.BadZipFile, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(2)
