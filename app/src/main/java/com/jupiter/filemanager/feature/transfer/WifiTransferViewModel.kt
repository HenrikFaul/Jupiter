package com.jupiter.filemanager.feature.transfer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.transfer.WifiTransferServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * UI state for the Wi-Fi transfer screen.
 *
 * @property isRunning whether the embedded HTTP server is currently listening.
 * @property url the reachable LAN URL while running (e.g. "http://192.168.1.5:8080/"),
 *               or null when stopped or when no LAN address could be determined.
 * @property error a human-readable error if the server failed to start.
 */
data class WifiTransferUiState(
    val isRunning: Boolean = false,
    val url: String? = null,
    val error: String? = null,
)

/**
 * Owns the lifecycle of a real [WifiTransferServer] that serves the device's
 * public Downloads directory over the local network.
 *
 * The server is started/stopped on demand and torn down when the ViewModel is
 * cleared so the socket is always released.
 */
@HiltViewModel
class WifiTransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiTransferUiState())
    val uiState: StateFlow<WifiTransferUiState> = _uiState.asStateFlow()

    @Volatile
    private var server: WifiTransferServer? = null

    /** Default served directory: the public Downloads folder, falling back to app files. */
    private fun servedDirectory(): File {
        val publicDownloads = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Throwable) {
            null
        }
        if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
            return publicDownloads
        }
        val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appExternal != null && (appExternal.exists() || appExternal.mkdirs())) {
            return appExternal
        }
        return context.filesDir
    }

    /** Starts the embedded server and publishes the reachable URL, or an error. */
    fun start() {
        if (server != null) return
        viewModelScope.launch {
            val dir = servedDirectory()
            val instance = WifiTransferServer(port = 8080, rootDir = dir)
            val url = try {
                instance.startServer()
            } catch (_: Throwable) {
                null
            }
            if (url != null) {
                server = instance
                _uiState.value = WifiTransferUiState(isRunning = true, url = url, error = null)
            } else {
                try {
                    instance.stopServer()
                } catch (_: Throwable) {
                    // ignore cleanup failure
                }
                _uiState.value = WifiTransferUiState(
                    isRunning = false,
                    url = null,
                    error = "Couldn't start the server. Make sure you're connected to Wi-Fi.",
                )
            }
        }
    }

    /** Stops the embedded server and clears the published URL. */
    fun stop() {
        val running = server ?: run {
            _uiState.value = WifiTransferUiState(isRunning = false, url = null, error = null)
            return
        }
        try {
            running.stopServer()
        } catch (_: Throwable) {
            // ignore — already stopped
        }
        server = null
        _uiState.value = WifiTransferUiState(isRunning = false, url = null, error = null)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            server?.stopServer()
        } catch (_: Throwable) {
            // ignore — best-effort teardown
        }
        server = null
    }
}
