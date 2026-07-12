package com.jupiter.filemanager.feature.vault

import android.net.Uri
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VaultViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importDocument_forwardsContentUriOnlyAfterTheVaultIsUnlocked() = runTest(dispatcher) {
        val repository = RecordingVaultRepository()
        val viewModel = VaultViewModel(repository)
        val documentUri = Uri.parse("content://documents.example/document/42")
        advanceUntilIdle()

        viewModel.importDocument(documentUri)
        advanceUntilIdle()

        assertNull("A locked vault must not read the selected document.", repository.importedUri)
        assertEquals(0, repository.pathImportCount)

        viewModel.unlock()
        advanceUntilIdle()
        viewModel.importDocument(documentUri)
        advanceUntilIdle()

        assertEquals(documentUri, repository.importedUri)
        assertEquals("The URI flow must not be downgraded to a path import.", 0, repository.pathImportCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun importDocument_surfacesRepositoryErrorWhileKeepingVaultUnlockedAndItemsIntact() =
        runTest(dispatcher) {
            val repository = RecordingVaultRepository().apply {
                uriImportResult = AppResult.Failure(
                    AppError.Io("The selected document cannot be read."),
                )
            }
            val viewModel = VaultViewModel(repository)
            advanceUntilIdle()

            viewModel.unlock()
            advanceUntilIdle()
            viewModel.importDocument(Uri.parse("content://documents.example/document/unavailable"))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isUnlocked)
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("The selected document cannot be read.", viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.items.isEmpty())
        }

    private class RecordingVaultRepository : VaultRepository {
        var importedUri: Uri? = null
        var pathImportCount: Int = 0
        var uriImportResult: AppResult<FileItem> = AppResult.Success(sampleItem())

        override suspend fun isVaultInitialized(): Boolean = false

        override suspend fun listVaultFiles(): AppResult<List<FileItem>> = AppResult.Success(emptyList())

        override suspend fun importToVault(sourcePath: String): AppResult<FileItem> {
            pathImportCount += 1
            return AppResult.Success(sampleItem())
        }

        override suspend fun importToVault(sourceUri: Uri): AppResult<FileItem> {
            importedUri = sourceUri
            return uriImportResult
        }

        override suspend fun exportFromVault(
            vaultItem: FileItem,
            destinationDir: String,
        ): AppResult<FileItem> = AppResult.Success(vaultItem)

        override suspend fun deleteFromVault(vaultItem: FileItem): AppResult<Unit> =
            AppResult.Success(Unit)

        private companion object {
            fun sampleItem(): FileItem = FileItem(
                path = "/vault/entry.enc",
                name = "entry.txt",
                isDirectory = false,
                sizeBytes = 7L,
                lastModified = 1L,
                type = FileType.DOCUMENT,
                extension = "txt",
                mimeType = "text/plain",
            )
        }
    }
}
