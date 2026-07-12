package com.jupiter.filemanager.feature.vault

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.repository.VaultRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    fun importDocument_forwardsContentUriOnlyAfterAuthenticatedUnlock() = runTest(dispatcher) {
        val repository = RecordingVaultRepository()
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        val documentUri = Uri.parse("content://documents.example/document/42")
        runCurrent()

        viewModel.importDocument(documentUri)
        runCurrent()

        assertNull("A locked Vault must not read the selected document.", repository.importedUri)
        assertEquals(0, repository.pathImportCount)

        authenticateWithDevice(viewModel)
        runCurrent()
        viewModel.importDocument(documentUri)
        runCurrent()

        assertEquals(documentUri, repository.importedUri)
        assertEquals("The URI flow must not be downgraded to a path import.", 0, repository.pathImportCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun importDocument_surfacesRepositoryErrorWithoutEndingAuthenticatedSession() =
        runTest(dispatcher) {
            val repository = RecordingVaultRepository().apply {
                uriImportResult = AppResult.Failure(
                    AppError.Io("The selected document cannot be read."),
                )
            }
            val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
            runCurrent()
            authenticateWithDevice(viewModel)
            runCurrent()

            viewModel.importDocument(Uri.parse("content://documents.example/document/unavailable"))
            runCurrent()

            assertTrue(viewModel.uiState.value.isUnlocked)
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("The selected document cannot be read.", viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.items.isEmpty())
        }

    @Test
    fun exportItem_usesChosenExistingDestinationAndReportsSuccess() = runTest(dispatcher) {
        val repository = RecordingVaultRepository()
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        val item = RecordingVaultRepository.sampleItem()
        viewModel.exportItem(item, "/storage/emulated/0/Download")
        runCurrent()

        assertEquals("/storage/emulated/0/Download", repository.exportDestination)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Exported entry.txt to Downloads", viewModel.uiState.value.infoMessage)
    }

    @Test
    fun busyExportPreventsConcurrentPermanentDelete() = runTest(dispatcher) {
        val repository = RecordingVaultRepository()
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        val item = RecordingVaultRepository.sampleItem()
        viewModel.exportItem(item, "/storage/emulated/0/Download")
        assertTrue("Busy must be set before the IO coroutine starts.", viewModel.uiState.value.isLoading)
        viewModel.deleteItem(item)
        runCurrent()

        assertEquals(0, repository.deleteCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun deviceCallbackWithoutOutstandingSystemChallengeCannotUnlock() = runTest(dispatcher) {
        val viewModel = VaultViewModel(RecordingVaultRepository(), FakeVaultRuntimeSecurity())
        runCurrent()

        viewModel.onDeviceAuthenticationSucceeded()
        runCurrent()

        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun pinOnlyPolicyRejectsDevicePathAndRequiresRealPinVerification() = runTest(dispatcher) {
        val security = FakeVaultRuntimeSecurity(
            pinConfigured = true,
            biometricUnlockEnabled = false,
            acceptedPin = "1234",
        )
        val viewModel = VaultViewModel(RecordingVaultRepository(), security)
        runCurrent()

        assertFalse(viewModel.uiState.value.deviceCredentialUnlockEnabled)
        assertFalse(viewModel.beginDeviceAuthentication())

        viewModel.verifyVaultPin("9999".toCharArray())
        runCurrent()
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertEquals("Incorrect PIN. Vault stays locked.", viewModel.uiState.value.authenticationError)

        val callerOwnedPin = "1234".toCharArray()
        viewModel.verifyVaultPin(callerOwnedPin)
        callerOwnedPin.fill('\u0000')
        runCurrent()

        assertEquals(listOf("9999", "1234"), security.verifiedPins)
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.authenticationError)
    }

    @Test
    fun missingPinAndDisabledLegacyFlagFailsSafeToDeviceCredential() = runTest(dispatcher) {
        val security = FakeVaultRuntimeSecurity(
            pinConfigured = false,
            biometricUnlockEnabled = false,
        )
        val viewModel = VaultViewModel(RecordingVaultRepository(), security)
        runCurrent()

        assertTrue(viewModel.uiState.value.deviceCredentialUnlockEnabled)
        authenticateWithDevice(viewModel)
        runCurrent()
        assertTrue(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun persistedOneMinuteInactivityWindowLocksAndClearsItems() = runTest(dispatcher) {
        val repository = RecordingVaultRepository().apply {
            listedItems = listOf(RecordingVaultRepository.sampleItem())
        }
        val security = FakeVaultRuntimeSecurity(autoLockMinutes = 1)
        val viewModel = VaultViewModel(repository, security)
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        assertTrue(viewModel.uiState.value.isUnlocked)
        assertEquals(1, viewModel.uiState.value.items.size)

        advanceTimeBy(59_999L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isUnlocked)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun userInteractionRestartsTheInactivityWindow() = runTest(dispatcher) {
        val viewModel = VaultViewModel(
            RecordingVaultRepository(),
            FakeVaultRuntimeSecurity(autoLockMinutes = 1),
        )
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        advanceTimeBy(30_000L)
        viewModel.recordUserInteraction()
        advanceTimeBy(59_999L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isUnlocked)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun hostStopImmediatelyLocksAnAuthenticatedSession() = runTest(dispatcher) {
        val repository = RecordingVaultRepository().apply {
            listedItems = listOf(RecordingVaultRepository.sampleItem())
        }
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        viewModel.onHostStopped()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun systemCredentialPromptMayStopHostBeforeItsSuccessCallback() = runTest(dispatcher) {
        val viewModel = VaultViewModel(RecordingVaultRepository(), FakeVaultRuntimeSecurity())
        runCurrent()

        assertTrue(viewModel.beginDeviceAuthentication())
        viewModel.onHostStopped()
        viewModel.onDeviceAuthenticationSucceeded()
        runCurrent()

        assertFalse("A success callback must not expose data while host is stopped.", viewModel.uiState.value.isUnlocked)

        viewModel.onHostStarted()
        runCurrent()

        assertTrue(viewModel.uiState.value.isUnlocked)
    }

    @Test
    fun pickerStopAndReturnedUriRequireFreshAuthenticationBeforeImport() = runTest(dispatcher) {
        val repository = RecordingVaultRepository()
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        val returnedUri = Uri.parse("content://documents.example/document/private")
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        assertTrue(viewModel.beginDocumentPicker())
        viewModel.onHostStopped()
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.items.isEmpty())

        viewModel.onDocumentPickerResult(returnedUri)
        viewModel.onHostStarted()
        runCurrent()

        assertTrue(viewModel.uiState.value.pendingImportAwaitingAuthentication)
        assertNull("Returned URI must not be read before re-authentication.", repository.importedUri)

        authenticateWithDevice(viewModel)
        runCurrent()

        assertEquals(returnedUri, repository.importedUri)
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertFalse(viewModel.uiState.value.pendingImportAwaitingAuthentication)
    }

    @Test
    fun pickerCancellationAfterStopClearsPendingFlowWithoutImport() = runTest(dispatcher) {
        val repository = RecordingVaultRepository()
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        assertTrue(viewModel.beginDocumentPicker())
        viewModel.onHostStopped()
        viewModel.onDocumentPickerResult(null)
        viewModel.onHostStarted()
        runCurrent()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertFalse(viewModel.uiState.value.pendingImportAwaitingAuthentication)
        assertNull(repository.importedUri)
    }

    @Test
    fun pickerLaunchMarkerSurvivesViewModelRecreationButUriStillRequiresFreshAuthentication() =
        runTest(dispatcher) {
            val repository = RecordingVaultRepository()
            val security = FakeVaultRuntimeSecurity()
            val savedStateHandle = SavedStateHandle()
            val first = VaultViewModel(repository, security, savedStateHandle)
            val returnedUri = Uri.parse("content://documents.example/document/recreated")
            runCurrent()
            authenticateWithDevice(first)
            runCurrent()

            assertTrue(first.beginDocumentPicker())
            first.onHostStopped()

            // Models Activity/process recreation while Activity Result keeps the pending launch.
            val recreated = VaultViewModel(repository, security, savedStateHandle)
            runCurrent()
            recreated.onDocumentPickerResult(returnedUri)
            recreated.onHostStarted()
            runCurrent()

            assertTrue(recreated.uiState.value.pendingImportAwaitingAuthentication)
            assertNull("The returned URI must remain unread before fresh auth.", repository.importedUri)

            authenticateWithDevice(recreated)
            runCurrent()

            assertEquals(returnedUri, repository.importedUri)
            assertFalse(recreated.uiState.value.pendingImportAwaitingAuthentication)
            assertTrue(
                "Only launch provenance may be saved; the returned URI must stay memory-only.",
                savedStateHandle.keys().isEmpty(),
            )
        }

    @Test
    fun explicitLockClearsSavedPickerProvenanceSoRecreatedViewModelRejectsStaleCallback() =
        runTest(dispatcher) {
            val repository = RecordingVaultRepository()
            val security = FakeVaultRuntimeSecurity()
            val savedStateHandle = SavedStateHandle()
            val first = VaultViewModel(repository, security, savedStateHandle)
            runCurrent()
            authenticateWithDevice(first)
            runCurrent()

            assertTrue(first.beginDocumentPicker())
            first.lock()
            assertTrue(savedStateHandle.keys().isEmpty())

            val recreated = VaultViewModel(repository, security, savedStateHandle)
            runCurrent()
            recreated.onDocumentPickerResult(
                Uri.parse("content://documents.example/document/stale"),
            )
            authenticateWithDevice(recreated)
            runCurrent()

            assertNull(repository.importedUri)
            assertFalse(recreated.uiState.value.pendingImportAwaitingAuthentication)
        }

    @Test
    fun explicitLockInvalidatesInFlightResultAndPreventsMetadataLeak() = runTest(dispatcher) {
        val exportGate = CompletableDeferred<Unit>()
        val repository = RecordingVaultRepository().apply { this.exportGate = exportGate }
        val viewModel = VaultViewModel(repository, FakeVaultRuntimeSecurity())
        runCurrent()
        authenticateWithDevice(viewModel)
        runCurrent()

        viewModel.exportItem(RecordingVaultRepository.sampleItem(), "/storage/emulated/0/Download")
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.lock()
        exportGate.complete(Unit)
        runCurrent()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertNull(viewModel.uiState.value.infoMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun authenticateWithDevice(viewModel: VaultViewModel) {
        assertTrue("A loaded secure policy should allow the device prompt.", viewModel.beginDeviceAuthentication())
        viewModel.onDeviceAuthenticationSucceeded()
    }

    private class FakeVaultRuntimeSecurity(
        pinConfigured: Boolean = false,
        biometricUnlockEnabled: Boolean = true,
        autoLockMinutes: Int = 5,
        private val acceptedPin: String = "1234",
    ) : VaultRuntimeSecurity {
        override val pinConfigured: StateFlow<Boolean> = MutableStateFlow(pinConfigured)
        override val biometricUnlockEnabled: Flow<Boolean> = MutableStateFlow(biometricUnlockEnabled)
        override val autoLockMinutes: Flow<Int> = MutableStateFlow(autoLockMinutes)
        val verifiedPins = mutableListOf<String>()

        override suspend fun verifyPin(pin: CharArray): Boolean {
            val supplied = String(pin)
            verifiedPins += supplied
            return supplied == acceptedPin
        }
    }

    private class RecordingVaultRepository : VaultRepository {
        var importedUri: Uri? = null
        var pathImportCount: Int = 0
        var exportDestination: String? = null
        var deleteCount: Int = 0
        var listedItems: List<FileItem> = emptyList()
        var exportGate: CompletableDeferred<Unit>? = null
        var uriImportResult: AppResult<FileItem> = AppResult.Success(sampleItem())

        override suspend fun isVaultInitialized(): Boolean = listedItems.isNotEmpty()

        override suspend fun listVaultFiles(): AppResult<List<FileItem>> =
            AppResult.Success(listedItems)

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
        ): AppResult<FileItem> {
            exportDestination = destinationDir
            exportGate?.await()
            return AppResult.Success(vaultItem)
        }

        override suspend fun deleteFromVault(vaultItem: FileItem): AppResult<Unit> {
            deleteCount += 1
            return AppResult.Success(Unit)
        }

        companion object {
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
