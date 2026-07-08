package com.jupiter.filemanager.data.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jupiter.filemanager.BuildConfig
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.remote.CredentialStore
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.CloudAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Owns the Google Drive authentication and authorization flow.
 *
 * Three steps:
 *  1. [signIn] — Credential Manager account chooser → [GoogleIdentity] (email/displayName/idToken).
 *  2. [authorize] / [accessTokenFromResolution] — Identity Authorization for the Drive scope,
 *     possibly requiring a UI-launched consent resolution.
 *  3. [fetchConnectedAccount] — read the Drive storage quota with the access token, persist the
 *     token, and produce a connected [CloudAccount].
 *
 * Tokens are never logged.
 */
@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialStore: CredentialStore,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    /** Whether an OAuth Web client id is configured for this build. */
    fun isConfigured(): Boolean = BuildConfig.GDRIVE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Step 1: presents the system Google account chooser via Credential Manager and returns the
     * chosen account's [GoogleIdentity]. Returns a [AppResult.Failure] with a readable message on
     * any credential error or when no client id is configured.
     */
    suspend fun signIn(activity: Activity): AppResult<GoogleIdentity> {
        if (!isConfigured()) {
            return AppResult.Failure(
                AppError.Unknown("Google Drive isn't set up: add an OAuth Web client id"),
            )
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GDRIVE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val credentialManager = CredentialManager.create(context)
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data)
                AppResult.Success(
                    GoogleIdentity(
                        email = token.id,
                        displayName = token.displayName,
                        idToken = token.idToken,
                    ),
                )
            } else {
                AppResult.Failure(AppError.Unknown("Unexpected sign-in credential."))
            }
        } catch (e: GetCredentialException) {
            AppResult.Failure(
                AppError.Unknown(e.localizedMessage ?: "Google sign-in failed.", e),
            )
        } catch (t: Throwable) {
            AppResult.Failure(
                AppError.Unknown(t.localizedMessage ?: "Google sign-in failed.", t),
            )
        }
    }

    /** Builds the Drive-scope [AuthorizationRequest] used by [authorize]. */
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

    /**
     * Step 2: requests authorization for the Drive scope. If the result requires user consent,
     * returns [AuthorizationOutcome.NeedsConsent] carrying the [android.content.IntentSender] the
     * UI must launch; otherwise returns [AuthorizationOutcome.Token]. Failures are returned as
     * [AuthorizationOutcome.Error].
     */
    suspend fun authorize(activity: Activity): AuthorizationOutcome {
        return try {
            val result = awaitAuthorization(activity)
            if (result.hasResolution()) {
                val sender = result.pendingIntent?.intentSender
                if (sender != null) {
                    AuthorizationOutcome.NeedsConsent(sender)
                } else {
                    AuthorizationOutcome.Error("Authorization consent could not be started.")
                }
            } else {
                val accessToken = result.accessToken
                if (accessToken != null) {
                    AuthorizationOutcome.Token(accessToken)
                } else {
                    AuthorizationOutcome.Error("Drive authorization returned no access token.")
                }
            }
        } catch (t: Throwable) {
            AuthorizationOutcome.Error(t.localizedMessage ?: "Drive authorization failed.")
        }
    }

    private suspend fun awaitAuthorization(activity: Activity): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest())
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    /**
     * Extracts the access token from the [data] returned by the consent resolution launched by the
     * UI. Returns null if the result is missing or carries no token.
     */
    fun accessTokenFromResolution(activity: Activity, data: Intent?): String? {
        return try {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Step 3: reads the Drive storage quota and account info using [accessToken], persists the
     * token, and returns a connected [CloudAccount] derived from [base]. The token is never logged.
     * A missing quota limit (unlimited account) maps to `totalBytes = 0`.
     */
    suspend fun fetchConnectedAccount(
        base: CloudAccount,
        identity: GoogleIdentity,
        accessToken: String,
    ): AppResult<CloudAccount> = withContext(dispatcher) {
        try {
            val request = Request.Builder()
                .url(DRIVE_ABOUT_URL)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(
                        AppError.Io("Drive request failed (${response.code})."),
                    )
                }

                val json = JSONObject(body)
                val quota = json.optJSONObject("storageQuota")
                val usage = quota?.optString("usage")?.toLongOrNull() ?: 0L
                // "limit" is absent for unlimited accounts → 0 = unknown/unlimited.
                val limit = quota?.optString("limit")?.toLongOrNull() ?: 0L

                val user = json.optJSONObject("user")
                val userEmail = user?.optString("emailAddress").takeUnless { it.isNullOrBlank() }
                val userDisplayName = user?.optString("displayName").takeUnless { it.isNullOrBlank() }

                credentialStore.saveSecret(GDRIVE_TOKEN_PREFIX + base.id, accessToken)

                val connected = base.copy(
                    isConnected = true,
                    accountEmail = identity.email.ifBlank { userEmail.orEmpty() }
                        .ifBlank { base.accountEmail.orEmpty() }
                        .takeUnless { it.isBlank() },
                    displayName = identity.displayName
                        ?: userDisplayName
                        ?: base.displayName,
                    usedBytes = usage,
                    totalBytes = limit,
                )
                AppResult.Success(connected)
            }
        } catch (t: Throwable) {
            AppResult.Failure(
                AppError.Io(t.localizedMessage ?: "Failed to read Drive account.", t),
            )
        }
    }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_ABOUT_URL =
            "https://www.googleapis.com/drive/v3/about?fields=storageQuota,user"
        const val GDRIVE_TOKEN_PREFIX = "gdrive_token_"
    }
}
