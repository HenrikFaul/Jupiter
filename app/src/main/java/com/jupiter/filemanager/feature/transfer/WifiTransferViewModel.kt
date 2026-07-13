package com.jupiter.filemanager.feature.transfer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.transfer.RelaySession
import com.jupiter.filemanager.data.transfer.WifiTransferServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

/**
 * UI state for the Wi-Fi transfer screen.
 *
 * @property isRunning whether the explicitly paired Relay server is currently listening.
 * @property pairingUrl the one-time LAN pairing URL while running, or null when stopped.
 * @property expiresAtMillis wall-clock expiry of the in-memory pairing session, or null when stopped.
 * @property error a human-readable error if the server failed to start.
 */
data class WifiTransferUiState(
    val isRunning: Boolean = false,
    val pairingUrl: String? = null,
    val pairingSessionId: String? = null,
    val expiresAtMillis: Long? = null,
    val error: String? = null,
)

/**
 * Owns the lifecycle of a real [WifiTransferServer] that serves the device's public Downloads
 * directory only after a QR/copy-link pairing token is presented. The server uses an OS-selected
 * local port and is torn down at the session deadline, on explicit stop and when this ViewModel is
 * cleared.
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

    private var expiryJob: Job? = null

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

    /** Starts a new in-memory, time-limited Relay session and publishes its pairing URL. */
    fun start() {
        if (server != null) return
        viewModelScope.launch {
            val dir = servedDirectory()
            val relaySession = RelaySession.create()
            val instance = WifiTransferServer(rootDir = dir, relaySession = relaySession)
            val url = try {
                instance.startServer()
            } catch (_: Throwable) {
                null
            }
            if (url != null) {
                server = instance
                _uiState.value = WifiTransferUiState(
                    isRunning = true,
                    pairingUrl = url,
                    pairingSessionId = relaySession.token.takeLast(6).uppercase(),
                    expiresAtMillis = relaySession.expiresAtMillis,
                    error = null,
                )
                expiryJob?.cancel()
                expiryJob = viewModelScope.launch {
                    delay(relaySession.remainingMillis(System.currentTimeMillis()))
                    if (server === instance) {
                        stopInternal(
                            message = "This Relay pairing session expired. Start a new session to share files again.",
                        )
                    }
                }
            } else {
                try {
                    instance.stopServer()
                } catch (_: Throwable) {
                    // ignore cleanup failure
                }
                _uiState.value = WifiTransferUiState(
                    isRunning = false,
                    pairingUrl = null,
                    error = "Couldn't start the server. Make sure you're connected to Wi-Fi.",
                )
            }
        }
    }

    /** Stops the embedded server and clears the published URL. */
    fun stop() {
        stopInternal(message = null)
    }

    private fun stopInternal(message: String?) {
        expiryJob?.cancel()
        expiryJob = null
        val running = server ?: run {
            _uiState.value = WifiTransferUiState(isRunning = false, error = message)
            return
        }
        try {
            running.stopServer()
        } catch (_: Throwable) {
            // ignore — already stopped
        }
        server = null
        _uiState.value = WifiTransferUiState(isRunning = false, error = message)
    }

    override fun onCleared() {
        super.onCleared()
        stopInternal(message = null)
    }
}
