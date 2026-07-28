/** mobile/app/src/main/java/com/kp/filemanager/MainActivity.kt: Creates the optimized media repository with debug-only instrumentation and renders the Compose application; updated for pipeline telemetry. */
package com.kp.filemanager

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.edit
import com.kp.filemanager.data.DefaultFileOperationRepository
import com.kp.filemanager.data.OperationLogStore
import com.kp.filemanager.data.SafDocumentGateway
import com.kp.filemanager.ui.FileManagerScreen
import com.kp.filemanager.ui.FileManagerViewModel
import com.kp.filemanager.ui.OrganizationDefaults
import com.kp.filemanager.ui.theme.KpFileManagerTheme
import com.kp.filemanager.ui.theme.ThemePreference

class MainActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }
    private val repository by lazy {
        DefaultFileOperationRepository(
            gateway = SafDocumentGateway(this),
            logStore = OperationLogStore(this),
            debugLoggingEnabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
    }
    private val viewModel: FileManagerViewModel by viewModels {
        FileManagerViewModel.Factory(
            repository = repository,
            organizationDefaults = readOrganizationDefaults(),
            onOrganizationDefaultsChanged = ::persistOrganizationDefaults,
        )
    }

    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            persistFolderAccess(it)
            viewModel.selectSource(it.toString())
        }
    }
    private val destinationPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            persistFolderAccess(it)
            viewModel.selectDestination(it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var themeName by rememberSaveable {
                mutableStateOf(
                    preferences.getString(THEME_KEY, ThemePreference.SYSTEM.name)
                        ?: ThemePreference.SYSTEM.name,
                )
            }
            val themePreference = ThemePreference.entries
                .firstOrNull { it.name == themeName }
                ?: ThemePreference.SYSTEM
            val useDarkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                        detectDarkMode = { useDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = LIGHT_NAVIGATION_SCRIM,
                        darkScrim = DARK_NAVIGATION_SCRIM,
                        detectDarkMode = { useDarkTheme },
                    ),
                )
            }
            KpFileManagerTheme(themePreference = themePreference) {
                FileManagerScreen(
                    viewModel = viewModel,
                    onPickSource = { sourcePicker.launch(null) },
                    onPickDestination = { destinationPicker.launch(null) },
                    themePreference = themePreference,
                    onThemePreferenceChange = { selected ->
                        themeName = selected.name
                        preferences.edit { putString(THEME_KEY, selected.name) }
                    },
                )
            }
        }
    }

    private fun persistFolderAccess(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private fun readOrganizationDefaults() = OrganizationDefaults(
        maximumScanDepth = preferences.getInt(SCAN_DEPTH_KEY, 4),
        movieFolderName = preferences.getString(MOVIE_FOLDER_KEY, "Movies") ?: "Movies",
        tvFolderName = preferences.getString(TV_FOLDER_KEY, "TV Shows") ?: "TV Shows",
    )

    private fun persistOrganizationDefaults(defaults: OrganizationDefaults) {
        preferences.edit {
            putInt(SCAN_DEPTH_KEY, defaults.maximumScanDepth)
            putString(MOVIE_FOLDER_KEY, defaults.movieFolderName)
            putString(TV_FOLDER_KEY, defaults.tvFolderName)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "kp_file_manager_preferences"
        const val THEME_KEY = "theme"
        const val SCAN_DEPTH_KEY = "scan_depth"
        const val MOVIE_FOLDER_KEY = "movie_folder"
        const val TV_FOLDER_KEY = "tv_folder"
        val LIGHT_NAVIGATION_SCRIM = 0xFFF9F9FF.toInt()
        val DARK_NAVIGATION_SCRIM = 0xFF111318.toInt()
    }
}
