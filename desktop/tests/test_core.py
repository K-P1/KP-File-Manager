"""desktop/tests/test_core.py: Desktop pure-logic regression tests; added for the repository reorganization."""

import json
import tempfile
import unittest
from pathlib import Path

from kp_file_manager.renamer import Renamer
from kp_file_manager.sorter import FileSorter


class FileSorterTests(unittest.TestCase):
    def test_classifies_known_and_unknown_extensions(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "categories.json"
            config.write_text(json.dumps({"Images": ["jpg"]}), encoding="utf-8")
            sorter = FileSorter(str(config))

            self.assertEqual("Images", sorter._classify_file(Path("photo.JPG")))
            self.assertEqual("Others", sorter._classify_file(Path("archive.bin")))

    def test_extracts_a_shared_series_title(self):
        sorter = FileSorter()

        self.assertEqual("The Bear", sorter._extract_title("The.Bear.S03E04.1080p.mkv"))


class RenamerTests(unittest.TestCase):
    def test_builds_a_zero_padded_filename(self):
        renamer = Renamer()

        self.assertEqual("Holiday_007.jpg", renamer._build_new_name("Holiday_", 7, 3, ".jpg"))


if __name__ == "__main__":
    unittest.main()
