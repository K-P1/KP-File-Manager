/** mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerViewModelTest.kt: Verifies organizer defaults, output-root fallback, and the repository contract; last changed for explicit output selection. */
package com.kp.filemanager.ui

import com.kp.filemanager.data.FileOperationRepository
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.OperationResult
import com.kp.filemanager.domain.OrganizationProgress
import com.kp.filemanager.domain.LibraryContextType
import org.junit.Assert.assertEquals
import org.junit.Test

class FileManagerViewModelTest {
    @Test
    fun `organization output defaults to the selected source folder`() {
        val state = FileManagerUiState(
            sourceUri = "content://source/downloads",
            sourceName = "Downloads",
        )

        assertEquals("content://source/downloads", state.effectiveDestinationUri)
        assertEquals("Downloads", state.effectiveDestinationName)
        assertEquals(true, state.usesSourceAsDestination)
    }

    @Test
    fun `custom organization output replaces the source fallback`() {
        val state = FileManagerUiState(
            sourceUri = "content://source/downloads",
            sourceName = "Downloads",
            destinationUri = "content://library/media",
            destinationName = "Media",
        )

        assertEquals("content://library/media", state.effectiveDestinationUri)
        assertEquals("Media", state.effectiveDestinationName)
        assertEquals(false, state.usesSourceAsDestination)
    }

    @Test
    fun `library overrides retain separate movie and tv destinations`() {
        val state = FileManagerUiState(
            movieLibraryUri = "content://library/movies",
            movieLibraryName = "Movies",
            tvLibraryUri = "content://library/tv",
            tvLibraryName = "TV Shows",
        )

        assertEquals("content://library/movies", state.movieLibraryUri)
        assertEquals("Movies", state.movieLibraryName)
        assertEquals("content://library/tv", state.tvLibraryUri)
        assertEquals("TV Shows", state.tvLibraryName)
    }

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
            onProgress: (OrganizationProgress) -> Unit,
        ) = Result.success(OperationResult(OperationPlan(request.sourceTreeUri, emptyList())))

        override suspend fun execute(
            plan: OperationPlan,
            onProgress: (OrganizationProgress) -> Unit,
        ) =
            Result.success(OperationResult(plan))

        override suspend fun undoLast() =
            Result.success(OperationResult(OperationPlan("", emptyList())))

        override suspend fun resolveLibraryContext(
            plan: OperationPlan,
            contextType: LibraryContextType,
        ) = Result.success(OperationResult(plan))

        override suspend fun folderName(treeUri: String) = "Folder"
        override fun canUndo() = false
    }
}
