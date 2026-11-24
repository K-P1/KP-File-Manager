<!--
Path: CODEBASE_STRUCTURE.md
Purpose: Automatically generated repository structure, file catalog, and metadata validation report.
Last changed: 2026-11-24, regenerated repository structure and metadata catalog.
Run: python tools/map_codebase.py
-->

# Codebase Structure and Catalog

> Automatically generated from the current repository. Do not manually edit this file.

## Repository

- Name: `KP-File Manager`
- Generated: `2026-11-24T02:04:08+01:00`
- Filtering: `Git tracked/unignored files`
- Git branch: `main`
- Git revision: `69e88231facb`

## Part 1: Compact File Tree

```text
KP-File Manager/
├── desktop/
│   ├── config/
│   │   └── categories.json
│   ├── src/
│   │   └── kp_file_manager/
│   │       ├── __init__.py
│   │       ├── __main__.py
│   │       ├── cli.py
│   │       ├── gui_qt.py
│   │       ├── gui_tk.py
│   │       ├── paths.py
│   │       ├── renamer.py
│   │       ├── sorter.py
│   │       └── utils.py
│   ├── tests/
│   │   └── test_core.py
│   ├── pyproject.toml
│   ├── README.md
│   └── run_checks.bat
├── docs/
│   ├── FEATURES.md
│   ├── PROTECTED_EXPORT.md
│   └── PROTECTED_EXPORT_VALIDATION.md
├── mobile/
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── aidl/
│   │   │   │   │   └── com/
│   │   │   │   │       └── kp/
│   │   │   │   │           └── filemanager/
│   │   │   │   │               └── export/
│   │   │   │   │                   └── IShizukuShellService.aidl
│   │   │   │   ├── java/
│   │   │   │   │   └── com/
│   │   │   │   │       └── kp/
│   │   │   │   │           └── filemanager/
│   │   │   │   │               ├── data/
│   │   │   │   │               │   ├── DocumentGateway.kt
│   │   │   │   │               │   ├── FileOperationRepository.kt
│   │   │   │   │               │   ├── OperationExecutor.kt
│   │   │   │   │               │   ├── OperationLogStore.kt
│   │   │   │   │               │   ├── SafDocumentGateway.kt
│   │   │   │   │               │   └── SafTransferStrategy.kt
│   │   │   │   │               ├── domain/
│   │   │   │   │               │   ├── DirectoryScanner.kt
│   │   │   │   │               │   ├── EmptyDirectoryCleaner.kt
│   │   │   │   │               │   ├── LibraryContextResolver.kt
│   │   │   │   │               │   ├── MediaFileClassifier.kt
│   │   │   │   │               │   ├── MediaGrouper.kt
│   │   │   │   │               │   ├── MediaNameParser.kt
│   │   │   │   │               │   ├── MediaPathFormatter.kt
│   │   │   │   │               │   ├── OperationModels.kt
│   │   │   │   │               │   ├── OperationPlanner.kt
│   │   │   │   │               │   └── ProgressReporter.kt
│   │   │   │   │               ├── export/
│   │   │   │   │               │   ├── ExportForegroundService.kt
│   │   │   │   │               │   ├── ExportJournal.kt
│   │   │   │   │               │   ├── ProtectedExportModels.kt
│   │   │   │   │               │   ├── ProtectedExportRepository.kt
│   │   │   │   │               │   ├── ProtectedExportScreen.kt
│   │   │   │   │               │   ├── ProtectedExportViewModel.kt
│   │   │   │   │               │   └── ShizukuShell.kt
│   │   │   │   │               ├── ui/
│   │   │   │   │               │   ├── components/
│   │   │   │   │               │   │   └── KpComponents.kt
│   │   │   │   │               │   ├── screens/
│   │   │   │   │               │   │   ├── FileToolScreens.kt
│   │   │   │   │               │   │   ├── OrganizerScreen.kt
│   │   │   │   │               │   │   └── SettingsScreen.kt
│   │   │   │   │               │   ├── theme/
│   │   │   │   │               │   │   └── KpFileManagerTheme.kt
│   │   │   │   │               │   ├── FileManagerPresentation.kt
│   │   │   │   │               │   ├── FileManagerScreen.kt
│   │   │   │   │               │   ├── FileManagerUiState.kt
│   │   │   │   │               │   └── FileManagerViewModel.kt
│   │   │   │   │               └── MainActivity.kt
│   │   │   │   ├── res/
│   │   │   │   │   ├── drawable/
│   │   │   │   │   │   └── ic_launcher.xml
│   │   │   │   │   └── values/
│   │   │   │   │       ├── strings.xml
│   │   │   │   │       └── styles.xml
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── kp/
│   │   │                   └── filemanager/
│   │   │                       ├── data/
│   │   │                       │   ├── OperationExecutorTest.kt
│   │   │                       │   └── SafTransferStrategyTest.kt
│   │   │                       ├── domain/
│   │   │                       │   ├── DirectoryScannerTest.kt
│   │   │                       │   ├── EmptyDirectoryCleanerTest.kt
│   │   │                       │   ├── FileToolPlannerTest.kt
│   │   │                       │   ├── MediaIndexPipelineTest.kt
│   │   │                       │   ├── MediaNameParserTest.kt
│   │   │                       │   ├── MediaOrganizationTest.kt
│   │   │                       │   └── ProgressReporterTest.kt
│   │   │                       ├── export/
│   │   │                       │   ├── ProtectedExportPlannerTest.kt
│   │   │                       │   └── ShizukuShellTest.kt
│   │   │                       └── ui/
│   │   │                           ├── FileManagerPresentationTest.kt
│   │   │                           └── FileManagerViewModelTest.kt
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   ├── gradle/
│   │   ├── wrapper/
│   │   │   ├── gradle-wrapper.jar
│   │   │   └── gradle-wrapper.properties
│   │   └── gradle-daemon-jvm.properties
│   ├── build.gradle.kts
│   ├── device_ui_initial.xml
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── README.md
│   ├── run_checks.bat
│   └── settings.gradle.kts
├── tools/
│   ├── codebase_packager.py
│   ├── codebase_packager.toml
│   ├── file_replacer.py
│   └── map_codebase.py
├── .gitignore
└── README.md
```

