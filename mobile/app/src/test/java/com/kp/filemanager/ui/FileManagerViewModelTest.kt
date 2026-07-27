/** mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerViewModelTest.kt: Verifies persisted organization defaults are initialized and emitted by the ViewModel; created for the UI refactor. */
package com.kp.filemanager.ui

import com.kp.filemanager.data.FileOperationRepository
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.OperationResult
import com.kp.filemanager.domain.ScanProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class FileManagerViewModelTest {
    @Test
    fun `organization defaults initialize state and emit when changed`() {
        val persisted = mutableListOf<OrganizationDefaults>()
        val viewModel = FileManagerViewModel(
            repository = FakeRepository,
            organizationDefaults = OrganizationDefaults(
                maximumScanDepth = 6,
                movieFolderName = "Films",
                tvFolderName = "Television",
            ),
            onOrganizationDefaultsChanged = persisted::add,
        )

        assertEquals(6, viewModel.uiState.value.maximumScanDepth)
        assertEquals("Films", viewModel.uiState.value.movieFolderName)
        assertEquals("Television", viewModel.uiState.value.tvFolderName)

        viewModel.setMaximumScanDepth(8)
        viewModel.setMovieFolderName("Movies")
        viewModel.setTvFolderName("Shows")

        assertEquals(
            OrganizationDefaults(8, "Movies", "Shows"),
            persisted.last(),
        )
    }

    private object FakeRepository : FileOperationRepository {
        override suspend fun preview(
            request: OperationRequest,
            onScanProgress: (ScanProgress) -> Unit,
        ) = Result.success(OperationResult(OperationPlan(request.sourceTreeUri, emptyList())))

        override suspend fun execute(plan: OperationPlan) =
            Result.success(OperationResult(plan))

        override suspend fun undoLast() =
            Result.success(OperationResult(OperationPlan("", emptyList())))

        override fun folderName(treeUri: String) = "Folder"
        override fun canUndo() = false
    }
}
