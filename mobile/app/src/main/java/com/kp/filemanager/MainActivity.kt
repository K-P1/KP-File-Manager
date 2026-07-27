/** mobile/app/src/main/java/com/kp/filemanager/MainActivity.kt: Creates the media-operation repository and renders the existing Compose application; wired to the refactored core. */
package com.kp.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.kp.filemanager.data.DefaultFileOperationRepository
import com.kp.filemanager.data.OperationLogStore
import com.kp.filemanager.data.SafDocumentGateway
import com.kp.filemanager.ui.FileManagerScreen
import com.kp.filemanager.ui.FileManagerViewModel
import com.kp.filemanager.ui.theme.KpFileManagerTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        DefaultFileOperationRepository(
            gateway = SafDocumentGateway(this),
            logStore = OperationLogStore(this),
        )
    }
    private val viewModel: FileManagerViewModel by viewModels { FileManagerViewModel.Factory(repository) }

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KpFileManagerTheme {
                FileManagerScreen(
                    viewModel = viewModel,
                    onPickSource = { sourcePicker.launch(null) },
                    onPickDestination = { destinationPicker.launch(null) },
                )
            }
        }
    }

    private fun persistFolderAccess(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    }
}
