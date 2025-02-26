# main.py
import argparse
import logging
import sys

from sorter import FileSorter
from renamer import Renamer
# from gui import main as run_gui  # If you want to launch the GUI from CLI

def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

    parser = argparse.ArgumentParser(description="File Manager - Sort and Rename.")
    parser.add_argument("--sort", action="store_true", help="Sort files and folders.")
    parser.add_argument("--mass-rename", action="store_true", help="Mass rename files.")
    parser.add_argument("--music-rename", action="store_true", help="Simplify music filenames.")
    parser.add_argument("--source", type=str, default=".", help="Source directory.")
    parser.add_argument("--dest", type=str, default="sorted", help="Destination directory (for sorting).")
    parser.add_argument("--dry-run", action="store_true", help="Perform a dry run (no changes).")
    parser.add_argument("--undo", action="store_true", help="Undo previous operations.")
    # Add more CLI args as needed (prefix, extension, etc.)

    args = parser.parse_args()

    sorter = FileSorter()
    renamer = Renamer()

    # Handle UNDO first if requested
    if args.undo:
        # Undo sorting
        sorter.undo()
        # Undo renaming
        renamer.undo()
        sys.exit(0)

    # Sorting
    if args.sort:
        sorter.sort_directory(args.source, args.dest, dry_run=args.dry_run)

    # Mass rename example
    if args.mass_rename:
        # For demonstration, we’ll rename .jpg files with prefix "CLI_"
        renamer.mass_rename(
            folder=args.source,
            file_extension="jpg",
            prefix="CLI_",
            start_index=1,
            zero_padding=3,
            preserve_timestamps=True,
            dry_run=args.dry_run
        )

    # Music rename example
    if args.music_rename:
        renamer.rename_music(
            folder=args.source,
            extensions=["mp3"],
            dry_run=args.dry_run,
            preserve_timestamps=True
        )

    logging.info("All requested operations completed.")

if __name__ == "__main__":
    main()
