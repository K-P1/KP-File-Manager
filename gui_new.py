import sys
import os
import logging
from pathlib import Path
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QRadioButton, QButtonGroup,
    QFileDialog, QMessageBox, QCheckBox, QSpinBox, QStackedWidget
)
from PyQt5.QtCore import Qt

from sorter import FileSorter
from renamer import Renamer

LOG_FILE = "operation_logs.txt"

DARK_STYLE = """
QWidget {
    background-color: #2b2b2b;
    color: #fff;
}
QLineEdit, QSpinBox {
    background-color: #3c3c3c;
    color: #fff;
    border: 1px solid #5a5a5a;
}
QPushButton {
    background-color: #3c3c3c;
    color: #fff;
    border: 1px solid #5a5a5a;
    padding: 5px;
}
QPushButton:hover {
    background-color: #4a4a4a;
}
QLabel, QRadioButton, QCheckBox {
    color: #fff;
}
"""

class FileManagerWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("File Manager - Dark Mode")
        self.sorter = FileSorter()
        self.renamer = Renamer()
        self._init_ui()
        self._connect_signals()

    def _init_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout(central)

        # Operation selection (radio buttons)
        op_layout = QHBoxLayout()
        self.radio_sort = QRadioButton("Sort Files")
        self.radio_mass = QRadioButton("Mass Rename")
        self.radio_music = QRadioButton("Music Rename")
        self.radio_keyword = QRadioButton("Keyword Rename")
        self.radio_sort.setChecked(True)
        self.op_group = QButtonGroup()
        for i, btn in enumerate([self.radio_sort, self.radio_mass, self.radio_music, self.radio_keyword]):
            self.op_group.addButton(btn, i)
            op_layout.addWidget(btn)
        main_layout.addLayout(op_layout)

        # Source folder input
        source_layout = QHBoxLayout()
        source_layout.addWidget(QLabel("Source Folder:"))
        self.source_edit = QLineEdit()
        source_layout.addWidget(self.source_edit)
        btn_browse_source = QPushButton("Browse")
        btn_browse_source.clicked.connect(self._browse_source)
        source_layout.addWidget(btn_browse_source)
        main_layout.addLayout(source_layout)

        # Destination folder input (optional)
        dest_layout = QHBoxLayout()
        dest_layout.addWidget(QLabel("Destination Folder (optional):"))
        self.dest_edit = QLineEdit()
        dest_layout.addWidget(self.dest_edit)
        btn_browse_dest = QPushButton("Browse")
        btn_browse_dest.clicked.connect(self._browse_dest)
        dest_layout.addWidget(btn_browse_dest)
        main_layout.addLayout(dest_layout)

        # Operation-specific options container
        self.stacked = QStackedWidget()
        main_layout.addWidget(self.stacked)

        # Sort options
        sort_widget = QWidget()
        sort_layout = QHBoxLayout(sort_widget)
        self.series_mode_check = QCheckBox("Series Mode (Group by Title)")
        sort_layout.addWidget(self.series_mode_check)
        sort_layout.addStretch()
        self.stacked.addWidget(sort_widget)

        # Mass Rename options
        mass_widget = QWidget()
        mass_layout = QHBoxLayout(mass_widget)
        self.mass_ext_edit = QLineEdit("mp4")
        self.mass_prefix_edit = QLineEdit("Renamed_")
        self.mass_start_spin = QSpinBox()
        self.mass_start_spin.setMinimum(1)
        self.mass_start_spin.setValue(1)
        self.mass_pad_spin = QSpinBox()
        self.mass_pad_spin.setMinimum(0)
        self.mass_pad_spin.setValue(2)
        self.scramble_check = QCheckBox("Scramble")
        self.scramble_mult_spin = QSpinBox()
        self.scramble_mult_spin.setRange(1, 10)
        self.scramble_mult_spin.setValue(1)
        self.scramble_mult_spin.setEnabled(False)

        mass_layout.addWidget(QLabel("Ext:"))
        mass_layout.addWidget(self.mass_ext_edit)
        mass_layout.addWidget(QLabel("Prefix:"))
        mass_layout.addWidget(self.mass_prefix_edit)
        mass_layout.addWidget(QLabel("Start:"))
        mass_layout.addWidget(self.mass_start_spin)
        mass_layout.addWidget(QLabel("Pad:"))
        mass_layout.addWidget(self.mass_pad_spin)
        mass_layout.addWidget(self.scramble_check)
        mass_layout.addWidget(QLabel("Scramble Multiplier:"))
        mass_layout.addWidget(self.scramble_mult_spin)
        mass_layout.addStretch()
        self.stacked.addWidget(mass_widget)

        # Music Rename - no options, just info label
        music_widget = QWidget()
        music_layout = QVBoxLayout(music_widget)
        music_layout.addWidget(QLabel("No additional settings required for Music Rename."))
        music_layout.addStretch()
        self.stacked.addWidget(music_widget)

        # Keyword Rename options
        keyword_widget = QWidget()
        keyword_layout = QHBoxLayout(keyword_widget)
        self.key_ext_edit = QLineEdit("mp4")
        self.keyword_edit = QLineEdit()
        self.new_keyword_edit = QLineEdit()
        keyword_layout.addWidget(QLabel("Ext:"))
        keyword_layout.addWidget(self.key_ext_edit)
        keyword_layout.addWidget(QLabel("Keyword:"))
        keyword_layout.addWidget(self.keyword_edit)
        keyword_layout.addWidget(QLabel("New Keyword:"))
        keyword_layout.addWidget(self.new_keyword_edit)
        keyword_layout.addStretch()
        self.stacked.addWidget(keyword_widget)

        # Buttons: Preview, Run, Undo, Quit
        btn_layout = QHBoxLayout()
        self.btn_preview = QPushButton("Preview")
        self.btn_run = QPushButton("Run")
        self.btn_undo = QPushButton("Undo")
        self.btn_quit = QPushButton("Quit")
        btn_layout.addWidget(self.btn_preview)
        btn_layout.addWidget(self.btn_run)
        btn_layout.addWidget(self.btn_undo)
        btn_layout.addWidget(self.btn_quit)
        main_layout.addLayout(btn_layout)

    def _connect_signals(self):
        self.op_group.buttonClicked[int].connect(self._switch_operation)
        self.btn_preview.clicked.connect(self._on_preview)
        self.btn_run.clicked.connect(self._on_run)
        self.btn_undo.clicked.connect(self._on_undo)
        self.btn_quit.clicked.connect(self.close) # type: ignore
        self.scramble_check.toggled.connect(self.scramble_mult_spin.setEnabled)

    def _browse_source(self):
        directory = QFileDialog.getExistingDirectory(self, "Select Source Folder")
        if directory:
            self.source_edit.setText(directory)

    def _browse_dest(self):
        directory = QFileDialog.getExistingDirectory(self, "Select Destination Folder")
        if directory:
            self.dest_edit.setText(directory)

    def _switch_operation(self, index):
        self.stacked.setCurrentIndex(index)

    def _setup_logger(self):
        if os.path.exists(LOG_FILE):
            os.remove(LOG_FILE)
        handler = logging.FileHandler(LOG_FILE, mode='w', encoding='utf-8')
        handler.setLevel(logging.INFO)
        formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")
        handler.setFormatter(formatter)
        root = logging.getLogger()
        root.handlers.clear()
        root.addHandler(handler)
        root.setLevel(logging.INFO)
        return handler

    def _cleanup_logger(self, handler):
        root = logging.getLogger()
        root.removeHandler(handler)
        handler.close()

    def _get_dest_path(self, source, dest):
        return dest if dest else source

    def _on_preview(self):
        source = self.source_edit.text().strip()
        if not source:
            QMessageBox.warning(self, "Warning", "Please select a Source folder.")
            return
        dest = self._get_dest_path(source, self.dest_edit.text().strip())

        op = self.op_group.checkedId()
        handler = self._setup_logger()
        logging.info("=== PREVIEW OPERATION ===")
        try:
            if op == 0:  # Sort
                series_mode = self.series_mode_check.isChecked()
                self.sorter.sort_directory(source, dest, dry_run=True, series_mode=series_mode)
            elif op == 1:  # Mass Rename
                self.renamer.mass_rename(
                    folder=source,
                    file_extension=self.mass_ext_edit.text().strip(),
                    prefix=self.mass_prefix_edit.text().strip(),
                    start_index=self.mass_start_spin.value(),
                    zero_padding=self.mass_pad_spin.value(),
                    dry_run=True,
                    scramble=self.scramble_check.isChecked(),
                    scramble_multiplier=self.scramble_mult_spin.value(),
                )
            elif op == 2:  # Music Rename
                self.renamer.rename_music(folder=source, extensions=["mp3"], dry_run=True)
            elif op == 3:  # Keyword Rename
                keyword = self.keyword_edit.text().strip()
                if not keyword:
                    QMessageBox.warning(self, "Warning", "Please enter a keyword to search for.")
                    self._cleanup_logger(handler)
                    return
                self.renamer.rename_by_keyword(
                    folder=source,
                    file_extension=self.key_ext_edit.text().strip(),
                    keyword=keyword,
                    new_keyword=self.new_keyword_edit.text().strip(),
                    dry_run=True,
                )
        finally:
            self._cleanup_logger(handler)
        QMessageBox.information(self, "Preview Complete", f"Logs written to '{LOG_FILE}'.")

    def _on_run(self):
        source = self.source_edit.text().strip()
        if not source:
            QMessageBox.warning(self, "Warning", "Please select a Source folder.")
            return
        dest = self._get_dest_path(source, self.dest_edit.text().strip())

        op = self.op_group.checkedId()
        handler = self._setup_logger()
        logging.info("=== RUN OPERATION ===")
        try:
            if op == 0:
                series_mode = self.series_mode_check.isChecked()
                self.sorter.sort_directory(source, dest, dry_run=False, series_mode=series_mode)
            elif op == 1:
                self.renamer.mass_rename(
                    folder=source,
                    file_extension=self.mass_ext_edit.text().strip(),
                    prefix=self.mass_prefix_edit.text().strip(),
                    start_index=self.mass_start_spin.value(),
                    zero_padding=self.mass_pad_spin.value(),
                    preserve_timestamps=True,
                    dry_run=False,
                    scramble=self.scramble_check.isChecked(),
                    scramble_multiplier=self.scramble_mult_spin.value(),
                )
            elif op == 2:
                self.renamer.rename_music(folder=source, extensions=["mp3"], preserve_timestamps=True, dry_run=False)
            elif op == 3:
                keyword = self.keyword_edit.text().strip()
                if not keyword:
                    QMessageBox.warning(self, "Warning", "Please enter a keyword to search for.")
                    self._cleanup_logger(handler)
                    return
                self.renamer.rename_by_keyword(
                    folder=source,
                    file_extension=self.key_ext_edit.text().strip(),
                    keyword=keyword,
                    new_keyword=self.new_keyword_edit.text().strip(),
                    preserve_timestamps=True,
                    dry_run=False,
                )
        finally:
            self._cleanup_logger(handler)
        QMessageBox.information(self, "Operation Complete", f"Logs written to '{LOG_FILE}'.")

    def _on_undo(self):
        op = self.op_group.checkedId()
        handler = self._setup_logger()
        logging.info("=== UNDO OPERATION ===")
        success = True
        try:
            if op == 0:
                self.sorter.undo()
                dest_path = self._get_dest_path(self.source_edit.text().strip(), self.dest_edit.text().strip())
                if dest_path:
                    self._remove_empty_dirs(Path(dest_path))
            else:
                self.renamer.undo()
        except Exception as e:
            success = False
            logging.error(f"Undo failed: {e}")
        finally:
            self._cleanup_logger(handler)
        if success:
            QMessageBox.information(self, "Undo Complete", "All operations undone successfully.")
        else:
            QMessageBox.critical(self, "Undo Failed", "Some operations failed. Check logs.")

    def _remove_empty_dirs(self, parent_dir: Path):
        if not parent_dir.exists() or not parent_dir.is_dir():
            return
        for root, dirs, files in os.walk(parent_dir, topdown=False):
            if not files and not dirs:
                try:
                    os.rmdir(root)
                    logging.info(f"Removed empty folder: {root}")
                except Exception as e:
                    logging.warning(f"Could not remove {root}: {e}")

def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
    app = QApplication(sys.argv)
    app.setStyleSheet(DARK_STYLE)
    window = FileManagerWindow()
    window.resize(700, 300)
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
