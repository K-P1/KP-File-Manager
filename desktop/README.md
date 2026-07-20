# KP File Manager Desktop

The desktop application is a Python package with a PyQt interface, a retained Tk interface, and a CLI.

## Setup

From this directory:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -e .
```

## Run

```powershell
kp-file-manager-gui
```

The legacy Tk interface can be launched with:

```powershell
python -m kp_file_manager.gui_tk
```

CLI help is available with:

```powershell
kp-file-manager --help
```

Runtime logs are written to `runtime/operation_logs.txt`. Category rules are stored in `config/categories.json`.

The pre-existing packaged Windows executable is retained under `dist/` for archival distribution. It predates the source reorganization and should be rebuilt before publishing a new desktop release.

## Checks

```powershell
run_checks.bat
```