## Part 2: File Catalog

Catalog metadata is extracted only from the leading metadata comment in each file. Responsibilities are never inferred from filenames or implementation contents.

### Catalog Validation

- Cataloged files: 87
- Text/header-capable files: 86
- Headers found: 78
- Complete valid headers: 18
- Files with header warnings: 68
- Binary/non-UTF-8/symlink files without inspected headers: 1

#### Header Warnings

- `.gitignore`: Missing top-of-file metadata comment.
- `desktop/config/categories.json`: Missing top-of-file metadata comment.
- `desktop/pyproject.toml`: Missing top-of-file metadata comment.
- `desktop/README.md`: Missing Path metadata.
- `desktop/README.md`: Missing Purpose metadata.
- `desktop/README.md`: Missing Last changed metadata.
- `desktop/run_checks.bat`: Missing top-of-file metadata comment.
- `desktop/src/kp_file_manager/__init__.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/__init__.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/__init__.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/__main__.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/__main__.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/__main__.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/cli.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/cli.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/cli.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/gui_qt.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/gui_qt.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/gui_qt.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/gui_tk.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/gui_tk.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/gui_tk.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/paths.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/paths.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/paths.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/renamer.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/renamer.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/renamer.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/sorter.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/sorter.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/sorter.py`: Missing Last changed metadata.
- `desktop/src/kp_file_manager/utils.py`: Missing Path metadata.
- `desktop/src/kp_file_manager/utils.py`: Missing Purpose metadata.
- `desktop/src/kp_file_manager/utils.py`: Missing Last changed metadata.
- `desktop/tests/test_core.py`: Missing Path metadata.
- `desktop/tests/test_core.py`: Missing Purpose metadata.
- `desktop/tests/test_core.py`: Missing Last changed metadata.
- `docs/FEATURES.md`: Missing Path metadata.
- `docs/FEATURES.md`: Missing Purpose metadata.
- `docs/FEATURES.md`: Missing Last changed metadata.
- `mobile/app/src/main/AndroidManifest.xml`: Missing Path metadata.
- `mobile/app/src/main/AndroidManifest.xml`: Missing Purpose metadata.
- `mobile/app/src/main/AndroidManifest.xml`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/DocumentGateway.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/DocumentGateway.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/DocumentGateway.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/OperationLogStore.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/OperationLogStore.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/OperationLogStore.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/SafTransferStrategy.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/SafTransferStrategy.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/data/SafTransferStrategy.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/DirectoryScanner.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/DirectoryScanner.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/DirectoryScanner.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/EmptyDirectoryCleaner.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/EmptyDirectoryCleaner.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/EmptyDirectoryCleaner.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/LibraryContextResolver.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/LibraryContextResolver.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/LibraryContextResolver.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaFileClassifier.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaFileClassifier.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaFileClassifier.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaGrouper.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaGrouper.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaGrouper.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaNameParser.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaNameParser.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaNameParser.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaPathFormatter.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaPathFormatter.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/MediaPathFormatter.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/ProgressReporter.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/ProgressReporter.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/domain/ProgressReporter.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/MainActivity.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/MainActivity.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/MainActivity.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/components/KpComponents.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/components/KpComponents.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/components/KpComponents.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerPresentation.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerPresentation.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerPresentation.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerScreen.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerScreen.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerScreen.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/FileToolScreens.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/FileToolScreens.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/FileToolScreens.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/OrganizerScreen.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/OrganizerScreen.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/OrganizerScreen.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/SettingsScreen.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/SettingsScreen.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/screens/SettingsScreen.kt`: Missing Last changed metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/theme/KpFileManagerTheme.kt`: Missing Path metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/theme/KpFileManagerTheme.kt`: Missing Purpose metadata.
- `mobile/app/src/main/java/com/kp/filemanager/ui/theme/KpFileManagerTheme.kt`: Missing Last changed metadata.
- `mobile/app/src/main/res/drawable/ic_launcher.xml`: Missing Path metadata.
- `mobile/app/src/main/res/drawable/ic_launcher.xml`: Missing Purpose metadata.
- `mobile/app/src/main/res/drawable/ic_launcher.xml`: Missing Last changed metadata.
- `mobile/app/src/main/res/values/strings.xml`: Missing Path metadata.
- `mobile/app/src/main/res/values/strings.xml`: Missing Purpose metadata.
- `mobile/app/src/main/res/values/strings.xml`: Missing Last changed metadata.
- `mobile/app/src/main/res/values/styles.xml`: Missing Path metadata.
- `mobile/app/src/main/res/values/styles.xml`: Missing Purpose metadata.
- `mobile/app/src/main/res/values/styles.xml`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/data/OperationExecutorTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/data/OperationExecutorTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/data/OperationExecutorTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/data/SafTransferStrategyTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/data/SafTransferStrategyTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/data/SafTransferStrategyTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/DirectoryScannerTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/DirectoryScannerTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/DirectoryScannerTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/EmptyDirectoryCleanerTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/EmptyDirectoryCleanerTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/EmptyDirectoryCleanerTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/FileToolPlannerTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/FileToolPlannerTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/FileToolPlannerTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaIndexPipelineTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaIndexPipelineTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaIndexPipelineTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaNameParserTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaNameParserTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaNameParserTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaOrganizationTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaOrganizationTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/MediaOrganizationTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/ProgressReporterTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/ProgressReporterTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/domain/ProgressReporterTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerPresentationTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerPresentationTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerPresentationTest.kt`: Missing Last changed metadata.
- `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerViewModelTest.kt`: Missing Path metadata.
- `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerViewModelTest.kt`: Missing Purpose metadata.
- `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerViewModelTest.kt`: Missing Last changed metadata.
- `mobile/build.gradle.kts`: Missing Path metadata.
- `mobile/build.gradle.kts`: Missing Purpose metadata.
- `mobile/build.gradle.kts`: Missing Last changed metadata.
- `mobile/device_ui_initial.xml`: Missing top-of-file metadata comment.
- `mobile/gradle.properties`: Missing Path metadata.
- `mobile/gradle.properties`: Missing Purpose metadata.
- `mobile/gradle.properties`: Missing Last changed metadata.
- `mobile/gradle/gradle-daemon-jvm.properties`: Missing Path metadata.
- `mobile/gradle/gradle-daemon-jvm.properties`: Missing Purpose metadata.
- `mobile/gradle/gradle-daemon-jvm.properties`: Missing Last changed metadata.
- `mobile/gradle/wrapper/gradle-wrapper.properties`: Missing top-of-file metadata comment.
- `mobile/gradlew`: Missing Path metadata.
- `mobile/gradlew`: Missing Purpose metadata.
- `mobile/gradlew`: Missing Last changed metadata.
- `mobile/gradlew.bat`: Missing top-of-file metadata comment.
- `mobile/README.md`: Missing Path metadata.
- `mobile/README.md`: Missing Purpose metadata.
- `mobile/README.md`: Missing Last changed metadata.
- `mobile/run_checks.bat`: Missing top-of-file metadata comment.
- `mobile/settings.gradle.kts`: Missing Path metadata.
- `mobile/settings.gradle.kts`: Missing Purpose metadata.
- `mobile/settings.gradle.kts`: Missing Last changed metadata.
- `README.md`: Missing Path metadata.
- `README.md`: Missing Purpose metadata.
- `README.md`: Missing Last changed metadata.

