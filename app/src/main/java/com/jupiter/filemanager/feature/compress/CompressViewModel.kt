package com.jupiter.filemanager.feature.compress

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.media.CategorySort
import com.jupiter.filemanager.data.media.CompressPreset
import com.jupiter.filemanager.data.media.DeviceDisplayProfile
import com.jupiter.filemanager.data.media.MediaCompressor
import com.jupiter.filemanager.data.media.MediaStoreCategorySource
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.StorageCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Compress screen.
 *
 * Responsibilities:
 *  - On init, load the device's compressible media (images + videos from
 *    [MediaStoreCategorySource], largest first) into a picker and build the
 *    device/screen label from [DeviceDisplayProfile].
 *  - On [selectSource], compute the source's dimensions and the never-upscale
 *    [CompressPreset] list for the current device, defaulting the selection to
 *    the recommended preset.
 *  - On [compress], run the appropriate [MediaCompressor] path (image or video)
 *    into a sibling `*_compressed` file, streaming progress and resolving to a
 *    before/after [CompressResult].
 *
 * All blocking work happens inside the injected collaborators (which hop onto the
 * IO / Main dispatchers themselves); this ViewModel only orchestrates on
 * [viewModelScope] and never touches the filesystem or codecs on the main thread.
 */
@HiltViewModel
class CompressViewModel @Inject constructor(
    private val compressor: MediaCompressor,
    private val profile: DeviceDisplayProfile,
    private val mediaSource: MediaStoreCategorySource,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressUiState(deviceLabel = buildDeviceLabel()))
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    /** In-flight media-load job, so a refresh cancels a previous load. */
    private var loadJob: Job? = null

    /** In-flight preset-computation job for the current source pick. */
    private var selectJob: Job? = null

    /** In-flight compression job, so cancel/replace is possible. */
    private var compressJob: Job? = null

    init {
        loadMedia()
    }

    /**
     * (Re)loads the compressible media picker: all device images and videos,
     * ordered largest-first so the biggest space wins surface at the top. Never
     * throws — the underlying source returns an empty list on any failure or
     * missing permission, which the screen renders as an empty state.
     */
    fun loadMedia() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMedia = true, error = null) }

            val images = mediaSource.query(StorageCategory.IMAGES, CategorySort.SIZE_DESC)
            val videos = mediaSource.query(StorageCategory.VIDEOS, CategorySort.SIZE_DESC)

            val combined = (videos + images)
                .filter { !it.isDirectory && it.sizeBytes > 0L }
                .sortedByDescending { it.sizeBytes }

            _uiState.update { it.copy(isLoadingMedia = false, availableMedia = combined) }
        }
    }

    /**
     * Picks [item] as the compression source: decodes its dimensions and builds
     * the recommended preset list, defaulting the selection to the recommended
     * preset (or the first available). Clears any prior result/error.
     */
    fun selectSource(item: FileItem) {
        selectJob?.cancel()
        compressJob?.cancel()
        _uiState.update {
            it.copy(
                sourceItem = item,
                sourceDims = null,
                presets = emptyList(),
                selectedPreset = null,
                isCompressing = false,
                progress = 0,
                result = null,
                error = null,
            )
        }

        selectJob = viewModelScope.launch {
            val dims = compressor.dimensionsOf(item)
            val presets = profile.recommendedPresets(dims)
            val default = presets.firstOrNull { it.recommended } ?: presets.firstOrNull()
            _uiState.update {
                it.copy(
                    sourceDims = dims,
                    presets = presets,
                    selectedPreset = default,
                    // No presets means the source is already at/under every tier —
                    // nothing to gain by compressing. Surface it honestly.
                    error = if (presets.isEmpty()) {
                        "This ${typeWord(item)} is already at or below every recommended size — nothing to compress."
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** Selects [preset] as the active compression target. */
    fun selectPreset(preset: CompressPreset) {
        _uiState.update { it.copy(selectedPreset = preset) }
    }

    /** Clears the current source pick, returning the UI to the picker phase. */
    fun clearSource() {
        selectJob?.cancel()
        compressJob?.cancel()
        _uiState.update {
            it.copy(
                sourceItem = null,
                sourceDims = null,
                presets = emptyList(),
                selectedPreset = null,
                isCompressing = false,
                progress = 0,
                result = null,
                error = null,
            )
        }
    }

    /**
     * Compresses the current [CompressUiState.sourceItem] with the selected
     * preset into a sibling `<name>_compressed.<ext>` file, streaming progress and
     * publishing a before/after [CompressResult] on success.
     *
     * A no-op when there is no source/preset or a job is already running. All
     * failures are surfaced as [CompressUiState.error]; the ViewModel never throws.
     */
    fun compress() {
        val state = _uiState.value
        val item = state.sourceItem ?: return
        val preset = state.selectedPreset ?: return
        if (state.isCompressing) return

        compressJob?.cancel()
        _uiState.update { it.copy(isCompressing = true, progress = 0, result = null, error = null) }

        compressJob = viewModelScope.launch {
            val outFile = try {
                buildOutputFile(item)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isCompressing = false, error = "Could not create the output file.")
                }
                return@launch
            }

            val result: AppResult<Long> = if (item.type == FileType.VIDEO) {
                compressor.compressVideo(item, preset, outFile) { pct ->
                    _uiState.update { it.copy(progress = pct.coerceIn(0, 100)) }
                }
            } else {
                compressor.compressImage(item, preset, outFile)
            }

            when (result) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isCompressing = false,
                        progress = 100,
                        result = CompressResult(
                            originalBytes = item.sizeBytes,
                            compressedBytes = result.data,
                            outputPath = outFile.absolutePath,
                        ),
                    )
                }

                is AppResult.Failure -> {
                    // Best-effort cleanup of a partial/failed output so we never
                    // leave a corrupt file behind.
                    runCatching { if (outFile.exists()) outFile.delete() }
                    _uiState.update {
                        it.copy(
                            isCompressing = false,
                            error = result.error.displayMessage,
                        )
                    }
                }
            }
        }
    }

    /** Dismisses a surfaced error without otherwise changing state. */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // region Helpers -----------------------------------------------------------

    /**
     * Builds a non-clobbering output [File] in the source's own folder, named
     * `<base>_compressed.<ext>` (videos are normalised to `.mp4`). If that name is
     * taken, a numeric suffix is appended until a free path is found.
     */
    private fun buildOutputFile(item: FileItem): File {
        val source = File(item.path)
        val parent = source.parentFile ?: context.cacheDir
        val base = source.nameWithoutExtension.ifBlank { "media" }
        val ext = when {
            item.type == FileType.VIDEO -> "mp4"
            item.extension.isNotBlank() -> item.extension.lowercase()
            else -> "jpg"
        }

        var candidate = File(parent, "${base}_compressed.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parent, "${base}_compressed_$counter.$ext")
            counter++
        }
        return candidate
    }

    private fun typeWord(item: FileItem): String =
        if (item.type == FileType.VIDEO) "video" else "image"

    /**
     * Renders the device/screen line, e.g.
     * "Your screen: 1080×2400 — recommended 1080p". Falls back gracefully when no
     * recommended preset can be derived (unknown source at label time).
     */
    private fun buildDeviceLabel(): String {
        val display = profile.display()
        val recommended = profile.recommendedPresets(source = null)
            .firstOrNull { it.recommended }
        val suffix = recommended?.let { " — recommended ${it.label}" } ?: ""
        return "Your screen: ${display.widthPx}×${display.heightPx}$suffix"
    }

    // endregion
}
