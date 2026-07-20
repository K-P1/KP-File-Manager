@REM desktop/run_checks.bat: Runs desktop verification without requiring an editable install; created for the reorganized project.
@echo off
setlocal
set "PYTHONPATH=%~dp0src"
python -m compileall "%~dp0src" "%~dp0tests" || exit /b 1
python -m unittest discover -s "%~dp0tests" -v || exit /b 1