### Catalog Entries

### `.gitignore`

- Metadata: missing top-of-file metadata comment.

### `desktop/config/categories.json`

- Metadata: missing top-of-file metadata comment.

### `desktop/pyproject.toml`

- Metadata: missing top-of-file metadata comment.

### `desktop/README.md`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/run_checks.bat`

- Metadata: missing top-of-file metadata comment.

### `desktop/src/kp_file_manager/__init__.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/__main__.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/cli.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/gui_qt.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/gui_tk.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/paths.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/renamer.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/sorter.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/src/kp_file_manager/utils.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `desktop/tests/test_core.py`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `docs/FEATURES.md`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `docs/PROTECTED_EXPORT.md`

- Purpose: Describes protected export behavior, safety and device validation.
- Last changed: 2026-11-24, document the authorized legacy fallback and startup troubleshooting.

### `docs/PROTECTED_EXPORT_VALIDATION.md`

- Purpose: Records protected export validation and remaining device checks.
- Last changed: 2026-11-24, add fallback transport device checks.

### `mobile/app/build.gradle.kts`

- Purpose: Configures the Android application, build variants, and test dependencies.
- Last changed: 2026-11-23 — aligned the minimum SDK with Shizuku and adopted typed Kotlin compiler options.

### `mobile/app/proguard-rules.pro`

