package com.jupiter.filemanager.data.cloud

import android.content.IntentSender

/**
 * Lightweight identity returned by the Credential Manager sign-in step.
 *
 * Carries only what the connect flow needs; the [idToken] is never logged.
 */
data class GoogleIdentity(
    val email: String,
    val displayName: String?,
    val idToken: String,
)

/**
 * Outcome of the Identity authorization step (Drive scope grant).
 *
 * Authorization may either complete silently with an access [Token], or require a
 * user-visible consent screen which only the UI can launch via the [IntentSender]
 * carried by [NeedsConsent]. [Error] carries a readable failure message.
 */
sealed interface AuthorizationOutcome {

    /** Authorization succeeded silently; [accessToken] is ready to use. */
    data class Token(val accessToken: String) : AuthorizationOutcome

    /** A consent screen must be launched by the UI via [intentSender]. */
    data class NeedsConsent(val intentSender: IntentSender) : AuthorizationOutcome

    /** Authorization failed; [message] is safe to surface to the user. */
    data class Error(val message: String) : AuthorizationOutcome
}
