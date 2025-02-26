import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import os
import logging
from pathlib import Path
import random

from sorter import FileSorter
from renamer import Renamer

class FileManagerGUI:
    """
    Radio buttons:
      - Sort
      - Mass Rename
      - Music Rename

    Mass Rename fields:
      - Extension
      - Prefix
      - Start Index
      - Zero Padding
      - Scramble (checkbox)
      - Scramble Multiplier (1..10)

    Only one operation can be chosen at a time.
    """

    def __init__(self, master):
        self.master = master
        self.master.title("File Manager")

        self.sorter = FileSorter()
        self.renamer = Renamer()

        # Radio button for the operation
        self.operation_var = tk.StringVar(value="sort")  # "sort", "mass_rename", "music_rename"

        # Common variables
        self.source_var = tk.StringVar()
        self.dest_var = tk.StringVar()

        # Mass rename variables
        self.mass_ext_var = tk.StringVar(value="mp4")
        self.mass_prefix_var = tk.StringVar(value="Renamed_")
        self.mass_start_var = tk.IntVar(value=1)
        self.mass_padding_var = tk.IntVar(value=2)

        # Scramble fields
        self.scramble_var = tk.BooleanVar(value=False)
        self.scramble_multiplier_var = tk.IntVar(value=1)  # 1..10

        self.log_file = "operation_logs.txt"

        self._create_widgets()
        self._layout_widgets()
        self._update_operation_fields()

    # --------------------------------------------------------------------------
    # CREATE WIDGETS
    # --------------------------------------------------------------------------
    def _create_widgets(self):
        # Radio Buttons for choosing operation
        self.rbtn_sort = ttk.Radiobutton(
            self.master, text="Sort Files", variable=self.operation_var,
            value="sort", command=self._update_operation_fields
        )
        self.rbtn_mass_rename = ttk.Radiobutton(
            self.master, text="Mass Rename", variable=self.operation_var,
            value="mass_rename", command=self._update_operation_fields
        )
        self.rbtn_music_rename = ttk.Radiobutton(
            self.master, text="Music Rename", variable=self.operation_var,
            value="music_rename", command=self._update_operation_fields
        )

        # Row 1: Source folder
        self.lbl_source = ttk.Label(self.master, text="Source Folder:")
        self.entry_source = ttk.Entry(self.master, textvariable=self.source_var, width=50)
        self.btn_browse_source = ttk.Button(self.master, text="Browse", command=self._browse_source)

        # Row 2: Destination folder (used only for sorting)
        self.lbl_dest = ttk.Label(self.master, text="Destination Folder:")
        self.entry_dest = ttk.Entry(self.master, textvariable=self.dest_var, width=50)
        self.btn_browse_dest = ttk.Button(self.master, text="Browse", command=self._browse_dest)

        # Mass Rename Fields
        self.lbl_mass_ext = ttk.Label(self.master, text="File Extension:")
        self.entry_mass_ext = ttk.Entry(self.master, textvariable=self.mass_ext_var, width=10)

        self.lbl_mass_prefix = ttk.Label(self.master, text="Prefix:")
        self.entry_mass_prefix = ttk.Entry(self.master, textvariable=self.mass_prefix_var, width=10)

        self.lbl_mass_start = ttk.Label(self.master, text="Start Index:")
        self.entry_mass_start = ttk.Entry(self.master, textvariable=self.mass_start_var, width=5)

        self.lbl_mass_pad = ttk.Label(self.master, text="Zero Padding:")
        self.entry_mass_pad = ttk.Entry(self.master, textvariable=self.mass_padding_var, width=5)

        # Scramble
        self.chk_scramble = ttk.Checkbutton(
            self.master, text="Scramble", variable=self.scramble_var,
            command=self._toggle_scramble_fields
        )
        self.lbl_scramble_mult = ttk.Label(self.master, text="Scramble Multiplier (1..10):")
        self.entry_scramble_mult = ttk.Entry(self.master, textvariable=self.scramble_multiplier_var, width=5)

        # Action Buttons
        self.btn_preview = ttk.Button(self.master, text="Preview", command=self.on_preview)
        self.btn_run = ttk.Button(self.master, text="Run", command=self.on_run)
        self.btn_undo = ttk.Button(self.master, text="Undo", command=self.on_undo)
        self.btn_quit = ttk.Button(self.master, text="Quit", command=self.master.quit)

    def _layout_widgets(self):
        # Row 0: Radio Buttons (Sort, Mass Rename, Music Rename)
        self.rbtn_sort.grid(row=0, column=0, padx=5, pady=5, sticky=tk.W)
        self.rbtn_mass_rename.grid(row=0, column=1, padx=5, pady=5, sticky=tk.W)
        self.rbtn_music_rename.grid(row=0, column=2, padx=5, pady=5, sticky=tk.W)

        # Row 1: Source
        self.lbl_source.grid(row=1, column=0, padx=5, pady=5, sticky=tk.E)
        self.entry_source.grid(row=1, column=1, padx=5, pady=5)
        self.btn_browse_source.grid(row=1, column=2, padx=5, pady=5, sticky=tk.W)

        # Row 2: Destination
        self.lbl_dest.grid(row=2, column=0, padx=5, pady=5, sticky=tk.E)
        self.entry_dest.grid(row=2, column=1, padx=5, pady=5)
        self.btn_browse_dest.grid(row=2, column=2, padx=5, pady=5, sticky=tk.W)

        # Row 3: Mass Rename Fields
        self.lbl_mass_ext.grid(row=3, column=0, padx=5, pady=5, sticky=tk.E)
        self.entry_mass_ext.grid(row=3, column=1, padx=5, pady=5, sticky=tk.W)

        self.lbl_mass_prefix.grid(row=3, column=2, padx=5, pady=5, sticky=tk.E)
        self.entry_mass_prefix.grid(row=3, column=3, padx=5, pady=5, sticky=tk.W)

        # Row 4: More Mass Rename Fields
        self.lbl_mass_start.grid(row=4, column=0, padx=5, pady=5, sticky=tk.E)
        self.entry_mass_start.grid(row=4, column=1, padx=5, pady=5, sticky=tk.W)

        self.lbl_mass_pad.grid(row=4, column=2, padx=5, pady=5, sticky=tk.E)
        self.entry_mass_pad.grid(row=4, column=3, padx=5, pady=5, sticky=tk.W)

        # Row 5: Scramble
        self.chk_scramble.grid(row=5, column=0, padx=5, pady=5, sticky=tk.W)
        self.lbl_scramble_mult.grid(row=5, column=1, padx=5, pady=5, sticky=tk.E)
        self.entry_scramble_mult.grid(row=5, column=2, padx=5, pady=5, sticky=tk.W)

        # Row 6: Buttons
        self.btn_preview.grid(row=6, column=0, padx=5, pady=10)
        self.btn_run.grid(row=6, column=1, padx=5, pady=10)
        self.btn_undo.grid(row=6, column=2, padx=5, pady=10)
        self.btn_quit.grid(row=6, column=3, padx=5, pady=10)

    # --------------------------------------------------------------------------
    # TOGGLE FIELDS
    # --------------------------------------------------------------------------
    def _update_operation_fields(self):
        """
        Enable/disable widgets depending on operation_var:
         - "sort" => Destination is enabled, mass rename fields disabled
         - "mass_rename" => Show mass rename fields, hide destination
         - "music_rename" => Hide both
        """
        op = self.operation_var.get()

        if op == "sort":
            # Enable dest
            self._enable_widget(self.lbl_dest)
            self._enable_widget(self.entry_dest)
            self._enable_widget(self.btn_browse_dest)

            # Disable mass rename
            self._disable_widget(self.lbl_mass_ext)
            self._disable_widget(self.entry_mass_ext)
            self._disable_widget(self.lbl_mass_prefix)
            self._disable_widget(self.entry_mass_prefix)
            self._disable_widget(self.lbl_mass_start)
            self._disable_widget(self.entry_mass_start)
            self._disable_widget(self.lbl_mass_pad)
            self._disable_widget(self.entry_mass_pad)
            self._disable_widget(self.chk_scramble)
            self._disable_widget(self.lbl_scramble_mult)
            self._disable_widget(self.entry_scramble_mult)

        elif op == "mass_rename":
            # Disable dest
            self._disable_widget(self.lbl_dest)
            self._disable_widget(self.entry_dest)
            self._disable_widget(self.btn_browse_dest)

            # Enable mass rename
            self._enable_widget(self.lbl_mass_ext)
            self._enable_widget(self.entry_mass_ext)
            self._enable_widget(self.lbl_mass_prefix)
            self._enable_widget(self.entry_mass_prefix)
            self._enable_widget(self.lbl_mass_start)
            self._enable_widget(self.entry_mass_start)
            self._enable_widget(self.lbl_mass_pad)
            self._enable_widget(self.entry_mass_pad)
            self._enable_widget(self.chk_scramble)

            # Then toggle the scramble multiplier field
            self._toggle_scramble_fields()

        elif op == "music_rename":
            # Disable dest
            self._disable_widget(self.lbl_dest)
            self._disable_widget(self.entry_dest)
            self._disable_widget(self.btn_browse_dest)

            # Disable mass rename
            self._disable_widget(self.lbl_mass_ext)
            self._disable_widget(self.entry_mass_ext)
            self._disable_widget(self.lbl_mass_prefix)
            self._disable_widget(self.entry_mass_prefix)
            self._disable_widget(self.lbl_mass_start)
            self._disable_widget(self.entry_mass_start)
            self._disable_widget(self.lbl_mass_pad)
            self._disable_widget(self.entry_mass_pad)
            self._disable_widget(self.chk_scramble)
            self._disable_widget(self.lbl_scramble_mult)
            self._disable_widget(self.entry_scramble_mult)

    def _toggle_scramble_fields(self):
        """
        If scramble_var is True, enable the scramble multiplier field, else disable it.
        """
        if self.scramble_var.get():
            self._enable_widget(self.lbl_scramble_mult)
            self._enable_widget(self.entry_scramble_mult)
        else:
            self._disable_widget(self.lbl_scramble_mult)
            self._disable_widget(self.entry_scramble_mult)

    def _enable_widget(self, widget):
        try:
            widget.config(state="normal")
        except tk.TclError:
            pass

    def _disable_widget(self, widget):
        try:
            widget.config(state="disabled")
        except tk.TclError:
            pass

    # --------------------------------------------------------------------------
    # BROWSE
    # --------------------------------------------------------------------------
    def _browse_source(self):
        directory = filedialog.askdirectory()
        if directory:
            self.source_var.set(directory)

    def _browse_dest(self):
        directory = filedialog.askdirectory()
        if directory:
            self.dest_var.set(directory)

    # --------------------------------------------------------------------------
    # LOGGING
    # --------------------------------------------------------------------------
    def _setup_file_logger(self):
        if os.path.exists(self.log_file):
            os.remove(self.log_file)

        file_handler = logging.FileHandler(self.log_file, mode='w', encoding='utf-8')
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

    # --------------------------------------------------------------------------
    # PREVIEW, RUN, UNDO
    # --------------------------------------------------------------------------
    def on_preview(self):
        source = self.source_var.get()
        if not source:
            messagebox.showwarning("Warning", "Please select a Source folder.")
            return

        op = self.operation_var.get()
        fh = self._setup_file_logger()
        logging.info(f"=== PREVIEW: {op.upper()} ===")

        if op == "sort":
            dest = self.dest_var.get()
            if not dest:
                messagebox.showwarning("Warning", "Please select a Destination folder for sorting.")
                self._cleanup_file_logger(fh)
                return
            self.sorter.sort_directory(source, dest, dry_run=True)

        elif op == "mass_rename":
            ext = self.mass_ext_var.get()
            prefix = self.mass_prefix_var.get()
            try:
                start_index = int(self.mass_start_var.get())
            except ValueError:
                start_index = 1
            try:
                zero_pad = int(self.mass_padding_var.get())
            except ValueError:
                zero_pad = 0

            scramble = self.scramble_var.get()
            try:
                scramble_mult = int(self.scramble_multiplier_var.get())
            except ValueError:
                scramble_mult = 1
            if scramble_mult < 1 or scramble_mult > 10:
                scramble_mult = 1

            logging.info(
                f"Preview mass rename: ext='{ext}', prefix='{prefix}', start_index={start_index}, "
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

        elif op == "music_rename":
            self.renamer.rename_music(folder=source, extensions=["mp3"], dry_run=True)

        self._cleanup_file_logger(fh)
        messagebox.showinfo("Preview Complete", f"Logs written to '{self.log_file}'.")

    def on_run(self):
        source = self.source_var.get()
        if not source:
            messagebox.showwarning("Warning", "Please select a Source folder.")
            return

        op = self.operation_var.get()
        fh = self._setup_file_logger()
        logging.info(f"=== RUN: {op.upper()} ===")

        if op == "sort":
            dest = self.dest_var.get()
            if not dest:
                messagebox.showwarning("Warning", "Please select a Destination folder for sorting.")
                self._cleanup_file_logger(fh)
                return
            self.sorter.sort_directory(source, dest, dry_run=False)

        elif op == "mass_rename":
            ext = self.mass_ext_var.get()
            prefix = self.mass_prefix_var.get()
            try:
                start_index = int(self.mass_start_var.get())
            except ValueError:
                start_index = 1
            try:
                zero_pad = int(self.mass_padding_var.get())
            except ValueError:
                zero_pad = 0

            scramble = self.scramble_var.get()
            try:
                scramble_mult = int(self.scramble_multiplier_var.get())
            except ValueError:
                scramble_mult = 1
            if scramble_mult < 1 or scramble_mult > 10:
                scramble_mult = 1

            logging.info(
                f"Mass rename: ext='{ext}', prefix='{prefix}', start_index={start_index}, "
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

        elif op == "music_rename":
            self.renamer.rename_music(folder=source, extensions=["mp3"], preserve_timestamps=True, dry_run=False)

        self._cleanup_file_logger(fh)
        messagebox.showinfo("Operation Complete", f"Logs have been written to '{self.log_file}'.")

    def on_undo(self):
        op = self.operation_var.get()
        fh = self._setup_file_logger()
        success = True

        logging.info(f"=== UNDO: {op.upper()} ===")

        try:
            if op == "sort":
                self.sorter.undo()
                # Remove empty dirs in the destination, if desired
                dest = self.dest_var.get()
                if dest:
                    self._remove_empty_dirs(Path(dest))
            else:
                # mass_rename or music_rename => same rename undo
                self.renamer.undo()
        except Exception as e:
            success = False
            logging.error(f"Undo failed: {e}")

        self._cleanup_file_logger(fh)

        if success:
            messagebox.showinfo("Undo Complete", "All operations have been undone successfully.")
        else:
            messagebox.showerror("Undo Failed", "Some operations could not be undone. Check logs.")

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
    root = tk.Tk()
    app = FileManagerGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()