- Purpose: Preserves app entry points that external runtimes instantiate reflectively.
- Last changed: 2026-11-23 — retained the Shizuku UserService constructor for release builds.

### `mobile/app/src/main/aidl/com/kp/filemanager/export/IShizukuShellService.aidl`

- Purpose: Defines the privileged command boundary hosted by Shizuku UserService.
- Last changed: 2026-11-23 — replaced the removed Shizuku newProcess API.

### `mobile/app/src/main/AndroidManifest.xml`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/data/DocumentGateway.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/data/OperationLogStore.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/data/SafTransferStrategy.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/DirectoryScanner.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/EmptyDirectoryCleaner.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/LibraryContextResolver.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/MediaFileClassifier.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/MediaGrouper.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/MediaNameParser.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/MediaPathFormatter.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/domain/ProgressReporter.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/export/ExportForegroundService.kt`

- Purpose: Keep long, user-initiated Shizuku exports foreground and mirror progress to a system notification.
- Last changed: 2026-11-23, protect multi-gigabyte transfers from background restrictions.

### `mobile/app/src/main/java/com/kp/filemanager/export/ExportJournal.kt`

- Purpose: Durable atomic transaction journal for crash cleanup and reversible protected exports.
- Last changed: 2026-11-23, introduce crash-safe export records.

### `mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportModels.kt`

- Purpose: Strict source/destination rules and selection-ready KP organizer plans for protected media.
- Last changed: 2026-11-23, introduce protected media exporting.

### `mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportRepository.kt`

- Purpose: Transactional Shizuku export with byte progress, verified copies, non-clobber publishing and recovery.
- Last changed: 2026-11-23, add protected source to KP organizer pipeline.

### `mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportScreen.kt`

- Purpose: Responsive Material 3 protected media export with selection, detailed previews and live byte progress.
- Last changed: 2026-11-24, distinguish Shizuku authorization from verified folder access.

### `mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportViewModel.kt`

- Purpose: Lifecycle-safe selection, permission, scan, transfer, cancel, restore and progress state.
- Last changed: 2026-11-24, align permission guidance with the authorization-only check.

### `mobile/app/src/main/java/com/kp/filemanager/export/ShizukuShell.kt`

