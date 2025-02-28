#gui_pyqt.py
import sys
import os
import logging
from pathlib import Path

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
    QLabel, QLineEdit, QPushButton, QRadioButton, QButtonGroup, QFileDialog,
    QMessageBox, QCheckBox, QSpinBox, QStackedWidget
)
from PyQt5.QtCore import Qt

from sorter import FileSorter
from renamer import Renamer

LOG_FILE = "operation_logs.txt"

# Define a dark style sheet
DARK_STYLE = """
QWidget {
    background-color: #2b2b2b;
    color: #ffffff;
}
QLineEdit, QSpinBox, QComboBox, QTextEdit {
    background-color: #3c3c3c;
    color: #ffffff;
    border: 1px solid #5a5a5a;
}
QPushButton {
    background-color: #3c3c3c;
    color: #ffffff;
    border: 1px solid #5a5a5a;
    padding: 5px;
}
QPushButton:hover {
    background-color: #4a4a4a;
}
QLabel {
    color: #ffffff;
}
QRadioButton, QCheckBox {
    color: #ffffff;
}
QGroupBox {
    border: 1px solid #5a5a5a;
    margin-top: 6px;
}
QGroupBox::title {
    subcontrol-origin: margin;
    left: 7px;
    padding: 0 3px 0 3px;
}
"""

class FileManagerWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("File Manager - Dark Mode")
        self.sorter = FileSorter()
        self.renamer = Renamer()

        self._setup_ui()
        self._setup_connections()

    def _setup_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout()
        central.setLayout(main_layout)

        # Operation selection
        op_layout = QHBoxLayout()
        self.radio_sort = QRadioButton("Sort Files")
        self.radio_mass = QRadioButton("Mass Rename")
        self.radio_music = QRadioButton("Music Rename")
        self.radio_keyword = QRadioButton("Keyword Rename")
        self.radio_sort.setChecked(True)

        self.op_group = QButtonGroup()
        self.op_group.addButton(self.radio_sort, 0)
        self.op_group.addButton(self.radio_mass, 1)
        self.op_group.addButton(self.radio_music, 2)
        self.op_group.addButton(self.radio_keyword, 3)

        op_layout.addWidget(self.radio_sort)
        op_layout.addWidget(self.radio_mass)
        op_layout.addWidget(self.radio_music)
        op_layout.addWidget(self.radio_keyword)
        main_layout.addLayout(op_layout)

        # Source folder (common)
        source_layout = QHBoxLayout()
        source_layout.addWidget(QLabel("Source Folder:"))
        self.source_edit = QLineEdit()
        source_layout.addWidget(self.source_edit)
        self.btn_browse_source = QPushButton("Browse")
        source_layout.addWidget(self.btn_browse_source)
        main_layout.addLayout(source_layout)

        # Stacked widget for operation-specific fields
        self.stacked = QStackedWidget()
        main_layout.addWidget(self.stacked)

        # Page 0: Sort (requires Destination folder)
        sort_page = QWidget()
        sort_layout = QHBoxLayout()
        sort_page.setLayout(sort_layout)
        sort_layout.addWidget(QLabel("Destination Folder:"))
        self.dest_edit = QLineEdit()
        sort_layout.addWidget(self.dest_edit)
        self.btn_browse_dest = QPushButton("Browse")
        sort_layout.addWidget(self.btn_browse_dest)
        self.stacked.addWidget(sort_page)

        # Page 1: Mass Rename fields
        mass_page = QWidget()
        mass_layout = QGridLayout()
        mass_page.setLayout(mass_layout)
        mass_layout.addWidget(QLabel("File Extension:"), 0, 0)
        self.mass_ext_edit = QLineEdit("mp4")
        mass_layout.addWidget(self.mass_ext_edit, 0, 1)
        mass_layout.addWidget(QLabel("Prefix:"), 0, 2)
        self.mass_prefix_edit = QLineEdit("Renamed_")
        mass_layout.addWidget(self.mass_prefix_edit, 0, 3)
        mass_layout.addWidget(QLabel("Start Index:"), 1, 0)
        self.mass_start_spin = QSpinBox()
        self.mass_start_spin.setMinimum(1)
        self.mass_start_spin.setValue(1)
        mass_layout.addWidget(self.mass_start_spin, 1, 1)
        mass_layout.addWidget(QLabel("Zero Padding:"), 1, 2)
        self.mass_pad_spin = QSpinBox()
        self.mass_pad_spin.setMinimum(0)
        self.mass_pad_spin.setValue(2)
        mass_layout.addWidget(self.mass_pad_spin, 1, 3)
        self.scramble_check = QCheckBox("Scramble")
        mass_layout.addWidget(self.scramble_check, 2, 0)
        mass_layout.addWidget(QLabel("Scramble Multiplier (1-10):"), 2, 1)
        self.scramble_mult_spin = QSpinBox()
        self.scramble_mult_spin.setRange(1, 10)
        self.scramble_mult_spin.setValue(1)
        mass_layout.addWidget(self.scramble_mult_spin, 2, 2)
        self.stacked.addWidget(mass_page)

        # Page 2: Music Rename (no extra fields)
        music_page = QWidget()
        music_layout = QVBoxLayout()
        music_page.setLayout(music_layout)
        music_layout.addWidget(QLabel("No additional settings required for Music Rename."))
        self.stacked.addWidget(music_page)

        # Page 3: Keyword Rename fields
        keyword_page = QWidget()
        keyword_layout = QGridLayout()
        keyword_page.setLayout(keyword_layout)
        keyword_layout.addWidget(QLabel("File Extension:"), 0, 0)
        self.key_ext_edit = QLineEdit("mp4")
        keyword_layout.addWidget(self.key_ext_edit, 0, 1)
        keyword_layout.addWidget(QLabel("Keyword:"), 1, 0)
        self.keyword_edit = QLineEdit()
        keyword_layout.addWidget(self.keyword_edit, 1, 1)
        keyword_layout.addWidget(QLabel("New Keyword:"), 1, 2)
        self.new_keyword_edit = QLineEdit()
        keyword_layout.addWidget(self.new_keyword_edit, 1, 3)
        self.stacked.addWidget(keyword_page)

        # Action buttons
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

    def _setup_connections(self):
        self.op_group.buttonClicked[int].connect(self._switch_operation)
        self.btn_browse_source.clicked.connect(self._browse_source)
        self.btn_browse_dest.clicked.connect(self._browse_dest)
        self.btn_preview.clicked.connect(self.on_preview)
        self.btn_run.clicked.connect(self.on_run)
        self.btn_undo.clicked.connect(self.on_undo)
        self.btn_quit.clicked.connect(self.close)
        # Enable/disable scramble multiplier based on scramble checkbox
        self.scramble_check.toggled.connect(
            lambda checked: self.scramble_mult_spin.setEnabled(checked)
        )
        # Set initial state
        self.scramble_mult_spin.setEnabled(self.scramble_check.isChecked())

    def _switch_operation(self, index):
        self.stacked.setCurrentIndex(index)

    def _browse_source(self):
        directory = QFileDialog.getExistingDirectory(self, "Select Source Folder")
        if directory:
            self.source_edit.setText(directory)

    def _browse_dest(self):
        directory = QFileDialog.getExistingDirectory(self, "Select Destination Folder")
        if directory:
            self.dest_edit.setText(directory)

    def _setup_file_logger(self):
        if os.path.exists(LOG_FILE):
            os.remove(LOG_FILE)
        file_handler = logging.FileHandler(LOG_FILE, mode='w', encoding='utf-8')
        file_handler.setLevel(logging.INFO)
        formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")
        file_handler.setFormatter(formatter)
        root_logger = logging.getLogger()
        root_logger.addHandler(file_handler)
        return file_handler

    def _cleanup_file_logger(self, file_handler):
        root_logger = logging.getLogger()
        root_logger.removeHandler(file_handler)
        file_handler.close()

    def on_preview(self):
        source = self.source_edit.text().strip()
        if not source:
            QMessageBox.warning(self, "Warning", "Please select a Source folder.")
            return

        op_index = self.op_group.checkedId()
        fh = self._setup_file_logger()
        logging.info(f"=== PREVIEW OPERATION ===")
        try:
            if op_index == 0:  # Sort Files
                dest = self.dest_edit.text().strip()
                if not dest:
                    QMessageBox.warning(self, "Warning", "Please select a Destination folder for sorting.")
                    self._cleanup_file_logger(fh)
                    return
                self.sorter.sort_directory(source, dest, dry_run=True)

            elif op_index == 1:  # Mass Rename
                ext = self.mass_ext_edit.text().strip()
                prefix = self.mass_prefix_edit.text().strip()
                start_index = self.mass_start_spin.value()
                zero_pad = self.mass_pad_spin.value()
                scramble = self.scramble_check.isChecked()
                scramble_mult = self.scramble_mult_spin.value()
                logging.info(
                    f"Preview Mass Rename: ext='{ext}', prefix='{prefix}', start_index={start_index}, "
                    f"zero_pad={zero_pad}, scramble={scramble}, scramble_multiplier={scramble_mult}"
                )
                self.renamer.mass_rename(
                    folder=source,
                    file_extension=ext,
                    prefix=prefix,
                    start_index=start_index,
                    zero_padding=zero_pad,
                    dry_run=True,
                    scramble=scramble,
                    scramble_multiplier=scramble_mult
                )

            elif op_index == 2:  # Music Rename
                self.renamer.rename_music(folder=source, extensions=["mp3"], dry_run=True)

            elif op_index == 3:  # Keyword Rename
                ext = self.key_ext_edit.text().strip()
                keyword = self.keyword_edit.text().strip()
                new_keyword = self.new_keyword_edit.text().strip()
                if not keyword:
                    QMessageBox.warning(self, "Warning", "Please enter the keyword to search for.")
                    self._cleanup_file_logger(fh)
                    return
                logging.info(
                    f"Preview Keyword Rename: ext='{ext}', keyword='{keyword}', new_keyword='{new_keyword}'"
                )
                self.renamer.rename_by_keyword(
                    folder=source,
                    file_extension=ext,
                    keyword=keyword,
                    new_keyword=new_keyword,
                    dry_run=True
                )
        finally:
            self._cleanup_file_logger(fh)
        QMessageBox.information(self, "Preview Complete", f"Logs written to '{LOG_FILE}'.")

    def on_run(self):
        source = self.source_edit.text().strip()
        if not source:
            QMessageBox.warning(self, "Warning", "Please select a Source folder.")
            return

        op_index = self.op_group.checkedId()
        fh = self._setup_file_logger()
        logging.info(f"=== RUN OPERATION ===")
        try:
            if op_index == 0:  # Sort Files
                dest = self.dest_edit.text().strip()
                if not dest:
                    QMessageBox.warning(self, "Warning", "Please select a Destination folder for sorting.")
                    self._cleanup_file_logger(fh)
                    return
                self.sorter.sort_directory(source, dest, dry_run=False)

            elif op_index == 1:  # Mass Rename
                ext = self.mass_ext_edit.text().strip()
                prefix = self.mass_prefix_edit.text().strip()
                start_index = self.mass_start_spin.value()
                zero_pad = self.mass_pad_spin.value()
                scramble = self.scramble_check.isChecked()
                scramble_mult = self.scramble_mult_spin.value()
                logging.info(
                    f"Run Mass Rename: ext='{ext}', prefix='{prefix}', start_index={start_index}, "
                    f"zero_pad={zero_pad}, scramble={scramble}, scramble_multiplier={scramble_mult}"
                )
                self.renamer.mass_rename(
                    folder=source,
                    file_extension=ext,
                    prefix=prefix,
                    start_index=start_index,
                    zero_padding=zero_pad,
                    preserve_timestamps=True,
                    dry_run=False,
                    scramble=scramble,
                    scramble_multiplier=scramble_mult
                )

            elif op_index == 2:  # Music Rename
                self.renamer.rename_music(folder=source, extensions=["mp3"], preserve_timestamps=True, dry_run=False)

            elif op_index == 3:  # Keyword Rename
                ext = self.key_ext_edit.text().strip()
                keyword = self.keyword_edit.text().strip()
                new_keyword = self.new_keyword_edit.text().strip()
                if not keyword:
                    QMessageBox.warning(self, "Warning", "Please enter the keyword to search for.")
                    self._cleanup_file_logger(fh)
                    return
                logging.info(
                    f"Run Keyword Rename: ext='{ext}', keyword='{keyword}', new_keyword='{new_keyword}'"
                )
                self.renamer.rename_by_keyword(
                    folder=source,
                    file_extension=ext,
                    keyword=keyword,
                    new_keyword=new_keyword,
                    preserve_timestamps=True,
                    dry_run=False
                )
        finally:
            self._cleanup_file_logger(fh)
        QMessageBox.information(self, "Operation Complete", f"Logs have been written to '{LOG_FILE}'.")

    def on_undo(self):
        op_index = self.op_group.checkedId()
        fh = self._setup_file_logger()
        logging.info(f"=== UNDO OPERATION ===")
        success = True
        try:
            if op_index == 0:
                self.sorter.undo()
                dest = self.dest_edit.text().strip()
                if dest:
                    self._remove_empty_dirs(Path(dest))
            else:
                self.renamer.undo()
        except Exception as e:
            success = False
            logging.error(f"Undo failed: {e}")
        finally:
            self._cleanup_file_logger(fh)
        if success:
            QMessageBox.information(self, "Undo Complete", "All operations have been undone successfully.")
        else:
            QMessageBox.critical(self, "Undo Failed", "Some operations could not be undone. Check logs.")

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
    # Apply the dark style sheet
    app.setStyleSheet(DARK_STYLE)
    window = FileManagerWindow()
    window.resize(800, 400)
    window.show()
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
