"""
Path: tools/map_codebase.py
Purpose: Generates a two-part Markdown codebase map with a compact file tree and catalog extracted from top-of-file metadata comments.
Last changed: 2026-09-27, add deployable Path metadata to generated Markdown output.
Run: python tools/map_codebase.py
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
from collections import Counter
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path


DEFAULT_IGNORED_DIRS = {
    ".git", ".gradle", ".idea", ".kotlin", ".mypy_cache", ".pytest_cache",
    ".ruff_cache", ".venv", "__pycache__", "build", "dist", "node_modules", "out",
}
DEFAULT_IGNORED_FILES = {".DS_Store"}
TEXT_EXTENSIONS = {
    ".bat", ".cmd", ".css", ".gradle", ".groovy", ".html", ".java", ".js",
    ".json", ".kt", ".kts", ".md", ".pro", ".properties", ".ps1", ".py",
    ".sh", ".sq", ".sql", ".toml", ".ts", ".txt", ".xml", ".yaml", ".yml", ".sqm", ".sqh"
}
KOTLIN_JAVA_EXTENSIONS = {".java", ".kt", ".kts"}
SQLDELIGHT_EXTENSIONS = {".sq", ".sqm", ".sqh", ".sql"}
HEADER_READ_BYTES = 16 * 1024
HEADER_READ_LINES = 80
HEADER_FIELD_PATTERN = re.compile(
    r"^(Path|Purpose|Last changed|Run|Action)\s*:\s*(.*)$", re.IGNORECASE
)
DATE_PREFIX_PATTERN = re.compile(r"^(\d{4}-\d{2}-\d{2})(?:\b|,)")
PYTHON_ENCODING_PATTERN = re.compile(r"^#.*coding[:=]\s*[-\w.]+")


@dataclass(frozen=True)
class FileHeader:
    actual_path: Path
    declared_path: str | None
    purpose: str | None
    last_changed: str | None
    run_commands: tuple[str, ...]
    actions: tuple[str, ...]
    header_found: bool
    text_file: bool
    warnings: tuple[str, ...]

    @property
    def complete(self) -> bool:
        return (
            self.header_found
            and self.declared_path is not None
            and self.purpose is not None
            and self.last_changed is not None
            and not self.warnings
        )


def default_repository_root() -> Path:
    return Path(__file__).resolve().parent.parent


def run_git(root: Path, *args: str) -> subprocess.CompletedProcess[bytes] | None:
    if shutil.which("git") is None:
        return None
    try:
        return subprocess.run(
            ["git", "-C", str(root), *args],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    except OSError:
        return None


def is_git_worktree(root: Path) -> bool:
    result = run_git(root, "rev-parse", "--is-inside-work-tree")
    return (
        result is not None
        and result.returncode == 0
        and result.stdout.decode("utf-8", errors="replace").strip() == "true"
    )


def git_metadata(root: Path) -> tuple[str | None, str | None]:
    if not is_git_worktree(root):
        return None, None

    def value(*args: str) -> str | None:
        result = run_git(root, *args)
        if result is None or result.returncode != 0:
            return None
        return result.stdout.decode("utf-8", errors="replace").strip() or None

    return value("branch", "--show-current"), value("rev-parse", "--short=12", "HEAD")


def should_ignore_relative(
    relative_path: Path,
    ignored_dirs: set[str],
    ignored_files: set[str],
    output_relative: Path | None,
) -> bool:
    return (
        (output_relative is not None and relative_path == output_relative)
        or relative_path.name in ignored_files
        or any(part in ignored_dirs for part in relative_path.parts[:-1])
    )


def git_visible_files(
    root: Path,
    ignored_dirs: set[str],
    ignored_files: set[str],
    output_path: Path,
) -> list[Path] | None:
    if not is_git_worktree(root):
        return None

    result = run_git(
        root, "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "."
    )
    if result is None or result.returncode != 0:
        return None

    try:
        output_relative = output_path.relative_to(root)
    except ValueError:
        output_relative = None

    files: set[Path] = set()
    for raw_path in result.stdout.split(b"\0"):
        if not raw_path:
            continue
        relative_path = Path(raw_path.decode("utf-8", errors="surrogateescape"))
        if should_ignore_relative(relative_path, ignored_dirs, ignored_files, output_relative):
            continue
        absolute_path = root / relative_path
        if absolute_path.is_file() or absolute_path.is_symlink():
            files.add(relative_path)

    return sorted(files, key=lambda path: path.as_posix().lower())


def filesystem_files(
    root: Path,
    ignored_dirs: set[str],
    ignored_files: set[str],
    output_path: Path,
) -> list[Path]:
    try:
        output_relative = output_path.relative_to(root)
    except ValueError:
        output_relative = None

    files: list[Path] = []
    for directory, dirnames, filenames in os.walk(root, followlinks=False):
        current = Path(directory)
        dirnames[:] = sorted(
            (
                name
                for name in dirnames
                if name not in ignored_dirs and not (current / name).is_symlink()
            ),
            key=str.lower,
        )
        for filename in sorted(filenames, key=str.lower):
            if filename in ignored_files:
                continue
            relative_path = (current / filename).relative_to(root)
            if output_relative is None or relative_path != output_relative:
                files.append(relative_path)
    return files


def build_parent_directories(files: list[Path]) -> set[Path]:
    directories: set[Path] = set()
    for file_path in files:
        parent = file_path.parent
        while parent != Path("."):
            directories.add(parent)
            parent = parent.parent
    return directories


def collect_build_files(
    root: Path,
    visible_files: list[Path],
    ignored_dirs: set[str],
    ignored_files: set[str],
    output_path: Path,
) -> list[Path]:
    candidate_parents = {Path(".")} | build_parent_directories(visible_files)
    build_dirs = {
        root / parent / "build"
        for parent in candidate_parents
        if (root / parent / "build").is_dir()
    }
    nested_ignored_dirs = set(ignored_dirs)
    nested_ignored_dirs.discard("build")
    build_files: list[Path] = []
    for build_dir in sorted(build_dirs, key=lambda path: path.as_posix().lower()):
        for relative_path in filesystem_files(
            build_dir, nested_ignored_dirs, ignored_files, output_path
        ):
            build_files.append((build_dir / relative_path).relative_to(root))
    return build_files


def collect_files(
    root: Path,
    ignored_dirs: set[str],
    ignored_files: set[str],
    output_path: Path,
    respect_gitignore: bool,
    include_build: bool,
) -> tuple[list[Path], str]:
    if respect_gitignore:
        git_files = git_visible_files(root, ignored_dirs, ignored_files, output_path)
        if git_files is not None:
            files = git_files
            if include_build:
                files = sorted(
                    set(files)
                    | set(
                        collect_build_files(
                            root, files, ignored_dirs, ignored_files, output_path
                        )
                    ),
                    key=lambda path: path.as_posix().lower(),
                )
            return files, "Git tracked/unignored files"

    filesystem_ignored_dirs = set(ignored_dirs)
    if include_build:
        filesystem_ignored_dirs.discard("build")
    files = filesystem_files(root, filesystem_ignored_dirs, ignored_files, output_path)
    return files, (
        "Filesystem scan (--no-gitignore)" if not respect_gitignore else "Filesystem fallback"
    )


def build_tree(root: Path, files: list[Path]) -> list[str]:
    tree: dict[str, dict | None] = {}
    for relative_path in files:
        node = tree
        for part in relative_path.parts[:-1]:
            child = node.setdefault(part, {})
            if not isinstance(child, dict):
                break
            node = child
        else:
            node[relative_path.name] = None

    lines = [f"{root.name}/"]

    def walk(node: dict[str, dict | None], prefix: str = "") -> None:
        children = sorted(node.items(), key=lambda item: (item[1] is None, item[0].lower()))
        for index, (name, child) in enumerate(children):
            is_last = index == len(children) - 1
            connector = "└── " if is_last else "├── "
            if isinstance(child, dict):
                lines.append(f"{prefix}{connector}{name}/")
                walk(child, prefix + ("    " if is_last else "│   "))
            else:
                lines.append(f"{prefix}{connector}{name}")

    walk(tree)
    return lines


def count_lines(path: Path) -> int | None:
    if path.is_symlink() or path.suffix.lower() not in TEXT_EXTENSIONS:
        return None
    try:
        with path.open("r", encoding="utf-8", errors="ignore") as file:
            return sum(1 for _ in file)
    except (OSError, UnicodeError):
        return None


def collect_stats(root: Path, files: list[Path]) -> dict[str, object]:
    extension_counts: Counter[str] = Counter()
    kotlin_java_source_count = 0
    sqldelight_file_count = 0
    total_lines = 0

    for relative_path in files:
        extension = relative_path.suffix.lower() or "[no extension]"
        extension_counts[extension] += 1
        kotlin_java_source_count += extension in KOTLIN_JAVA_EXTENSIONS
        sqldelight_file_count += extension in SQLDELIGHT_EXTENSIONS
        lines = count_lines(root / relative_path)
        if lines is not None:
            total_lines += lines

    return {
        "file_count": len(files),
        "directory_count": len(build_parent_directories(files)),
        "kotlin_java_source_count": kotlin_java_source_count,
        "sqldelight_file_count": sqldelight_file_count,
        "total_text_lines": total_lines,
        "extension_counts": extension_counts,
    }


def read_header_prefix(path: Path) -> tuple[str | None, bool]:
    if path.is_symlink():
        return None, False
    try:
        with path.open("rb") as file:
            raw = file.read(HEADER_READ_BYTES)
    except OSError:
        return None, False
    if b"\x00" in raw:
        return None, False
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return None, False
    return "\n".join(text.splitlines()[:HEADER_READ_LINES]), True


def strip_allowed_preamble(text: str) -> str:
    lines = text.lstrip("\ufeff").splitlines()
    while lines:
        first = lines[0].strip()
        if first.startswith("#!") or PYTHON_ENCODING_PATTERN.match(first):
            lines.pop(0)
        else:
            break
    return "\n".join(lines).lstrip()


def extract_comment_body(text: str) -> str | None:
    candidate = strip_allowed_preamble(text)
    block_markers = (("/*", "*/"), ("<!--", "-->"), ('"""', '"""'), ("'''", "'''"))
    for start, end_marker in block_markers:
        if candidate.startswith(start):
            end = candidate.find(end_marker, len(start))
            return None if end == -1 else candidate[len(start):end]

    lines = candidate.splitlines()
    if not lines:
        return None
    prefixes = ("//", "#", "--", ";")
    prefix = next((p for p in prefixes if lines[0].lstrip().startswith(p)), None)
    if prefix is None:
        return None

    body: list[str] = []
    for line in lines:
        stripped = line.lstrip()
        if not stripped:
            body.append("")
        elif stripped.startswith(prefix):
            body.append(stripped[len(prefix):])
        else:
            break
    return "\n".join(body)


def clean_comment_lines(comment_body: str) -> list[str]:
    lines: list[str] = []
    for raw_line in comment_body.splitlines():
        line = raw_line.strip()
        if line.startswith("*"):
            line = line[1:].lstrip()
        lines.append(line.rstrip())
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return lines


def normalize_declared_path(value: str) -> str:
    normalized = value.strip().strip("`").replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized.rstrip("/")


def looks_like_legacy_path(value: str) -> bool:
    candidate = normalize_declared_path(value)
    return bool(candidate and " " not in candidate and ("/" in candidate or Path(candidate).suffix))


def valid_last_changed(value: str) -> bool:
    match = DATE_PREFIX_PATTERN.match(value.strip())
    if match is None:
        return False
    try:
        date.fromisoformat(match.group(1))
    except ValueError:
        return False
    return True


def parse_header_fields(lines: list[str]) -> tuple[dict[str, str], list[str], list[str]]:
    fields: dict[str, str] = {}
    run_commands: list[str] = []
    actions: list[str] = []
    nonempty = [line.strip() for line in lines if line.strip()]

    for line in nonempty:
        match = HEADER_FIELD_PATTERN.match(line)
        if match is None:
            continue
        name, value = match.group(1).casefold(), match.group(2).strip()
        if not value:
            continue
        if name == "run":
            run_commands.append(value)
        elif name == "action":
            actions.append(value)
        else:
            key = {"path": "declared_path", "purpose": "purpose", "last changed": "last_changed"}[name]
            fields.setdefault(key, value)

    if "declared_path" not in fields and nonempty:
        first = nonempty[0]
        if HEADER_FIELD_PATTERN.match(first) is None and looks_like_legacy_path(first):
            fields["declared_path"] = normalize_declared_path(first)
            if len(nonempty) > 1 and HEADER_FIELD_PATTERN.match(nonempty[1]) is None:
                fields["purpose"] = nonempty[1]

    return fields, run_commands, actions


def extract_file_header(root: Path, relative_path: Path) -> FileHeader:
    prefix, text_file = read_header_prefix(root / relative_path)
    if not text_file or prefix is None:
        return FileHeader(relative_path, None, None, None, (), (), False, False, ())

    comment_body = extract_comment_body(prefix)
    if comment_body is None:
        return FileHeader(
            relative_path, None, None, None, (), (), False, True,
            ("Missing top-of-file metadata comment.",),
        )

    fields, run_commands, actions = parse_header_fields(clean_comment_lines(comment_body))
    declared_path = fields.get("declared_path")
    purpose = fields.get("purpose")
    last_changed = fields.get("last_changed")
    actual_path = relative_path.as_posix()
    warnings: list[str] = []

    if declared_path is None:
        warnings.append("Missing Path metadata.")
    elif normalize_declared_path(declared_path) != actual_path:
        warnings.append(f"Declared path does not match actual repository path: {declared_path}")
    if purpose is None:
        warnings.append("Missing Purpose metadata.")
    if last_changed is None:
        warnings.append("Missing Last changed metadata.")
    elif not valid_last_changed(last_changed):
        warnings.append("Last changed must begin with a valid YYYY-MM-DD date.")

    return FileHeader(
        relative_path,
        declared_path,
        purpose,
        last_changed,
        tuple(run_commands),
        tuple(actions),
        True,
        True,
        tuple(warnings),
    )


def build_catalog(root: Path, files: list[Path]) -> list[FileHeader]:
    return [extract_file_header(root, relative_path) for relative_path in files]


def write_catalog_entry(file, entry: FileHeader) -> None:
    actual_path = entry.actual_path.as_posix()
    file.write(f"### `{actual_path}`\n\n")

    if not entry.text_file:
        file.write("- Metadata: not inspected because the file is binary, non-UTF-8, or a symlink.\n\n")
        return
    if not entry.header_found:
        file.write("- Metadata: missing top-of-file metadata comment.\n\n")
        return

    if entry.purpose is not None:
        file.write(f"- Purpose: {entry.purpose}\n")
    if entry.last_changed is not None:
        file.write(f"- Last changed: {entry.last_changed}\n")
    if entry.declared_path is not None and normalize_declared_path(entry.declared_path) != actual_path:
        file.write(f"- Declared path: `{entry.declared_path}`\n")
    for command in entry.run_commands:
        file.write(f"- Run: `{command}`\n")
    for action in entry.actions:
        file.write(f"- Action: {action}\n")
    for warning in entry.warnings:
        file.write(f"- Header warning: {warning}\n")
    file.write("\n")


def write_catalog_summary(file, catalog: list[FileHeader]) -> None:
    text_entries = [entry for entry in catalog if entry.text_file]
    header_entries = [entry for entry in text_entries if entry.header_found]
    complete_entries = [entry for entry in header_entries if entry.complete]
    warning_entries = [entry for entry in text_entries if entry.warnings]
    non_text_entries = [entry for entry in catalog if not entry.text_file]

    file.write("### Catalog Validation\n\n")
    file.write(f"- Cataloged files: {len(catalog)}\n")
    file.write(f"- Text/header-capable files: {len(text_entries)}\n")
    file.write(f"- Headers found: {len(header_entries)}\n")
    file.write(f"- Complete valid headers: {len(complete_entries)}\n")
    file.write(f"- Files with header warnings: {len(warning_entries)}\n")
    file.write(f"- Binary/non-UTF-8/symlink files without inspected headers: {len(non_text_entries)}\n\n")

    if warning_entries:
        file.write("#### Header Warnings\n\n")
        for entry in warning_entries:
            for warning in entry.warnings:
                file.write(f"- `{entry.actual_path.as_posix()}`: {warning}\n")
        file.write("\n")


def generate_structure(
    root: Path,
    output_path: Path,
    ignored_dirs: set[str],
    ignored_files: set[str],
    respect_gitignore: bool,
    include_build: bool,
) -> None:
    files, filtering = collect_files(
        root, ignored_dirs, ignored_files, output_path, respect_gitignore, include_build
    )
    tree = build_tree(root, files)
    catalog = build_catalog(root, files)
    stats = collect_stats(root, files)
    branch, revision = git_metadata(root)
    generated_now = datetime.now().astimezone()
    generated_at = generated_now.isoformat(timespec="seconds")
    generated_date = generated_now.date().isoformat()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    try:
        output_relative = output_path.relative_to(root).as_posix()
    except ValueError:
        output_relative = None

    with output_path.open("w", encoding="utf-8") as file:
        if output_relative is not None:
            file.write("<!--\n")
            file.write(f"Path: {output_relative}\n")
            file.write(
                "Purpose: Automatically generated repository structure, file catalog, and metadata validation report.\n"
            )
            file.write(
                f"Last changed: {generated_date}, regenerated repository structure and metadata catalog.\n"
            )
            file.write("Run: python tools/map_codebase.py\n")
            file.write("-->\n\n")

        file.write("# Codebase Structure and Catalog\n\n")
        file.write("> Automatically generated from the current repository. Do not manually edit this file.\n\n")
        file.write("## Repository\n\n")
        file.write(f"- Name: `{root.name}`\n")
        file.write(f"- Generated: `{generated_at}`\n")
        file.write(f"- Filtering: `{filtering}`\n")
        if branch is not None:
            file.write(f"- Git branch: `{branch}`\n")
        if revision is not None:
            file.write(f"- Git revision: `{revision}`\n")
        file.write("\n")

        file.write("## Part 1: Compact File Tree\n\n```text\n")
        file.write("\n".join(tree))
        file.write("\n```\n\n")

        file.write("## Part 2: File Catalog\n\n")
        file.write(
            "Catalog metadata is extracted only from the leading metadata comment in each file. "
            "Responsibilities are never inferred from filenames or implementation contents.\n\n"
        )
        write_catalog_summary(file, catalog)
        file.write("### Catalog Entries\n\n")
        for entry in catalog:
            write_catalog_entry(file, entry)

        file.write("### Repository Statistics\n\n")
        file.write(f"- Files: {stats['file_count']}\n")
        file.write(f"- Directories: {stats['directory_count']}\n")
        file.write(f"- Kotlin/Java source files: {stats['kotlin_java_source_count']}\n")
        file.write(f"- SQLDelight files: {stats['sqldelight_file_count']}\n")
        file.write(f"- Text lines: {stats['total_text_lines']:,}\n\n")

        file.write("#### File Types\n\n")
        extension_counts = stats["extension_counts"]
        assert isinstance(extension_counts, Counter)
        for extension, count in extension_counts.most_common():
            file.write(f"- `{extension}`: {count}\n")

        file.write("\n### Catalog Contract\n\n")
        file.write("Preferred metadata format:\n\n```text\n")
        file.write("Path: repository/relative/path.ext\n")
        file.write("Purpose: One-line responsibility description.\n")
        file.write("Last changed: YYYY-MM-DD, concise description of the latest change.\n")
        file.write("Run: optional command\n")
        file.write("Action: optional operational note\n")
        file.write("```\n\n")
        file.write(
            "The legacy positional format is also accepted when the first comment line is a path "
            "and the second is the purpose. `Run:` and `Action:` may appear more than once. "
            "The declared path is checked against the actual repository-relative path.\n\n"
        )
        file.write(
            "The generated output file is always excluded from its own map. When Git is available, "
            "tracked files plus unignored untracked files are used so local secrets and ignored/generated "
            "content do not pollute the map. If Git is unavailable, the script falls back to a pruned "
            "filesystem scan.\n"
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate a two-part Markdown codebase map with a compact file tree and metadata catalog."
    )
    parser.add_argument(
        "root",
        nargs="?",
        default=None,
        help="Repository root. Defaults to the parent of this script's tools directory.",
    )
    parser.add_argument(
        "-o",
        "--output",
        default="CODEBASE_STRUCTURE.md",
        help="Output Markdown file, relative to the repository root by default.",
    )
    parser.add_argument(
        "--include-build",
        action="store_true",
        help="Include build directories in addition to the normal repository map.",
    )
    parser.add_argument(
        "--no-gitignore",
        action="store_true",
        help="Ignore Git visibility rules and scan the filesystem directly.",
    )
    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve() if args.root is not None else default_repository_root()
    if not root.exists():
        raise SystemExit(f"Repository does not exist: {root}")
    if not root.is_dir():
        raise SystemExit(f"Repository path is not a directory: {root}")

    output_argument = Path(args.output).expanduser()
    output_path = output_argument.resolve() if output_argument.is_absolute() else (root / output_argument).resolve()

    generate_structure(
        root=root,
        output_path=output_path,
        ignored_dirs=set(DEFAULT_IGNORED_DIRS),
        ignored_files=set(DEFAULT_IGNORED_FILES),
        respect_gitignore=not args.no_gitignore,
        include_build=args.include_build,
    )
    print(f"Codebase map generated: {output_path}")


if __name__ == "__main__":
    main()