- Purpose: Executes checked Shizuku shell commands with a UserService and legacy compatibility transport.
- Last changed: 2026-11-24, recover from UserService startup failures with the authorized legacy process transport.

### `mobile/app/src/main/java/com/kp/filemanager/MainActivity.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/components/KpComponents.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerPresentation.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerScreen.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/screens/FileToolScreens.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/screens/OrganizerScreen.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/screens/SettingsScreen.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/java/com/kp/filemanager/ui/theme/KpFileManagerTheme.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/res/drawable/ic_launcher.xml`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/res/values/strings.xml`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/main/res/values/styles.xml`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/data/OperationExecutorTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/data/SafTransferStrategyTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/DirectoryScannerTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/EmptyDirectoryCleanerTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/FileToolPlannerTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/MediaIndexPipelineTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/MediaNameParserTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/MediaOrganizationTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/domain/ProgressReporterTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/export/ProtectedExportPlannerTest.kt`

- Purpose: Regression tests for cross-folder subtitle matching, KP output names and fail-closed conflicts.
- Last changed: 2026-11-23, add protected export coverage.

### `mobile/app/src/test/java/com/kp/filemanager/export/ShizukuShellTest.kt`

- Purpose: Verifies the process execution core used by the Shizuku user-service bridge.
- Last changed: 2026-11-24, cover shared process collection and one-shot command cancellation.

### `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerPresentationTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerViewModelTest.kt`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/build.gradle.kts`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/device_ui_initial.xml`

- Metadata: missing top-of-file metadata comment.

### `mobile/gradle.properties`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/gradle/gradle-daemon-jvm.properties`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/gradle/wrapper/gradle-wrapper.jar`

- Metadata: not inspected because the file is binary, non-UTF-8, or a symlink.

### `mobile/gradle/wrapper/gradle-wrapper.properties`

- Metadata: missing top-of-file metadata comment.

### `mobile/gradlew`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/gradlew.bat`

- Metadata: missing top-of-file metadata comment.

### `mobile/README.md`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `mobile/run_checks.bat`

- Metadata: missing top-of-file metadata comment.

### `mobile/settings.gradle.kts`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `README.md`

- Header warning: Missing Path metadata.
- Header warning: Missing Purpose metadata.
- Header warning: Missing Last changed metadata.

### `tools/codebase_packager.py`

- Purpose: Safely copy generic source snapshots or requested files into a local folder and ZIP.
- Last changed: 2026-09-25, include the wrapper JAR and copy staging safely on Windows.

### `tools/codebase_packager.toml`

- Purpose: Configure safe source extraction limits, output and Git-style ignore rules.
- Last changed: 2026-09-16, initialize editable codebase packaging configuration.

### `tools/file_replacer.py`

- Purpose: Safely install manifest-defined replacement packages from a folder or ZIP.
- Last changed: 2026-09-28, require self-contained package manifests for every replacement and deletion.

### `tools/map_codebase.py`

- Purpose: Generates a two-part Markdown codebase map with a compact file tree and catalog extracted from top-of-file metadata comments.
- Last changed: 2026-09-27, add deployable Path metadata to generated Markdown output.
- Run: `python tools/map_codebase.py`

### Repository Statistics

- Files: 87
- Directories: 41
- Kotlin/Java source files: 49
- SQLDelight files: 0
- Text lines: 15,129

#### File Types

- `.kt`: 46
- `.py`: 13
- `.md`: 6
- `.xml`: 5
- `.bat`: 3
- `.kts`: 3
- `.properties`: 3
- `[no extension]`: 2
- `.toml`: 2
- `.json`: 1
- `.pro`: 1
- `.aidl`: 1
- `.jar`: 1

### Catalog Contract

Preferred metadata format:

```text
Path: repository/relative/path.ext
Purpose: One-line responsibility description.
Last changed: YYYY-MM-DD, concise description of the latest change.
Run: optional command
Action: optional operational note
```

The legacy positional format is also accepted when the first comment line is a path and the second is the purpose. `Run:` and `Action:` may appear more than once. The declared path is checked against the actual repository-relative path.

The generated output file is always excluded from its own map. When Git is available, tracked files plus unignored untracked files are used so local secrets and ignored/generated content do not pollute the map. If Git is unavailable, the script falls back to a pruned filesystem scan.
