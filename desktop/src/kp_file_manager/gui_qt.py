"""desktop/src/kp_file_manager/gui_qt.py: Primary PyQt file-operation UI with explicit workflow and status feedback; last updated for clearer guidance and progress visibility."""

import sys
import os
import logging
from pathlib import Path
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QRadioButton, QButtonGroup,
    QFileDialog, QMessageBox, QCheckBox, QSpinBox, QStackedWidget, QProgressBar, QGroupBox
)
from PyQt5.QtCore import Qt

from .paths import LOG_FILE, ensure_runtime_dir
from .renamer import Renamer
from .sorter import FileSorter


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
        self.setWindowTitle("KP File Manager")
        self.sorter = FileSorter()
        self.renamer = Renamer()
        self._init_ui()
        self._connect_signals()

    def _init_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout(central)

        workflow_label = QLabel("1. Select an operation, 2. choose folders, 3. set options, 4. review changes, then apply them.")
        workflow_label.setWordWrap(True)
        main_layout.addWidget(workflow_label)

        operation_group = QGroupBox("1. Operation")
        operation_layout = QVBoxLayout(operation_group)
        operation_layout.addWidget(QLabel("Choose what to do. The options below update for the selected operation."))
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
        operation_layout.addLayout(op_layout)
        self.operation_help = QLabel()
        self.operation_help.setWordWrap(True)
        operation_layout.addWidget(self.operation_help)
        main_layout.addWidget(operation_group)

        folders_group = QGroupBox("2. Folders")
        folders_layout = QVBoxLayout(folders_group)
        source_layout = QHBoxLayout()
        source_layout.addWidget(QLabel("Source folder (files to process):"))
        self.source_edit = QLineEdit()
        self.source_edit.setPlaceholderText("Choose the folder containing the files you want to process")
        self.source_edit.setToolTip("Full selected folder path")
        source_layout.addWidget(self.source_edit)
        btn_browse_source = QPushButton("Browse")
        btn_browse_source.clicked.connect(self._browse_source)
        source_layout.addWidget(btn_browse_source)
        folders_layout.addLayout(source_layout)

        dest_layout = QHBoxLayout()
        dest_layout.addWidget(QLabel("Destination folder (optional for sorting):"))
        self.dest_edit = QLineEdit()
        self.dest_edit.setPlaceholderText("Leave empty to create category folders inside the source folder")
        self.dest_edit.setToolTip("Full selected destination folder path")
        dest_layout.addWidget(self.dest_edit)
        btn_browse_dest = QPushButton("Browse")
        btn_browse_dest.clicked.connect(self._browse_dest)
        dest_layout.addWidget(btn_browse_dest)
        folders_layout.addLayout(dest_layout)
        main_layout.addWidget(folders_group)

        settings_group = QGroupBox("3. Operation settings")
        settings_layout = QVBoxLayout(settings_group)
        self.settings_help = QLabel()
        self.settings_help.setWordWrap(True)
        settings_layout.addWidget(self.settings_help)
        self.stacked = QStackedWidget()
        settings_layout.addWidget(self.stacked)
        main_layout.addWidget(settings_group)

        # Sort options
        sort_widget = QWidget()
        sort_layout = QHBoxLayout(sort_widget)
        self.series_mode_check = QCheckBox("Group matching series titles")
        self.series_mode_check.setToolTip("Moves files with the same normalized title into one folder instead of file-type categories.")
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

        mass_layout.addWidget(QLabel("File type:"))
        mass_layout.addWidget(self.mass_ext_edit)
        mass_layout.addWidget(QLabel("Prefix:"))
        mass_layout.addWidget(self.mass_prefix_edit)
        mass_layout.addWidget(QLabel("Start:"))
        mass_layout.addWidget(self.mass_start_spin)
        mass_layout.addWidget(QLabel("Digits:"))
        mass_layout.addWidget(self.mass_pad_spin)
        mass_layout.addWidget(self.scramble_check)
        mass_layout.addWidget(QLabel("Scramble Multiplier:"))
        mass_layout.addWidget(self.scramble_mult_spin)
        mass_layout.addStretch()
        self.stacked.addWidget(mass_widget)

        # Music Rename - no options, just info label
        music_widget = QWidget()
        music_layout = QVBoxLayout(music_widget)
        music_layout.addWidget(QLabel("Uses available MP3 track metadata. Files without complete tags receive a cleaned filename."))
        music_layout.addStretch()
        self.stacked.addWidget(music_widget)

        # Keyword Rename options
        keyword_widget = QWidget()
        keyword_layout = QHBoxLayout(keyword_widget)
        self.key_ext_edit = QLineEdit("mp4")
        self.keyword_edit = QLineEdit()
        self.new_keyword_edit = QLineEdit()
        keyword_layout.addWidget(QLabel("File type:"))
        keyword_layout.addWidget(self.key_ext_edit)
        keyword_layout.addWidget(QLabel("Find text:"))
        keyword_layout.addWidget(self.keyword_edit)
        keyword_layout.addWidget(QLabel("Replace with:"))
        keyword_layout.addWidget(self.new_keyword_edit)
        keyword_layout.addStretch()
        self.stacked.addWidget(keyword_widget)

        self.progress = QProgressBar()
        self.progress.setRange(0, 1)
        self.progress.setValue(0)
        self.progress.setTextVisible(False)
        self.progress.hide()
        main_layout.addWidget(self.progress)
        self.status_label = QLabel("Choose an operation and a source folder. Review changes before applying them.")
        self.status_label.setWordWrap(True)
        main_layout.addWidget(self.status_label)

        # Buttons: Preview, Run, Undo, Quit
        btn_layout = QHBoxLayout()
        self.btn_preview = QPushButton("Review changes")
        self.btn_preview.setToolTip("Creates a log of proposed changes without changing any files.")
        self.btn_run = QPushButton("Apply changes")
        self.btn_run.setToolTip("Applies the reviewed operation to the selected folder.")
        self.btn_undo = QPushButton("Undo last change")
        self.btn_undo.setToolTip("Reverses changes made during this application session when possible.")
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
        self.source_edit.textChanged.connect(self._update_ready_status)
        self.dest_edit.textChanged.connect(self._update_ready_status)
        self._switch_operation(0)

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
        descriptions = [
            ("Sort files into type-based folders. Use the destination folder only when categories should be created elsewhere.", "Choose a destination folder only when categories should be created outside the source folder."),
            ("Rename matching files using a prefix and number. Use * as the file type to rename every file.", "Set the file type, prefix, starting number, and number of digits."),
            ("Rename MP3 files from their track metadata, with a cleaned filename as a fallback.", "No additional settings are required."),
            ("Find text in matching filenames and replace it. Leave Replace with empty to remove the text.", "Set a file type, the required text to find, and its replacement."),
        ]
        self.operation_help.setText(descriptions[index][0])
        self.settings_help.setText(descriptions[index][1])
        self._update_ready_status()

    def _update_ready_status(self):
        if not self.source_edit.text().strip():
            self.status_label.setText("Select a source folder to enable a review or file operation.")
        elif self.op_group.checkedId() == 0 and not self.dest_edit.text().strip():
            self.status_label.setText("Ready to review. Sorting will create category folders inside the source folder.")
        else:
            self.status_label.setText("Ready to review. Review changes first; apply changes only when the plan is correct.")

    def _set_working(self, message):
        self.progress.setRange(0, 0)
        self.progress.show()
        self.status_label.setText(message)
        for button in (self.btn_preview, self.btn_run, self.btn_undo, self.btn_quit):
            button.setEnabled(False)
        QApplication.processEvents()

    def _finish_working(self, message):
        self.progress.hide()
        for button in (self.btn_preview, self.btn_run, self.btn_undo, self.btn_quit):
            button.setEnabled(True)
        self.status_label.setText(message)

    def _setup_logger(self):
        ensure_runtime_dir()
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
        self._set_working("Reviewing the selected folder and preparing the change list…")
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
            self._finish_working("Review complete. Open the log to inspect every proposed change before applying it.")
        QMessageBox.information(self, "Preview Complete", f"Logs written to '{LOG_FILE}'.")

    def _on_run(self):
        source = self.source_edit.text().strip()
        if not source:
            QMessageBox.warning(self, "Warning", "Please select a Source folder.")
            return
        dest = self._get_dest_path(source, self.dest_edit.text().strip())

        op = self.op_group.checkedId()
        self._set_working("Applying changes to the selected folder. Keep this window open until the operation is complete…")
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
            self._finish_working("Operation complete. Use Undo last change in this session if you need to reverse it.")
        QMessageBox.information(self, "Operation Complete", f"Logs written to '{LOG_FILE}'.")

    def _on_undo(self):
        op = self.op_group.checkedId()
        self._set_working("Restoring the last operation…")
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
            self._finish_working("Undo complete. The last operation was restored where possible.")
            QMessageBox.information(self, "Undo Complete", "All operations undone successfully.")
        else:
            self._finish_working("Undo finished with errors. Review the log for files that could not be restored.")
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
    window.resize(880, 520)
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
