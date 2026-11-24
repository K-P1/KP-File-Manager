#!/usr/bin/env python3
# Path: tools/codebase_packager.py
# Purpose: Safely copy generic source snapshots or requested files into a local folder and ZIP.
# Last changed: 2026-09-25, include the wrapper JAR and copy staging safely on Windows.
"""Package small, reviewable codebase source without uploading the working tree.

Requires Python 3.11 or newer, using only the standard library. Place this
script in <repository>/tools/codebase_packager.py. Run from any directory:

    python tools/codebase_packager.py snapshot
    python tools/codebase_packager.py select
    python tools/codebase_packager.py select --list tools/phase_2_files.txt
    python tools/codebase_packager.py snapshot --root /path/to/another/repository
    python tools/codebase_packager.py snapshot --max-file-kib 128
    python tools/codebase_packager.py snapshot --ignore tools/extra_ignore.txt

The default request list is <repository>/tools/requested_files.txt. Put one
repository-relative path, unique filename, or glob on each line. Lines starting
with # and blank lines are ignored. Backslashes in input paths are accepted.

Archives AND inspectable copies are written to the configured folder under tools/.
An optional --output must also be inside that folder. Both modes exclude
hidden components, symlinks, dependency/build directories, suspicious filenames,
non-UTF-8/binary data, and large files. The tracked Gradle wrapper JAR is the
sole binary exception. The caps are intentionally conservative.
A tools/codebase_packager.toml is automatically created on first use. Its
[limits], [output] and [ignore] sections control both modes. On first use only,
existing tools/.codebaseignore patterns are imported into that TOML; subsequent
runs use the TOML exclusively unless --ignore supplies an additional ignore file.
Git-style ignore patterns are extra exclusions, never safety overrides. Use / for
patterns, ** for recursive matching, and ! to re-include custom-ignored files
when their parent directories are not ignored. Overrides change only one run.
A snapshot with skipped oversized or unsafe files exits 1 and records them in the
manifest; missing/excluded requested files or exceeded directory/bundle limits exit 2.

Review the extracted files before sharing. Filename/content checks cannot detect
all hard-coded secrets or private data.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass, replace
from datetime import datetime, timezone
import fnmatch
import hashlib
import json
import os
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import shutil
import stat
import sys
import tempfile
import tomllib
import zipfile

KIB = 1024
MIB = 1024 * KIB
DEFAULT_FILE_KIB = 192
DEFAULT_FOLDER_MIB = 2  # Sum of immediate files, not entire nested source tree.
DEFAULT_ARCHIVE_MIB = 12  # Uncompressed source data, before manifest/ZIP overhead.
DEFAULT_MAX_FILES = 1_500
DEFAULT_EXPORT_DIRECTORY = "codebase_exports"
DEFAULT_CONFIG = "codebase_packager.toml"
DEFAULT_LIST = "requested_files.txt"
LEGACY_IGNORE = ".codebaseignore"

# The configuration remains editable, but intentionally cannot enable huge exports.
LIMIT_CEILINGS = {
    "max_file_kib": 512,
    "max_folder_mib": 8,
    "max_archive_mib": 32,
    "max_files": 3_000,
}

# Generic generated/dependency directory names. Hidden names are rejected separately.
EXCLUDED_DIRS = {
    "node_modules", "vendor", "venv", "env", "__pycache__", "site-packages",
    "build", "dist", "out", "target", "bin", "obj", "coverage", "htmlcov",
    "generated", "gen", "dist-newstyle", "_build", "_output", "tmp", "temp",
    "logs", "log", "test-results", "testreports", "deriveddata", "pods",
    "packages_cache", "bower_components", DEFAULT_EXPORT_DIRECTORY,
}

# Source, tests, schemas, ordinary text configuration and small documentation.
ALLOWED_SUFFIXES = {
    ".kt", ".kts", ".java", ".scala", ".sc", ".groovy", ".gradle",
    ".py", ".pyi", ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx",
    ".vue", ".svelte", ".html", ".css", ".scss", ".sass", ".less",
    ".c", ".cc", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".hxx",
    ".rs", ".go", ".swift", ".m", ".mm", ".cs", ".fs", ".fsx",
    ".rb", ".php", ".dart", ".ex", ".exs", ".erl", ".hrl",
    ".clj", ".cljs", ".cljc", ".lua", ".pl", ".pm", ".r", ".jl",
    ".sh", ".bash", ".zsh", ".fish", ".bat", ".cmd", ".ps1",
    ".sq", ".sqm", ".sql", ".prisma", ".graphql", ".gql", ".proto",
    ".tf", ".hcl", ".cmake", ".mk", ".make", ".dockerfile",
    ".md", ".mdx", ".txt", ".rst", ".adoc", ".xml", ".toml",
    ".yaml", ".yml", ".json", ".jsonc", ".properties", ".ini", ".cfg",
}
ALLOWED_NAMES = {
    "makefile", "gnumakefile", "dockerfile", "containerfile", "justfile",
    "cmakelists.txt", "jenkinsfile", "gemfile", "rakefile", "procfile",
    "gradlew", "gradlew.bat", "readme", "license", "copying",
}
EXCLUDED_NAMES = {
    "local.properties", "google-services.json", "googleservice-info.plist",
    "credentials.json", "secrets.json", "id_rsa", "id_ed25519",
    "known_hosts", "package-lock.json", "npm-shrinkwrap.json", "yarn.lock",
    "pnpm-lock.yaml", "poetry.lock", "pdm.lock", "uv.lock", "cargo.lock",
    "composer.lock", "gemfile.lock", DEFAULT_LIST,
}
EXCLUDED_SUFFIXES = {
    ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".asc",
    ".kdbx", ".db", ".sqlite", ".sqlite3", ".jar", ".aar", ".war",
    ".apk", ".aab", ".class", ".zip", ".7z", ".tar", ".gz",
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".pdf",
    ".mp4", ".mp3", ".wav", ".woff", ".woff2", ".ttf", ".otf",
    ".so", ".dll", ".dylib", ".exe", ".bin", ".log", ".lock",
    ".env", ".tfvars", ".tfstate", ".map", ".min.js", ".min.css",
}
# The wrapper is required to execute the Gradle tasks described by an exported
# codebase. Keep the exception path-specific so arbitrary JARs remain excluded.
ALLOWED_BINARY_PATHS = frozenset({"gradle/wrapper/gradle-wrapper.jar"})
SENSITIVE_PART_RE = re.compile(
    r"(?:^|[._-])(?:secrets?|credentials?|tokens?|passwords?|"
    r"private[_-]?keys?|api[_-]?keys?)(?:[._-]|$)", re.IGNORECASE
)
SECRET_CONTENT_RE = re.compile(
    rb"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----|"
    rb"(?:github_pat_|gh[pousr]_|glpat-|sk-proj-)[A-Za-z0-9_-]{16,}"
)
DRIVE_PREFIX_RE = re.compile(r"^[a-zA-Z]:")


@dataclass(frozen=True)
class Limits:
    max_file_kib: int = DEFAULT_FILE_KIB
    max_folder_mib: int = DEFAULT_FOLDER_MIB
    max_archive_mib: int = DEFAULT_ARCHIVE_MIB
    max_files: int = DEFAULT_MAX_FILES

    @property
    def file_bytes(self) -> int:
        return self.max_file_kib * KIB

    @property
    def folder_bytes(self) -> int:
        return self.max_folder_mib * MIB

    @property
    def archive_bytes(self) -> int:
        return self.max_archive_mib * MIB


@dataclass(frozen=True)
class Configuration:
    limits: Limits
    output_directory: str
    ignore_patterns: str


def validate_limit(name: str, value: object) -> int:
    if type(value) is not int or not 1 <= value <= LIMIT_CEILINGS[name]:
        raise ValueError(
            f"{name} must be an integer between 1 and {LIMIT_CEILINGS[name]:,}"
        )
    return value


def validated_output_directory(raw: object) -> Path:
    if not isinstance(raw, str) or not raw or "\\" in raw or DRIVE_PREFIX_RE.match(raw):
        raise ValueError("[output].directory must be a relative path using / separators")
    parts = raw.split("/")
    if any(part in ("", ".", "..") or part.startswith(".") for part in parts):
        raise ValueError("[output].directory must not contain empty, hidden, or traversal components")
    if any("\x00" in part or ":" in part for part in parts):
        raise ValueError("[output].directory contains an invalid path component")
    return Path(*parts)


def default_config_text(legacy_patterns: str = "") -> str:
    """Create a commented, editable configuration, importing old ignore rules once."""
    patterns = legacy_patterns.replace("\r\n", "\n").replace("\r", "\n").strip("\n")
    if "'''" in patterns:
        raise ValueError("Legacy ignore contains triple quotes; migrate its rules manually")
    if "\x00" in patterns:
        raise ValueError("Legacy ignore contains a NUL byte")
    pattern_lines = patterns + "\n" if patterns else "# Add repository-relative patterns here, one per line.\n"
    return (
        "# Path: tools/codebase_packager.toml\n"
        "# Purpose: Configure safe source extraction limits, output and Git-style ignore rules.\n"
        "# Last changed: 2026-09-16, initialize editable codebase packaging configuration.\n"
        "# Size limits are evaluated before ZIP compression.\n"
        "# Only files directly inside a folder count toward max_folder_mib.\n"
        "# Built-in hidden-file, symlink and credential exclusions cannot be overridden.\n\n"
        "[limits]\n"
        f"max_file_kib = {DEFAULT_FILE_KIB}\n"
        f"max_folder_mib = {DEFAULT_FOLDER_MIB}\n"
        f"max_archive_mib = {DEFAULT_ARCHIVE_MIB}\n"
        f"max_files = {DEFAULT_MAX_FILES}\n\n"
        "[output]\n"
        f'directory = "{DEFAULT_EXPORT_DIRECTORY}" # Relative to tools/\n\n'
        "[ignore]\n"
        "# Git-style: / anchors to root, trailing / ignores folders, ** matches deeply.\n"
        "# ! can undo another custom rule only if its parent directories are included.\n"
        "patterns = '''\n"
        + pattern_lines
        + "'''\n"
    )


def load_configuration(tools_dir: Path) -> tuple[Configuration, Path, bool]:
    """Create defaults once; import legacy ignore patterns only on initial creation."""
    config_path = tools_dir / DEFAULT_CONFIG
    if config_path.is_symlink():
        raise ValueError(f"Refusing symlinked configuration file: {config_path}")
    created = False
    if not config_path.exists():
        legacy_path = tools_dir / LEGACY_IGNORE
        legacy_patterns = ""
        if legacy_path.is_symlink():
            raise ValueError(f"Refusing symlinked legacy ignore file: {legacy_path}")
        if legacy_path.exists():
            if not legacy_path.is_file() or legacy_path.stat().st_size > DEFAULT_FILE_KIB * KIB:
                raise ValueError(f"Legacy ignore is not a small regular file: {legacy_path}")
            legacy_patterns = legacy_path.read_text(encoding="utf-8-sig")
            # Do not create a config with rules that will fail at run time.
            IgnoreRules.parse(legacy_patterns, legacy_path.as_posix())
        try:
            with config_path.open("x", encoding="utf-8", newline="\n") as stream:
                stream.write(default_config_text(legacy_patterns))
            created = True
        except FileExistsError:
            # Another invocation initialized it first; read that exact file.
            pass
    if not config_path.is_file() or config_path.stat().st_size > DEFAULT_FILE_KIB * KIB:
        raise ValueError(f"Configuration is not a small regular file: {config_path}")
    try:
        with config_path.open("rb") as stream:
            document = tomllib.load(stream)
    except tomllib.TOMLDecodeError as exc:
        raise ValueError(f"Invalid TOML in {config_path}: {exc}") from exc
    expected = {"limits", "output", "ignore"}
    if set(document) != expected:
        raise ValueError(f"Configuration sections must be exactly {sorted(expected)}")
    for section, keys in (
        ("limits", set(LIMIT_CEILINGS)),
        ("output", {"directory"}),
        ("ignore", {"patterns"}),
    ):
        if not isinstance(document[section], dict) or set(document[section]) != keys:
            raise ValueError(f"[{section}] must contain exactly {sorted(keys)}")
    limit_values = {
        name: validate_limit(name, document["limits"][name]) for name in LIMIT_CEILINGS
    }
    raw_output = document["output"]["directory"]
    output_directory = validated_output_directory(raw_output).as_posix()
    patterns = document["ignore"]["patterns"]
    if not isinstance(patterns, str):
        raise ValueError("[ignore].patterns must be a multiline TOML string")
    IgnoreRules.parse(patterns, config_path.as_posix())
    return Configuration(Limits(**limit_values), output_directory, patterns), config_path, created


def glob_to_regex(pattern: str) -> str:
    """Translate Git-style path globs without allowing * and ? to cross /."""
    pieces: list[str] = []
    index = 0
    while index < len(pattern):
        char = pattern[index]
        if char == "\\" and index + 1 < len(pattern):
            pieces.append(re.escape(pattern[index + 1]))
            index += 2
            continue
        if char == "*":
            if index + 1 < len(pattern) and pattern[index + 1] == "*":
                index += 2
                if index < len(pattern) and pattern[index] == "/":
                    pieces.append("(?:[^/]+/)*")
                    index += 1
                else:
                    pieces.append(".*")
                continue
            pieces.append("[^/]*")
        elif char == "?":
            pieces.append("[^/]")
        elif char == "[":
            closing = pattern.find("]", index + 1)
            if closing < 0:
                pieces.append(r"\[")
            else:
                content = pattern[index + 1:closing]
                if content and content[0] in ("!", "^"):
                    content = "^" + content[1:]
                # Prevent even a negated character class from crossing /.
                pieces.append("(?:(?!/)[" + content.replace("\\", "\\\\") + "])")
                index = closing
        else:
            pieces.append(re.escape(char))
        index += 1
    return "".join(pieces)


class IgnoreRules:
    """Root-relative, ordered, Git-style exclusions with parent-directory pruning."""

    def __init__(self, rules: list[tuple[re.Pattern[str], bool, bool]], sources: list[str]):
        self.rules = rules
        self.sources = sources

    @classmethod
    def parse(cls, text: str, source: str) -> IgnoreRules:
        rules: list[tuple[re.Pattern[str], bool, bool]] = []
        for line_no, raw in enumerate(text.splitlines(), 1):
            # Unescaped trailing spaces do not belong to the pattern.
            while raw.endswith(" ") and not raw.endswith("\\ "):
                raw = raw[:-1]
            if not raw or raw.startswith("#"):
                continue
            negated = raw.startswith("!")
            if negated:
                raw = raw[1:]
            directory_only = raw.endswith("/")
            if directory_only:
                raw = raw[:-1]
            anchored = raw.startswith("/")
            if anchored:
                raw = raw[1:]
            if not raw or ".." in PurePosixPath(raw).parts:
                raise ValueError(f"Invalid ignore pattern in {source} line {line_no}: {raw!r}")
            regex = glob_to_regex(raw)
            expression = ("^" if anchored or "/" in raw else r"(?:^|.*/)") + regex + "$"
            try:
                rules.append((re.compile(expression), negated, directory_only))
            except re.error as exc:
                raise ValueError(f"Invalid ignore pattern in {source} line {line_no}: {raw!r}") from exc
        return cls(rules, [source])

    @classmethod
    def load(cls, root: Path, ignore_path: Path, max_bytes: int) -> IgnoreRules:
        if ignore_path.is_symlink():
            raise ValueError(f"Refusing symlinked ignore file: {ignore_path}")
        if not ignore_path.exists():
            raise ValueError(f"Ignore file not found: {ignore_path}")
        if not ignore_path.is_file():
            raise ValueError(f"Ignore path is not a regular file: {ignore_path}")
        if ignore_path.stat().st_size > max_bytes:
            raise ValueError(f"Ignore file exceeds {max_bytes:,} bytes: {ignore_path}")
        return cls.parse(ignore_path.read_text(encoding="utf-8-sig"), ignore_path.relative_to(root).as_posix())

    def direct_match(self, path: str, is_dir: bool) -> bool:
        ignored = False
        for expression, negated, directory_only in self.rules:
            if (is_dir or not directory_only) and expression.fullmatch(path):
                ignored = not negated
        return ignored

    def is_ignored(self, path: str, is_dir: bool = False) -> bool:
        parts = PurePosixPath(path).parts
        # A negation cannot recover a file from inside a pruned parent folder.
        for index in range(1, len(parts)):
            if self.direct_match("/".join(parts[:index]), is_dir=True):
                return True
        return self.direct_match(path, is_dir)


def is_within(path: Path, directory: Path) -> bool:
    return path == directory or directory in path.parents


def exclusion_reason(relative: PurePosixPath) -> str | None:
    parts = relative.parts
    if any(part.startswith(".") for part in parts):
        return "hidden file or directory"
    if any(part.casefold() in EXCLUDED_DIRS for part in parts[:-1]):
        return "dependency, generated, or build directory"
    if any(SENSITIVE_PART_RE.search(part) for part in parts[:-1]):
        return "potentially sensitive directory"
    name = relative.name.casefold()
    if name in EXCLUDED_NAMES or name.startswith("env.") or name.endswith(".env"):
        return "local configuration, credential, or lockfile"
    if SENSITIVE_PART_RE.search(relative.name):
        return "potential credential file"
    if not is_allowed_binary_path(relative) and any(name.endswith(suffix) for suffix in EXCLUDED_SUFFIXES):
        return "binary, generated, or sensitive format"
    if (
        not is_allowed_binary_path(relative)
        and name not in ALLOWED_NAMES
        and PurePosixPath(name).suffix not in ALLOWED_SUFFIXES
    ):
        return "not a recognized source/config/documentation file"
    return None


def is_allowed_binary_path(relative: PurePosixPath) -> bool:
    """Return whether a reviewed binary is required for a runnable snapshot."""
    return relative.as_posix().casefold() in ALLOWED_BINARY_PATHS


def find_candidates(
    root: Path, export_dir: Path, ignored_files: set[Path], ignore: IgnoreRules,
    limits: Limits,
) -> tuple[dict[str, Path], dict[str, int], int]:
    available: dict[str, Path] = {}
    oversized: dict[str, int] = {}
    custom_ignored = 0
    for current, dirs, names in os.walk(root, topdown=True, followlinks=False):
        current_path = Path(current)
        retained_dirs = []
        for name in sorted(dirs):
            full = current_path / name
            rel = PurePosixPath(full.relative_to(root).as_posix())
            if name.startswith(".") or name.casefold() in EXCLUDED_DIRS:
                continue
            if SENSITIVE_PART_RE.search(name) or full.is_symlink():
                continue
            # Never read the packager's own output, including earlier extractions.
            if full == export_dir:
                continue
            if exclusion_reason(rel / "source.kt") is not None:
                continue
            if ignore.is_ignored(rel.as_posix(), is_dir=True):
                custom_ignored += 1  # Directory count, not individual descendant count.
                continue
            retained_dirs.append(name)
        dirs[:] = retained_dirs
        for name in sorted(names):
            full = current_path / name
            rel = PurePosixPath(full.relative_to(root).as_posix())
            if exclusion_reason(rel) is not None or full.is_symlink():
                continue
            if full in ignored_files:
                continue
            if ignore.is_ignored(rel.as_posix()):
                custom_ignored += 1
                continue
            try:
                info = full.lstat()
            except OSError:
                continue
            if not stat.S_ISREG(info.st_mode):
                continue
            if info.st_size > limits.file_bytes:
                oversized[rel.as_posix()] = info.st_size
                continue
            available[rel.as_posix()] = full
    return available, oversized, custom_ignored


def load_selectors(path: Path) -> list[str]:
    if not path.is_file():
        raise ValueError(
            f"Request list not found: {path}. Create it with one repository-relative path per line."
        )
    selectors = []
    for line_number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        value = raw.strip().strip("`").strip().replace("\\", "/")
        if not value or value.startswith("#"):
            continue
        parsed = PurePosixPath(value)
        if parsed.is_absolute() or PureWindowsPath(value).is_absolute() or DRIVE_PREFIX_RE.match(value):
            raise ValueError(f"Line {line_number}: absolute paths are not accepted: {value}")
        if ".." in parsed.parts or value == ".":
            raise ValueError(f"Line {line_number}: parent traversal is not accepted: {value}")
        if any(part.startswith(".") for part in parsed.parts):
            raise ValueError(f"Line {line_number}: hidden paths are not accepted: {value}")
        selectors.append(parsed.as_posix())
    if not selectors:
        raise ValueError(f"Request list is empty: {path}")
    return selectors


def resolve_selectors(
    selectors: list[str], available: dict[str, Path], oversized: dict[str, int], ignore: IgnoreRules
) -> tuple[list[str], list[str]]:
    selected: set[str] = set()
    errors: list[str] = []
    for request in selectors:
        if any(ch in request for ch in "*?["):
            matches = [name for name in available if fnmatch.fnmatchcase(name, request)]
            large = [name for name in oversized if fnmatch.fnmatchcase(name, request)]
            if not matches and not large:
                errors.append(f"No eligible files match glob: {request}")
            for name in sorted(large):
                errors.append(f"Oversized requested file ({oversized[name]:,} bytes): {name}")
            selected.update(matches)
        elif request in available:
            selected.add(request)
        elif request in oversized:
            errors.append(f"Oversized requested file ({oversized[request]:,} bytes): {request}")
        elif "/" not in request:
            matches = [name for name in available if PurePosixPath(name).name == request]
            large = [name for name in oversized if PurePosixPath(name).name == request]
            if len(matches) == 1 and not large:
                selected.add(matches[0])
            elif len(matches) + len(large) > 1:
                errors.append(f"Ambiguous filename {request}; supply its complete relative path")
            elif large:
                errors.append(f"Oversized requested file ({oversized[large[0]]:,} bytes): {large[0]}")
            else:
                errors.append(f"Missing or excluded requested file: {request}")
        else:
            reason = exclusion_reason(PurePosixPath(request))
            if ignore.is_ignored(request):
                reason = "excluded by custom ignore file"
            errors.append(f"Missing or excluded requested file: {request}" + (f" ({reason})" if reason else ""))
    return sorted(selected), errors


def copy_and_hash(
    source: Path,
    target: Path,
    max_file_bytes: int,
    *,
    allow_binary: bool = False,
) -> tuple[int, str]:
    """Read via no-follow descriptor, validate copied bytes, and hash that exact copy."""
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(source, flags)
    try:
        with os.fdopen(fd, "rb", closefd=False) as stream:
            info = os.fstat(fd)
            if not stat.S_ISREG(info.st_mode) or info.st_size > max_file_bytes:
                raise ValueError(f"File is not regular or exceeds {max_file_bytes:,} bytes: {source}")
            content = stream.read(max_file_bytes + 1)
        if len(content) > max_file_bytes:
            raise ValueError(f"File grew beyond {max_file_bytes:,} bytes: {source}")
        if not allow_binary:
            if b"\x00" in content or any(c < 32 and c not in (9, 10, 12, 13) for c in content):
                raise ValueError(f"Binary/control-character content rejected: {source}")
            try:
                content.decode("utf-8-sig")
            except UnicodeDecodeError as exc:
                raise ValueError(f"Non-UTF-8 text rejected: {source}") from exc
        if SECRET_CONTENT_RE.search(content):
            raise ValueError(f"Potential private key or access token detected: {source}")
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as output:
            output.write(content)
        return len(content), hashlib.sha256(content).hexdigest()
    finally:
        os.close(fd)


def package(
    root: Path, destination: Path, selected: list[str], available: dict[str, Path],
    mode: str, skipped_oversize: dict[str, int], ignore: IgnoreRules, custom_ignored: int,
    limits: Limits, config_path: Path,
) -> tuple[Path, int, int, dict[str, str]]:
    folder = destination.with_suffix("")
    if destination.exists() or folder.exists():
        raise FileExistsError(f"Archive or extraction folder already exists: {destination} / {folder}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Scratch lives under exports; the source walk always ignores exports.
    with tempfile.TemporaryDirectory(prefix=".codebase-pack-", dir=destination.parent) as scratch:
        staged = Path(scratch) / "bundle"
        copy_root = staged / root.name
        entries: list[dict[str, str | int]] = []
        skipped_unsafe: dict[str, str] = {}
        directory_sizes: dict[str, int] = defaultdict(int)
        total = 0
        for name in selected:
            source = available[name]
            target = copy_root.joinpath(*PurePosixPath(name).parts)
            try:
                count, checksum = copy_and_hash(
                    source,
                    target,
                    limits.file_bytes,
                    allow_binary=is_allowed_binary_path(PurePosixPath(name)),
                )
            except ValueError as exc:
                if mode != "snapshot":
                    raise
                skipped_unsafe[name] = str(exc).split(": ", 1)[0]
                continue
            directory = PurePosixPath(name).parent.as_posix()
            directory_sizes[directory] += count
            total += count
            if directory_sizes[directory] > limits.folder_bytes:
                raise ValueError(
                    f"Directory exceeds {limits.folder_bytes:,} bytes of immediate files: {directory}. "
                    "Use a smaller request list."
                )
            if total > limits.archive_bytes:
                raise ValueError(
                    f"Source exceeds {limits.archive_bytes:,} bytes uncompressed. Use select mode or smaller requests."
                )
            entries.append({"path": name, "bytes": count, "sha256": checksum})
        if not entries:
            raise ValueError("No safe source files remain after content validation; no ZIP created")
        manifest = {
            "format_version": 4,
            "repository": root.name,
            "mode": mode,
            "created_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "configuration_file": config_path.relative_to(root).as_posix(),
            "ignore_sources": ignore.sources,
            "custom_ignore_rules": len(ignore.rules),
            "custom_ignored_entries": custom_ignored,
            "limits": {
                "file_bytes": limits.file_bytes,
                "directory_immediate_bytes": limits.folder_bytes,
                "total_source_bytes": limits.archive_bytes,
                "file_count": limits.max_files,
            },
            "source_bytes": total,
            "file_count": len(entries),
            "skipped_oversize": [
                {"path": name, "bytes": size} for name, size in sorted(skipped_oversize.items())
            ],
            "skipped_unsafe": [
                {"path": name, "reason": reason} for name, reason in sorted(skipped_unsafe.items())
            ],
            "files": entries,
        }
        (staged / "manifest.json").write_text(
            json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
        )
        archive_tmp = Path(scratch) / "archive.zip"
        with zipfile.ZipFile(archive_tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for item in sorted(staged.rglob("*")):
                if item.is_file():
                    archive.write(item, item.relative_to(staged).as_posix())
        # Windows can deny a directory rename from an active TemporaryDirectory.
        # Copying retains copytree's no-overwrite behavior and lets the
        # context manager remove the scratch directory after the archive is moved.
        shutil.copytree(staged, folder)
        try:
            shutil.move(str(archive_tmp), destination)
        except BaseException:
            shutil.rmtree(folder)
            raise
    return folder, len(entries), total, skipped_unsafe


def resolve_tools_path(root: Path, tools_dir: Path, requested: Path, kind: str) -> Path:
    path = (requested if requested.is_absolute() else root / requested).expanduser().resolve()
    if not is_within(path, tools_dir):
        raise ValueError(f"{kind} must be under {tools_dir}: {path}")
    return path


def empty_export_directory(export_dir: Path) -> int:
    """Remove prior export artifacts without following symlinks."""
    if export_dir.is_symlink() or not export_dir.is_dir():
        raise ValueError(f"Configured output directory is not a regular directory: {export_dir}")
    removed = 0
    for child in export_dir.iterdir():
        if child.is_symlink() or child.is_file():
            child.unlink()
        elif child.is_dir():
            shutil.rmtree(child)
        else:
            raise ValueError(f"Refusing to remove special output entry: {child}")
        removed += 1
    return removed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=("snapshot", "select"))
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="Repository root (default: parent of the script's tools directory)")
    parser.add_argument("--list", dest="list_file", type=Path,
                        help="Request list under tools/ (default: tools/requested_files.txt)")
    parser.add_argument("--ignore", type=Path,
                        help="Additional ignore file under tools/ for this run only")
    parser.add_argument("--output", type=Path,
                        help="ZIP path inside the configured tools/ output directory")
    parser.add_argument("--max-file-kib", type=int,
                        help="Override [limits].max_file_kib for this run")
    parser.add_argument("--max-folder-mib", type=int,
                        help="Override [limits].max_folder_mib for this run")
    parser.add_argument("--max-archive-mib", type=int,
                        help="Override [limits].max_archive_mib for this run")
    parser.add_argument("--max-files", type=int,
                        help="Override [limits].max_files for this run")
    args = parser.parse_args(argv)
    if args.mode == "snapshot" and args.list_file:
        parser.error("--list applies only to select mode")
    root = args.root.expanduser().resolve()
    if not root.is_dir():
        parser.error(f"Repository root not found: {root}")
    tools_dir = root / "tools"
    if tools_dir.is_symlink():
        parser.error(f"Refusing symlinked tools directory: {tools_dir}")
    try:
        tools_dir.mkdir(exist_ok=True)
        config, config_path, config_created = load_configuration(tools_dir)
        overrides = {
            name: getattr(args, name)
            for name in LIMIT_CEILINGS
            if getattr(args, name) is not None
        }
        limits = replace(
            config.limits,
            **{name: validate_limit(name, value) for name, value in overrides.items()},
        )
        output_parts = validated_output_directory(config.output_directory)
        export_dir = tools_dir / output_parts
        output_parents = (
            tools_dir,
            *(tools_dir.joinpath(*output_parts.parts[:n])
              for n in range(1, len(output_parts.parts) + 1)),
        )
        for parent in output_parents:
            if parent.is_symlink():
                raise ValueError(f"Refusing symlinked output path: {parent}")
        export_dir.mkdir(parents=True, exist_ok=True)
        if not export_dir.is_dir():
            raise ValueError(f"Configured output directory is not a directory: {export_dir}")
        removed_exports = empty_export_directory(export_dir)
        request_list = resolve_tools_path(
            root, tools_dir, args.list_file or Path("tools") / DEFAULT_LIST, "--list"
        )
        ignore = IgnoreRules.parse(config.ignore_patterns, config_path.relative_to(root).as_posix())
        ignored_files = {request_list, config_path}
        if args.ignore is not None:
            raw_ignore_path = args.ignore if args.ignore.is_absolute() else root / args.ignore
            if raw_ignore_path.is_symlink():
                raise ValueError(f"Refusing symlinked ignore file: {raw_ignore_path}")
            ignore_path = resolve_tools_path(root, tools_dir, args.ignore, "--ignore")
            additional = IgnoreRules.load(root, ignore_path, limits.file_bytes)
            ignore.rules.extend(additional.rules)
            ignore.sources.extend(additional.sources)
            ignored_files.add(ignore_path)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
        output = resolve_tools_path(
            root, tools_dir,
            args.output or Path("tools") / output_parts / f"{args.mode}_{timestamp}.zip",
            "--output",
        )
        if output.parent != export_dir or output.suffix.lower() != ".zip":
            raise ValueError(f"--output must be a .zip directly inside {export_dir}")
        available, oversized, custom_ignored = find_candidates(
            root, export_dir, ignored_files, ignore, limits
        )
        if args.mode == "select":
            selected, errors = resolve_selectors(load_selectors(request_list), available, oversized, ignore)
            if errors:
                for error in errors:
                    print(f"ERROR: {error}", file=sys.stderr)
                return 2  # Never return a partial selection as a successful handoff.
            skipped = {}
        else:
            selected, skipped = sorted(available), oversized
        if not selected:
            raise ValueError("No eligible source files found; no ZIP created")
        if len(selected) > limits.max_files:
            raise ValueError(f"{len(selected)} files exceed the {limits.max_files:,}-file cap")
        folder, count, total, skipped_unsafe = package(
            root, output, selected, available, args.mode, skipped, ignore, custom_ignored,
            limits, config_path,
        )
    except (OSError, UnicodeError, ValueError, zipfile.BadZipFile) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    print(f"Copied {count} files ({total:,} bytes of source) into {folder}")
    print(f"ZIP: {output}")
    print(f"Manifest: {folder / 'manifest.json'}")
    if removed_exports:
        print(f"Cleared {removed_exports} prior export artifact(s) from {export_dir}")
    if config_created:
        print(f"Created configuration: {config_path}")
    print(f"Configuration: {config_path}")
    print(f"Ignore rules: {len(ignore.rules)} from {', '.join(ignore.sources)} "
          f"({custom_ignored} excluded entries; directories count once)")
    if skipped:
        print(f"WARNING: {len(skipped)} source files exceeded the {limits.file_bytes:,}-byte file limit.", file=sys.stderr)
        for name, size in sorted(skipped.items()):
            print(f"  SKIPPED ({size:,} bytes): {name}", file=sys.stderr)
        print("Snapshot is incomplete; review the manifest before sharing.", file=sys.stderr)
    if skipped_unsafe:
        print(f"WARNING: {len(skipped_unsafe)} unsafe or non-text files were excluded.", file=sys.stderr)
        for name, reason in sorted(skipped_unsafe.items()):
            print(f"  SKIPPED ({reason}): {name}", file=sys.stderr)
        print("Snapshot is incomplete; review the manifest before sharing.", file=sys.stderr)
    print("Inspect the extraction for embedded secrets or private data before uploading.")
    return 1 if skipped or skipped_unsafe else 0


if __name__ == "__main__":
    sys.exit(main())
