# Dev Prompt — Initiative #2: Real Cloud Storage (Google Drive / Dropbox / OneDrive)

> AI coding-agent build prompt for the **real** Jupiter codebase at
> `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`.
> Stack: Kotlin 2.0.21 · Jetpack Compose · Material3 · Hilt 2.52 · Coroutines 1.9 · DataStore · OkHttp 4.12 · minSdk 26 / targetSdk 35.
> **Implement end-to-end, additively, with zero regression to existing working features.**

---

## 1. Initiative header

- **Title:** Real Cloud Storage — Google Drive / Dropbox / OneDrive (OAuth + REST)
- **Estimated value:** **+€220k – €420k**
- **Business case:** Jupiter already ships a polished *Cloud Hub* scaffold (`CloudHubScreen` + `CloudHubViewModel`) where users can "link" cloud accounts, but the entries are inert: every `CloudAccount` is surfaced as *Not connected / zero usage*, and there is no authentication or file backend. The three dominant consumer cloud providers — Google Drive, Dropbox and OneDrive — account for the overwhelming majority of personal-cloud usage, and a file manager that can browse and transfer those accounts side-by-side with local/SMB/SFTP storage becomes a genuine "single pane of glass." This initiative turns the existing scaffold into live cloud browsing and transfer by adding **AppAuth (PKCE) OAuth** plus **provider REST clients (OkHttp)** behind the *already-wired* `ConnectionRepository` / `CloudAccount` model and the `RemoteFileSource` abstraction, exactly mirroring how SMB/SFTP/FTP/WebDAV are wired in `data/remote`. Because the abstraction, persistence, navigation and Compose shell already exist, the marginal effort is concentrated on OAuth + three REST adapters, making this one of the highest value-per-line initiatives in the program.

---

## 2. Codebase context

### 2.1 Relevant real files that already exist

```
app/src/main/java/com/jupiter/filemanager/
├── core/result/
│   ├── AppResult.kt                 # sealed AppResult<Success|Failure>, onSuccess/onFailure/map/getOrNull
│   └── AppError.kt                  # sealed AppError: PermissionDenied/NotFound/AccessDenied/AlreadyExists/Io/Unknown
├── di/
│   ├── CoroutineModule.kt           # @IoDispatcher / @DefaultDispatcher / @MainDispatcher providers
│   └── RemoteModule.kt              # @Binds RemoteSourceProvider + RemoteAccessRepository
├── domain/
│   ├── model/
│   │   ├── CloudAccount.kt          # CloudProvider enum (GOOGLE_DRIVE, DROPBOX, ONEDRIVE, ICLOUD, BOX, WEBDAV) + CloudAccount
│   │   ├── RemoteConnection.kt      # ConnectionType enum (SMB/FTP/SFTP/FTPS/WEBDAV/NFS/NAS) + RemoteConnection
│   │   └── RemoteEntry.kt           # name/path/isDirectory/sizeBytes/lastModified
│   ├── remote/
│   │   ├── RemoteFileSource.kt      # type + testConnection/list/download (suspend, AppResult)
│   │   ├── RemoteCredentials.kt     # type/host/port/username/password/shareOrBasePath/domain
│   │   └── RemoteSourceProvider.kt  # sourceFor(ConnectionType): RemoteFileSource?
│   └── repository/
│       └── ConnectionRepository.kt  # observe/add/remove Remotes + CloudAccounts
├── data/
│   ├── connection/
│   │   └── ConnectionRepositoryImpl.kt   # DataStore "jupiter_connections", encodeCloudAccount = id|provider|displayName
│   └── remote/
│       ├── CredentialStore.kt            # EncryptedSharedPreferences (AES256_GCM), save/get/deletePassword(id)
│       ├── FtpFileSource.kt              # reference impl pattern (runFtp { } → AppResult)
│       ├── WebDavFileSource.kt           # reference OkHttp impl pattern
│       ├── SmbFileSource.kt / SftpFileSource.kt
│       └── RemoteSourceProviderImpl.kt   # routes ConnectionType → *FileSource
├── feature/cloud/
│   ├── CloudHubScreen.kt            # Material3 screen, add/remove account, bottom sheet
│   ├── CloudHubUiState.kt           # isLoading/accounts/showAddSheet/isEmpty
│   └── CloudHubViewModel.kt         # @HiltViewModel, observeCloudAccounts + add/remove
└── ui/navigation/
    ├── Destinations.kt              # Destination.CloudHub = "cloud_hub", RemoteBrowser pattern present
    └── JupiterNavHost.kt            # composable(Destination.CloudHub.route) { CloudHubScreen(...) }
```

### 2.2 What exists vs. missing vs. needs change

| Concern | State | Action |
|---|---|---|
| `CloudProvider` enum (GOOGLE_DRIVE/DROPBOX/ONEDRIVE) | **Exists** | reuse as-is |
| `CloudAccount` model + persistence | **Exists** (`id\|provider\|displayName`) | **needs change** — add token-id linkage (additive, backward-compatible decode) |
| `RemoteFileSource` abstraction | **Exists** | reuse; add a thin **cloud** sibling abstraction `CloudFileSource` (do NOT overload `ConnectionType`) |
| `CredentialStore` (encrypted) | **Exists** | reuse for OAuth refresh tokens (new key prefix) |
| OAuth (PKCE) | **Missing** | **NEW** `data/cloud/oauth/*` using AppAuth |
| Drive/Dropbox/OneDrive REST clients | **Missing** | **NEW** `data/cloud/source/*` (OkHttp) |
| Cloud token store + repository | **Missing** | **NEW** `data/cloud/CloudAuthRepositoryImpl.kt` + DataStore |
| Cloud browser screen | **Missing** | **NEW** `feature/cloud/browser/*` |
| Navigation to cloud browser | **Missing** | **needs change** — add `Destination.CloudBrowser` + NavHost entry |
| `CloudHubViewModel` connect/disconnect | **Partial** | **needs change** — wire real connect (launch OAuth) + disconnect |

> **Invariant:** SMB/SFTP/FTP/WebDAV and the existing inert Cloud Hub link/unlink flow must keep working unchanged. The cloud feature is added *beside* `RemoteFileSource`, never by mutating it.

---

## 3. Pre-conditions

### 3.1 Gradle dependencies to add (exact coordinates)

| Purpose | Coordinate | Notes |
|---|---|---|
| OAuth 2.0 + PKCE | `net.openid:appauth:0.11.1` | AuthorizationService, custom-tabs flow |
| JSON parsing (REST) | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` | + Kotlin serialization plugin |
| (already present) HTTP | `com.squareup.okhttp3:okhttp:4.12.0` | reuse `libs.okhttp` |
| (already present) Encrypted secrets | `androidx.security:security-crypto:1.1.0-alpha06` | reuse `CredentialStore` |
| (already present) DataStore | `androidx.datastore:datastore-preferences:1.1.1` | reuse pattern |

### 3.2 External / console prerequisites (must be done by a human, see §7)

1. **Google Cloud Console** → OAuth 2.0 *Android* client ID (package `com.jupiter.filemanager`, debug + release SHA-1) + Drive API enabled, scope `https://www.googleapis.com/auth/drive`.
2. **Dropbox App Console** → app key, scopes `files.metadata.read files.content.read account_info.read`, redirect URI registered.
3. **Microsoft Entra (Azure) App registration** → client ID, redirect URI, delegated scopes `Files.Read offline_access User.Read`.
4. **Redirect URI scheme** registered in all three consoles must match the manifest placeholder `appAuthRedirectScheme` (§4.3). Use `com.jupiter.filemanager.oauth`.
5. Client IDs are injected at build time via `local.properties` → `BuildConfig` (§7.1). **No secrets are committed.** (Public OAuth clients with PKCE do not require a client secret on Android.)

### 3.3 Manifest / permission prerequisites

- `android.permission.INTERNET` — required for REST + token exchange (not currently declared; **add it**, §4.3).
- `android.permission.ACCESS_NETWORK_STATE` — for offline detection (additive).
- AppAuth's `RedirectUriReceiverActivity` is contributed by the library manifest; we only supply the `appAuthRedirectScheme` manifest placeholder.

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml` — full replacement of the affected regions

Add to `[versions]`:

```toml
appauth = "0.11.1"
kotlinxSerialization = "1.7.3"
```

Add to `[libraries]`:

```toml
appauth = { group = "net.openid", name = "appauth", version.ref = "appauth" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

Add to `[plugins]`:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### 4.2 `app/build.gradle.kts` — exact edits

In the `plugins { }` block, append after `alias(libs.plugins.hilt)`:

```kotlin
    alias(libs.plugins.kotlin.serialization)
```

In `android { defaultConfig { } }`, append (reads keys from `local.properties`, see §7.1):

```kotlin
        // --- Cloud OAuth client IDs (injected from local.properties / CI secrets; never committed) ---
        val cloudProps = java.util.Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        fun prop(key: String): String = (cloudProps.getProperty(key) ?: "").trim()

        buildConfigField("String", "GOOGLE_DRIVE_CLIENT_ID", "\"${prop("GOOGLE_DRIVE_CLIENT_ID")}\"")
        buildConfigField("String", "DROPBOX_CLIENT_ID", "\"${prop("DROPBOX_CLIENT_ID")}\"")
        buildConfigField("String", "ONEDRIVE_CLIENT_ID", "\"${prop("ONEDRIVE_CLIENT_ID")}\"")

        // AppAuth redirect scheme placeholder (must match the consoles + manifest).
        manifestPlaceholders["appAuthRedirectScheme"] = "com.jupiter.filemanager.oauth"
```

In `dependencies { }`, append after the `// Remote / network protocols` block:

```kotlin
    // Cloud OAuth + REST (Initiative #2)
    implementation(libs.appauth)                    // OAuth 2.0 PKCE via Custom Tabs
    implementation(libs.kotlinx.serialization.json) // REST JSON parsing
    // okhttp already present (libs.okhttp) — reused for cloud REST clients
```

> `buildConfig = true` is already enabled in `buildFeatures`, so `BuildConfig` fields compile without further change.

### 4.3 `app/src/main/AndroidManifest.xml` — exact edits

Add the two `<uses-permission>` lines after the existing `POST_NOTIFICATIONS` permission:

```xml
    <!-- Cloud storage: REST + OAuth token exchange (Initiative #2). -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

No `<activity>` needs to be hand-declared: AppAuth's redirect receiver is merged from its library manifest and bound to the `appAuthRedirectScheme` placeholder set in §4.2.

### 4.4 String resources — append to `app/src/main/res/values/strings.xml`

```xml
    <!-- Cloud storage (Initiative #2) -->
    <string name="cloud_connect">Connect</string>
    <string name="cloud_disconnect">Disconnect</string>
    <string name="cloud_browse">Browse files</string>
    <string name="cloud_auth_cancelled">Sign-in cancelled</string>
    <string name="cloud_auth_failed">Could not sign in. Please try again.</string>
    <string name="cloud_not_configured">This provider is not configured in this build.</string>
    <string name="cloud_offline">No internet connection.</string>
```

---

## 5. Phase 2 — Data / domain layer

> All new code lives under the **new** package `data/cloud` plus a small `domain/cloud` abstraction. Nothing in `data/remote` or `domain/remote` is mutated.

### 5.1 Domain: cloud file-source abstraction — `domain/cloud/CloudFileSource.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.cloud

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.RemoteEntry
import java.io.File

/**
 * A cloud-provider-specific file source, the cloud analogue of
 * [com.jupiter.filemanager.domain.remote.RemoteFileSource]. Implementations are
 * stateless and authenticate every call with a freshly-resolved OAuth access
 * token (see [CloudAuthRepository.validAccessToken]). All blocking IO runs on an
 * IO dispatcher and every failure is mapped to an [AppResult.Failure] so a
 * network error can never crash the app.
 */
interface CloudFileSource {
    val provider: CloudProvider

    /** Lists children of [folderId] (provider-native id; empty string = root). */
    suspend fun list(accessToken: String, folderId: String): AppResult<List<RemoteEntry>>

    /** Streams the remote file identified by [fileId] to [destination]. */
    suspend fun download(accessToken: String, fileId: String, destination: File): AppResult<Unit>

    /** Reads the account display name + quota for a freshly connected token. */
    suspend fun accountInfo(accessToken: String): AppResult<CloudAccountInfo>
}

/** Lightweight provider account snapshot used when linking an account. */
data class CloudAccountInfo(
    val displayName: String,
    val usedBytes: Long,
    val totalBytes: Long,
)
```

> Note: `RemoteEntry.path` is reused to carry the provider-native item id (Drive fileId / Dropbox path-lower / OneDrive item id), and `isDirectory` distinguishes folders. This keeps the cloud browser able to reuse `RemoteEntry` without a new model.

### 5.2 Domain: cloud auth repository — `domain/cloud/CloudAuthRepository.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.cloud

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.CloudProvider

/**
 * Persists OAuth state for linked cloud accounts and resolves a valid (non-expired)
 * access token on demand, refreshing transparently when needed.
 */
interface CloudAuthRepository {

    /** Persists the OAuth state JSON (AppAuth) for [accountId], keyed encrypted. */
    suspend fun saveAuthState(accountId: String, authStateJson: String)

    /** Whether [accountId] currently has stored OAuth state. */
    suspend fun isLinked(accountId: String): Boolean

    /**
     * Returns a valid access token for [accountId], performing a silent refresh if
     * the cached token is expired. Failure surfaces an [AppError] (e.g. revoked).
     */
    suspend fun validAccessToken(accountId: String): AppResult<String>

    /** Removes all stored OAuth state for [accountId] (on disconnect). */
    suspend fun clearAuthState(accountId: String)

    /** The provider configured for [accountId], or null if unknown. */
    suspend fun providerFor(accountId: String): CloudProvider?
}
```

### 5.3 Data: OAuth provider config — `data/cloud/oauth/CloudOAuthConfig.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud.oauth

import android.net.Uri
import com.jupiter.filemanager.BuildConfig
import com.jupiter.filemanager.domain.model.CloudProvider

/**
 * Static OAuth endpoint + client configuration per provider. Client IDs are
 * injected at build time via BuildConfig (see app/build.gradle.kts). A provider
 * whose client id is blank is treated as "not configured in this build".
 */
data class CloudOAuthConfig(
    val provider: CloudProvider,
    val clientId: String,
    val authEndpoint: Uri,
    val tokenEndpoint: Uri,
    val redirectUri: Uri,
    val scopes: List<String>,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()

    companion object {
        const val REDIRECT_SCHEME = "com.jupiter.filemanager.oauth"
        private val REDIRECT = Uri.parse("$REDIRECT_SCHEME:/oauth2redirect")

        fun forProvider(provider: CloudProvider): CloudOAuthConfig? = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> CloudOAuthConfig(
                provider = provider,
                clientId = BuildConfig.GOOGLE_DRIVE_CLIENT_ID,
                authEndpoint = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
                tokenEndpoint = Uri.parse("https://oauth2.googleapis.com/token"),
                redirectUri = REDIRECT,
                scopes = listOf("https://www.googleapis.com/auth/drive"),
            )
            CloudProvider.DROPBOX -> CloudOAuthConfig(
                provider = provider,
                clientId = BuildConfig.DROPBOX_CLIENT_ID,
                authEndpoint = Uri.parse("https://www.dropbox.com/oauth2/authorize"),
                tokenEndpoint = Uri.parse("https://api.dropboxapi.com/oauth2/token"),
                redirectUri = REDIRECT,
                scopes = listOf("files.metadata.read", "files.content.read", "account_info.read"),
            )
            CloudProvider.ONEDRIVE -> CloudOAuthConfig(
                provider = provider,
                clientId = BuildConfig.ONEDRIVE_CLIENT_ID,
                authEndpoint = Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
                tokenEndpoint = Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token"),
                redirectUri = REDIRECT,
                scopes = listOf("Files.Read", "offline_access", "User.Read"),
            )
            // Not in scope for this initiative.
            CloudProvider.ICLOUD, CloudProvider.BOX, CloudProvider.WEBDAV -> null
        }
    }
}
```

### 5.4 Data: AppAuth wrapper — `data/cloud/oauth/CloudOAuthService.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud.oauth

import android.content.Context
import android.content.Intent
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.CloudProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.ResponseTypeValues

/**
 * Thin wrapper over the AppAuth [AuthorizationService] that builds PKCE
 * authorization intents and exchanges authorization codes for tokens, returning
 * an [AuthState] JSON string suitable for encrypted persistence. The Custom-Tabs
 * UI itself is launched by the caller (an ActivityResultLauncher in the screen),
 * because AppAuth requires an Activity context for the browser flow.
 */
@Singleton
class CloudOAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun authService(): AuthorizationService = AuthorizationService(context)

    /** Builds the browser authorization Intent for [provider], or null if unconfigured. */
    fun buildAuthIntent(service: AuthorizationService, provider: CloudProvider): Intent? {
        val cfg = CloudOAuthConfig.forProvider(provider)?.takeIf { it.isConfigured } ?: return null
        val serviceConfig = AuthorizationServiceConfiguration(cfg.authEndpoint, cfg.tokenEndpoint)
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            cfg.clientId,
            ResponseTypeValues.CODE,
            cfg.redirectUri,
        )
            .setScopes(cfg.scopes)
            // Google requires offline access + consent to return a refresh token.
            .setAdditionalParameters(
                if (provider == CloudProvider.GOOGLE_DRIVE) {
                    mapOf("access_type" to "offline", "prompt" to "consent")
                } else {
                    emptyMap()
                },
            )
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /**
     * Completes the flow: exchanges the authorization code carried in [responseData]
     * for tokens and returns the serialized [AuthState] JSON on success.
     */
    suspend fun completeAuthorization(
        service: AuthorizationService,
        responseData: Intent,
    ): AppResult<String> {
        val resp = AuthorizationResponse.fromIntent(responseData)
        val ex = AuthorizationException.fromIntent(responseData)
        if (resp == null) {
            return AppResult.Failure(
                AppError.Io(detail = ex?.errorDescription ?: "Authorization cancelled", cause = ex),
            )
        }
        val authState = AuthState(resp, ex)
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                if (tokenResp != null) {
                    authState.update(tokenResp, tokenEx)
                    cont.resume(AppResult.Success(authState.jsonSerializeString()))
                } else {
                    cont.resume(
                        AppResult.Failure(
                            AppError.Io(
                                detail = tokenEx?.errorDescription ?: "Token exchange failed",
                                cause = tokenEx,
                            ),
                        ),
                    )
                }
            }
        }
    }
}
```

### 5.5 Data: cloud token store — `data/cloud/CloudTokenStore.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores AppAuth [AuthState] JSON (including refresh tokens) at rest, keyed by
 * cloud account id. Mirrors [com.jupiter.filemanager.data.remote.CredentialStore]:
 * EncryptedSharedPreferences with a graceful plaintext fallback so the app never
 * crashes on KeyStore corruption. Tokens never touch the unencrypted DataStore.
 */
@Singleton
class CloudTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveAuthState(accountId: String, json: String) =
        runCatching { prefs.edit().putString(KEY_PREFIX + accountId, json).apply() }.let {}

    fun getAuthState(accountId: String): String? =
        runCatching { prefs.getString(KEY_PREFIX + accountId, null) }.getOrNull()

    fun deleteAuthState(accountId: String) =
        runCatching { prefs.edit().remove(KEY_PREFIX + accountId).apply() }.let {}

    fun saveProvider(accountId: String, providerName: String) =
        runCatching { prefs.edit().putString(PROVIDER_PREFIX + accountId, providerName).apply() }.let {}

    fun getProvider(accountId: String): String? =
        runCatching { prefs.getString(PROVIDER_PREFIX + accountId, null) }.getOrNull()

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "jupiter_cloud_tokens"
        const val FALLBACK_PREFS_NAME = "jupiter_cloud_tokens_fallback"
        const val KEY_PREFIX = "auth_"
        const val PROVIDER_PREFIX = "prov_"
    }
}
```

### 5.6 Data: cloud auth repository impl — `data/cloud/CloudAuthRepositoryImpl.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.cloud.oauth.CloudOAuthConfig
import com.jupiter.filemanager.data.cloud.oauth.CloudOAuthService
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.cloud.CloudAuthRepository
import com.jupiter.filemanager.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.NoClientAuthentication

/**
 * [CloudAuthRepository] backed by [CloudTokenStore]. Token refresh is delegated to
 * AppAuth's [AuthState.performActionWithFreshTokens], which performs a silent
 * refresh-token grant when the cached access token is expired.
 */
@Singleton
class CloudAuthRepositoryImpl @Inject constructor(
    private val tokenStore: CloudTokenStore,
    private val oauthService: CloudOAuthService,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CloudAuthRepository {

    override suspend fun saveAuthState(accountId: String, authStateJson: String) =
        withContext(io) { tokenStore.saveAuthState(accountId, authStateJson) }

    override suspend fun isLinked(accountId: String): Boolean =
        withContext(io) { tokenStore.getAuthState(accountId) != null }

    override suspend fun providerFor(accountId: String): CloudProvider? = withContext(io) {
        tokenStore.getProvider(accountId)?.let { name ->
            runCatching { CloudProvider.valueOf(name) }.getOrNull()
        }
    }

    override suspend fun clearAuthState(accountId: String) =
        withContext(io) { tokenStore.deleteAuthState(accountId) }

    override suspend fun validAccessToken(accountId: String): AppResult<String> = withContext(io) {
        val json = tokenStore.getAuthState(accountId)
            ?: return@withContext AppResult.Failure(AppError.Io("Account not linked"))
        val authState = runCatching { AuthState.jsonDeserialize(json) }.getOrNull()
            ?: return@withContext AppResult.Failure(AppError.Io("Corrupt auth state; reconnect required"))

        val service = oauthService.authService()
        try {
            suspendCancellableCoroutine { cont ->
                val clientAuth: ClientAuthentication = NoClientAuthentication.INSTANCE
                authState.performActionWithFreshTokens(service, clientAuth) { accessToken, _, ex ->
                    // Persist any rotated refresh/access token before returning.
                    tokenStore.saveAuthState(accountId, authState.jsonSerializeString())
                    when {
                        accessToken != null -> cont.resume(AppResult.Success(accessToken))
                        else -> cont.resume(
                            AppResult.Failure(
                                AppError.Io(
                                    detail = ex?.errorDescription ?: "Token refresh failed; reconnect required",
                                    cause = ex,
                                ),
                            ),
                        )
                    }
                }
            }
        } finally {
            service.dispose()
        }
    }

    /** Provider lookup used by the source provider (kept here for cohesion). */
    fun configFor(provider: CloudProvider): CloudOAuthConfig? = CloudOAuthConfig.forProvider(provider)
}
```

### 5.7 Data: shared OkHttp REST helper — `data/cloud/source/CloudHttp.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud.source

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Shared, lazily-built OkHttp client + helpers for cloud REST sources. */
internal object CloudHttp {

    val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    /** Executes [request], mapping transport/HTTP errors to [AppResult.Failure]. */
    inline fun <T> execute(request: Request, transform: (Response) -> T): AppResult<T> = try {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                AppResult.Failure(mapHttpError(resp.code, resp.message))
            } else {
                AppResult.Success(transform(resp))
            }
        }
    } catch (e: IOException) {
        AppResult.Failure(AppError.Io(detail = e.message ?: "Network error", cause = e))
    } catch (t: Throwable) {
        AppResult.Failure(AppError.Unknown(detail = t.message ?: "Cloud request failed", cause = t))
    }

    fun streamToFile(resp: Response, destination: File): AppResult<Unit> = try {
        destination.parentFile?.mkdirs()
        resp.body?.byteStream()?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: return AppResult.Failure(AppError.Io("Empty response body"))
        AppResult.Success(Unit)
    } catch (e: IOException) {
        AppResult.Failure(AppError.Io(detail = e.message ?: "Download failed", cause = e))
    }

    fun mapHttpError(code: Int, message: String): AppError = when (code) {
        401, 403 -> AppError.AccessDenied("HTTP $code: authorization expired or insufficient scope")
        404 -> AppError.NotFound("HTTP 404")
        429 -> AppError.Io("Rate limited (HTTP 429); please retry shortly")
        else -> AppError.Io("HTTP $code: $message")
    }
}
```

### 5.8 Data: Google Drive source — `data/cloud/source/GoogleDriveFileSource.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud.source

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.cloud.CloudAccountInfo
import com.jupiter.filemanager.domain.cloud.CloudFileSource
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.RemoteEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/**
 * Google Drive v3 [CloudFileSource]. Uses the Files.list endpoint with a parent
 * query, and alt=media for downloads. Folder ids are Drive file ids; the root is
 * the literal "root". All blocking IO runs on the injected IO dispatcher.
 */
@Singleton
class GoogleDriveFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : CloudFileSource {

    override val provider: CloudProvider = CloudProvider.GOOGLE_DRIVE

    override suspend fun list(accessToken: String, folderId: String): AppResult<List<RemoteEntry>> =
        withContext(io) {
            val parent = folderId.ifEmpty { "root" }
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$parent' in parents and trashed = false")
                .addQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime)")
                .addQueryParameter("pageSize", "1000")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            CloudHttp.execute(request) { resp ->
                val body = JSONObject(resp.body!!.string())
                val files = body.optJSONArray("files") ?: return@execute emptyList<RemoteEntry>()
                (0 until files.length()).map { i ->
                    val o = files.getJSONObject(i)
                    val isDir = o.optString("mimeType") == "application/vnd.google-apps.folder"
                    RemoteEntry(
                        name = o.optString("name"),
                        path = o.optString("id"),
                        isDirectory = isDir,
                        sizeBytes = o.optString("size").toLongOrNull() ?: 0L,
                        lastModified = parseIso(o.optString("modifiedTime")),
                    )
                }
            }
        }

    override suspend fun download(accessToken: String, fileId: String, destination: File): AppResult<Unit> =
        withContext(io) {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "media")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            CloudHttp.execute(request) { it }.let { result ->
                when (result) {
                    is AppResult.Success -> CloudHttp.streamToFile(result.data, destination)
                    is AppResult.Failure -> result
                }
            }
        }

    override suspend fun accountInfo(accessToken: String): AppResult<CloudAccountInfo> = withContext(io) {
        val url = "https://www.googleapis.com/drive/v3/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(displayName),storageQuota(limit,usage)")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
        CloudHttp.execute(request) { resp ->
            val o = JSONObject(resp.body!!.string())
            val quota = o.optJSONObject("storageQuota")
            CloudAccountInfo(
                displayName = o.optJSONObject("user")?.optString("displayName").orEmpty().ifEmpty { "Google Drive" },
                usedBytes = quota?.optString("usage")?.toLongOrNull() ?: 0L,
                totalBytes = quota?.optString("limit")?.toLongOrNull() ?: 0L,
            )
        }
    }

    private fun parseIso(s: String): Long =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
}
```

### 5.9 Data: Dropbox source — `data/cloud/source/DropboxFileSource.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud.source

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.cloud.CloudAccountInfo
import com.jupiter.filemanager.domain.cloud.CloudFileSource
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.RemoteEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Dropbox v2 [CloudFileSource]. Folder ids are Dropbox path-lower strings; the
 * root is the empty string "". list_folder is a POST with a JSON body; download
 * uses the content endpoint with the path passed via the Dropbox-API-Arg header.
 */
@Singleton
class DropboxFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : CloudFileSource {

    override val provider: CloudProvider = CloudProvider.DROPBOX
    private val json = "application/json".toMediaType()

    override suspend fun list(accessToken: String, folderId: String): AppResult<List<RemoteEntry>> =
        withContext(io) {
            val bodyJson = JSONObject().put("path", folderId).put("recursive", false).toString()
            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/list_folder")
                .header("Authorization", "Bearer $accessToken")
                .post(bodyJson.toRequestBody(json))
                .build()
            CloudHttp.execute(request) { resp ->
                val entries = JSONObject(resp.body!!.string()).optJSONArray("entries")
                    ?: return@execute emptyList<RemoteEntry>()
                (0 until entries.length()).map { i ->
                    val o = entries.getJSONObject(i)
                    val isDir = o.optString(".tag") == "folder"
                    RemoteEntry(
                        name = o.optString("name"),
                        path = o.optString("path_lower"),
                        isDirectory = isDir,
                        sizeBytes = o.optLong("size", 0L),
                        lastModified = parseIso(o.optString("server_modified")),
                    )
                }
            }
        }

    override suspend fun download(accessToken: String, fileId: String, destination: File): AppResult<Unit> =
        withContext(io) {
            val arg = JSONObject().put("path", fileId).toString()
            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .header("Authorization", "Bearer $accessToken")
                .header("Dropbox-API-Arg", arg)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            when (val r = CloudHttp.execute(request) { it }) {
                is AppResult.Success -> CloudHttp.streamToFile(r.data, destination)
                is AppResult.Failure -> r
            }
        }

    override suspend fun accountInfo(accessToken: String): AppResult<CloudAccountInfo> = withContext(io) {
        val acctReq = Request.Builder()
            .url("https://api.dropboxapi.com/2/users/get_current_account")
            .header("Authorization", "Bearer $accessToken")
            .post("null".toRequestBody(json))
            .build()
        CloudHttp.execute(acctReq) { resp ->
            val o = JSONObject(resp.body!!.string())
            val name = o.optJSONObject("name")?.optString("display_name").orEmpty().ifEmpty { "Dropbox" }
            CloudAccountInfo(displayName = name, usedBytes = 0L, totalBytes = 0L)
        }
    }

    private fun parseIso(s: String): Long =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
}
```

### 5.10 Data: OneDrive source — `data/cloud/source/OneDriveFileSource.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud.source

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.cloud.CloudAccountInfo
import com.jupiter.filemanager.domain.cloud.CloudFileSource
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.RemoteEntry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Microsoft Graph (OneDrive) [CloudFileSource]. Folder ids are driveItem ids; the
 * root is the empty string "" which maps to /me/drive/root/children.
 */
@Singleton
class OneDriveFileSource @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) : CloudFileSource {

    override val provider: CloudProvider = CloudProvider.ONEDRIVE

    override suspend fun list(accessToken: String, folderId: String): AppResult<List<RemoteEntry>> =
        withContext(io) {
            val url = if (folderId.isEmpty()) {
                "https://graph.microsoft.com/v1.0/me/drive/root/children"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children"
            }
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            CloudHttp.execute(request) { resp ->
                val values = JSONObject(resp.body!!.string()).optJSONArray("value")
                    ?: return@execute emptyList<RemoteEntry>()
                (0 until values.length()).map { i ->
                    val o = values.getJSONObject(i)
                    RemoteEntry(
                        name = o.optString("name"),
                        path = o.optString("id"),
                        isDirectory = o.has("folder"),
                        sizeBytes = o.optLong("size", 0L),
                        lastModified = parseIso(o.optString("lastModifiedDateTime")),
                    )
                }
            }
        }

    override suspend fun download(accessToken: String, fileId: String, destination: File): AppResult<Unit> =
        withContext(io) {
            val url = "https://graph.microsoft.com/v1.0/me/drive/items/$fileId/content"
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
            when (val r = CloudHttp.execute(request) { it }) {
                is AppResult.Success -> CloudHttp.streamToFile(r.data, destination)
                is AppResult.Failure -> r
            }
        }

    override suspend fun accountInfo(accessToken: String): AppResult<CloudAccountInfo> = withContext(io) {
        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive")
            .header("Authorization", "Bearer $accessToken").get().build()
        CloudHttp.execute(request) { resp ->
            val o = JSONObject(resp.body!!.string())
            val quota = o.optJSONObject("quota")
            val owner = o.optJSONObject("owner")?.optJSONObject("user")?.optString("displayName")
            CloudAccountInfo(
                displayName = owner.orEmpty().ifEmpty { "OneDrive" },
                usedBytes = quota?.optLong("used", 0L) ?: 0L,
                totalBytes = quota?.optLong("total", 0L) ?: 0L,
            )
        }
    }

    private fun parseIso(s: String): Long =
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
}
```

### 5.11 Data: cloud source provider — `data/cloud/CloudSourceProviderImpl.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.cloud

import com.jupiter.filemanager.data.cloud.source.DropboxFileSource
import com.jupiter.filemanager.data.cloud.source.GoogleDriveFileSource
import com.jupiter.filemanager.data.cloud.source.OneDriveFileSource
import com.jupiter.filemanager.domain.cloud.CloudFileSource
import com.jupiter.filemanager.domain.cloud.CloudSourceProvider
import com.jupiter.filemanager.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Routes a [CloudProvider] to its REST [CloudFileSource], mirroring RemoteSourceProviderImpl. */
@Singleton
class CloudSourceProviderImpl @Inject constructor(
    private val drive: GoogleDriveFileSource,
    private val dropbox: DropboxFileSource,
    private val oneDrive: OneDriveFileSource,
) : CloudSourceProvider {
    override fun sourceFor(provider: CloudProvider): CloudFileSource? = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> drive
        CloudProvider.DROPBOX -> dropbox
        CloudProvider.ONEDRIVE -> oneDrive
        CloudProvider.ICLOUD, CloudProvider.BOX, CloudProvider.WEBDAV -> null
    }
}
```

`domain/cloud/CloudSourceProvider.kt` (NEW):

```kotlin
package com.jupiter.filemanager.domain.cloud

import com.jupiter.filemanager.domain.model.CloudProvider

/** Resolves the [CloudFileSource] for a [CloudProvider], or null if unsupported. */
interface CloudSourceProvider {
    fun sourceFor(provider: CloudProvider): CloudFileSource?
}
```

### 5.12 Hilt module — `di/CloudModule.kt` (NEW)

```kotlin
package com.jupiter.filemanager.di

import com.jupiter.filemanager.data.cloud.CloudAuthRepositoryImpl
import com.jupiter.filemanager.data.cloud.CloudSourceProviderImpl
import com.jupiter.filemanager.domain.cloud.CloudAuthRepository
import com.jupiter.filemanager.domain.cloud.CloudSourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds cloud-storage data-layer implementations to their domain interfaces.
 * Each impl is a @Singleton with an @Inject constructor, so Hilt constructs them;
 * this module only declares the interface bindings. Mirrors [RemoteModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {

    @Binds
    abstract fun bindCloudSourceProvider(impl: CloudSourceProviderImpl): CloudSourceProvider

    @Binds
    abstract fun bindCloudAuthRepository(impl: CloudAuthRepositoryImpl): CloudAuthRepository
}
```

> `CloudOAuthService`, `CloudTokenStore`, and the three `*FileSource` classes are all `@Singleton` + `@Inject` constructor, so no `@Provides` is needed. AppAuth's `AuthorizationService` is created per-flow (it must be `dispose()`d), so it is **not** a Hilt singleton.

---

## 6. Phase 3 — Presentation

### 6.1 Navigation: `ui/navigation/Destinations.kt` — additive edit

Add after the existing `CloudHub` destination (do not remove anything):

```kotlin
    data object CloudBrowser : Destination("cloud_browser?accountId={accountId}&folderId={folderId}&title={title}") {
        const val ARG_ACCOUNT = "accountId"
        const val ARG_FOLDER = "folderId"
        const val ARG_TITLE = "title"
        fun create(accountId: String, folderId: String, title: String): String =
            "cloud_browser?accountId=" + android.net.Uri.encode(accountId) +
                "&folderId=" + android.net.Uri.encode(folderId) +
                "&title=" + android.net.Uri.encode(title)
    }
```

### 6.2 Cloud browser UiState — `feature/cloud/browser/CloudBrowserUiState.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.cloud.browser

import com.jupiter.filemanager.domain.model.RemoteEntry

/**
 * Immutable UI state for [CloudBrowserScreen].
 *
 * @param isLoading true while a directory listing or download is in flight.
 * @param title the folder/account name shown in the app bar.
 * @param entries the listed children, folders first then by name.
 * @param errorMessage a user-facing error to surface in a snackbar, or null.
 * @param downloadingName name of the file currently downloading, or null.
 */
data class CloudBrowserUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val entries: List<RemoteEntry> = emptyList(),
    val errorMessage: String? = null,
    val downloadingName: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty() && errorMessage == null
}
```

### 6.3 Cloud browser ViewModel — `feature/cloud/browser/CloudBrowserViewModel.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.cloud.browser

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.cloud.CloudAuthRepository
import com.jupiter.filemanager.domain.cloud.CloudSourceProvider
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs [CloudBrowserScreen]. Resolves a valid access token via
 * [CloudAuthRepository], lists the current cloud folder via [CloudSourceProvider],
 * and downloads files into the public Downloads directory. Never throws across the
 * boundary: every failure is surfaced as [CloudBrowserUiState.errorMessage].
 */
@HiltViewModel
class CloudBrowserViewModel @Inject constructor(
    private val authRepository: CloudAuthRepository,
    private val sourceProvider: CloudSourceProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: String = savedStateHandle[Destination.CloudBrowser.ARG_ACCOUNT] ?: ""
    private val folderId: String = savedStateHandle[Destination.CloudBrowser.ARG_FOLDER] ?: ""
    private val title: String = savedStateHandle[Destination.CloudBrowser.ARG_TITLE] ?: "Cloud"

    private val _uiState = MutableStateFlow(CloudBrowserUiState(title = title))
    val uiState: StateFlow<CloudBrowserUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val provider = authRepository.providerFor(accountId)
            val source = provider?.let { sourceProvider.sourceFor(it) }
            if (source == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Provider not supported in this build.") }
                return@launch
            }
            when (val token = authRepository.validAccessToken(accountId)) {
                is AppResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = token.error.displayMessage) }
                is AppResult.Success ->
                    when (val listed = source.list(token.data, folderId)) {
                        is AppResult.Success -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                entries = listed.data.sortedWith(
                                    compareByDescending<RemoteEntry> { e -> e.isDirectory }
                                        .thenBy { e -> e.name.lowercase() },
                                ),
                            )
                        }
                        is AppResult.Failure ->
                            _uiState.update { it.copy(isLoading = false, errorMessage = listed.error.displayMessage) }
                    }
            }
        }
    }

    fun onDownload(entry: RemoteEntry) {
        if (entry.isDirectory) return
        _uiState.update { it.copy(downloadingName = entry.name, errorMessage = null) }
        viewModelScope.launch {
            val provider = authRepository.providerFor(accountId)
            val source = provider?.let { sourceProvider.sourceFor(it) }
            val tokenResult = authRepository.validAccessToken(accountId)
            if (source == null || tokenResult is AppResult.Failure) {
                val msg = (tokenResult as? AppResult.Failure)?.error?.displayMessage ?: "Provider unavailable"
                _uiState.update { it.copy(downloadingName = null, errorMessage = msg) }
                return@launch
            }
            val token = (tokenResult as AppResult.Success).data
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dest = File(dir, entry.name)
            when (val r = source.download(token, entry.path, dest)) {
                is AppResult.Success ->
                    _uiState.update { it.copy(downloadingName = null, errorMessage = "Saved to Downloads/${entry.name}") }
                is AppResult.Failure ->
                    _uiState.update { it.copy(downloadingName = null, errorMessage = r.error.displayMessage) }
            }
        }
    }

    fun onErrorShown() { _uiState.update { it.copy(errorMessage = null) } }
}
```

### 6.4 Cloud browser screen — `feature/cloud/browser/CloudBrowserScreen.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.cloud.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.domain.model.RemoteEntry
import com.jupiter.filemanager.ui.components.EmptyView
import com.jupiter.filemanager.ui.components.LoadingView

/**
 * Browses a single linked cloud account's folder. Folders navigate deeper via
 * [onOpenFolder]; files are downloaded to the public Downloads directory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBrowserScreen(
    onOpenFolder: (accountId: String, folderId: String, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: CloudBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingView(modifier = Modifier.fillMaxSize().padding(padding))
            state.isEmpty -> EmptyView(
                title = "Empty folder",
                message = "No files here.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.path.ifEmpty { it.name } }) { entry ->
                    CloudEntryRow(
                        entry = entry,
                        isDownloading = state.downloadingName == entry.name,
                        onClick = {
                            if (entry.isDirectory) onOpenFolder(viewModel.accountIdArg, entry.path, entry.name)
                            else viewModel.onDownload(entry)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudEntryRow(
    entry: RemoteEntry,
    isDownloading: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.name) },
        supportingContent = {
            if (!entry.isDirectory) Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
            )
        },
        trailingContent = {
            when {
                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                !entry.isDirectory -> Icon(Icons.Filled.Download, contentDescription = "Download")
                else -> null
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
```

> Add a tiny convenience accessor to the ViewModel so the screen can pass the account id back into `onOpenFolder`:
> ```kotlin
>     val accountIdArg: String get() = accountId
> ```
> (`EmptyView` and `LoadingView` already exist in `ui/components` — they are used by `CloudHubScreen`.)

### 6.5 NavHost wiring — `ui/navigation/JupiterNavHost.kt` — additive edit

Add the import next to the existing cloud imports:

```kotlin
import com.jupiter.filemanager.feature.cloud.browser.CloudBrowserScreen
```

Add this `composable` block immediately after the existing `Destination.NasConnections` block (before/around the `RemoteBrowser` block). Also update the existing `CloudHub` composable so a connected account can open the browser:

```kotlin
        composable(route = Destination.CloudHub.route) {
            CloudHubScreen(
                onBack = { navController.popBackStack() },
                onOpenAccount = { accountId, displayName ->
                    navController.navigate(Destination.CloudBrowser.create(accountId, "", displayName))
                },
            )
        }

        composable(
            route = Destination.CloudBrowser.route,
            arguments = listOf(
                navArgument(Destination.CloudBrowser.ARG_ACCOUNT) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument(Destination.CloudBrowser.ARG_FOLDER) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument(Destination.CloudBrowser.ARG_TITLE) {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) {
            CloudBrowserScreen(
                onOpenFolder = { accountId, folderId, title ->
                    navController.navigate(Destination.CloudBrowser.create(accountId, folderId, title))
                },
                onBack = { navController.popBackStack() },
            )
        }
```

### 6.6 Cloud Hub: real connect/disconnect — `feature/cloud/CloudHubViewModel.kt` (additive replacement)

The current `onAddAccount` only persists an inert entry. Extend the ViewModel to (a) expose a one-shot "launch OAuth" event and (b) finalize linking after the browser returns. The Compose-side Custom-Tabs launch is done by the screen with an `ActivityResultLauncher` (AppAuth requires an Activity).

Full replacement of `CloudHubViewModel.kt`:

```kotlin
package com.jupiter.filemanager.feature.cloud

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.cloud.oauth.CloudOAuthService
import com.jupiter.filemanager.domain.cloud.CloudAuthRepository
import com.jupiter.filemanager.domain.cloud.CloudSourceProvider
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService

/**
 * Backs [CloudHubScreen]. Streams linked accounts and now drives the real OAuth
 * connect flow: it builds the AppAuth authorization Intent (handed to the screen
 * to launch via Custom Tabs), then on the result exchanges the code for tokens,
 * persists encrypted auth state, fetches account info and links the account.
 *
 * Backwards compatible: the legacy inert add/remove paths still work for providers
 * that are not configured in this build.
 */
@HiltViewModel
class CloudHubViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val authRepository: CloudAuthRepository,
    private val sourceProvider: CloudSourceProvider,
    private val oauthService: CloudOAuthService,
) : ViewModel() {

    private val showAddSheet = MutableStateFlow(false)

    val uiState: StateFlow<CloudHubUiState> =
        combine(connectionRepository.observeCloudAccounts(), showAddSheet) { accounts, showSheet ->
            CloudHubUiState(isLoading = false, accounts = accounts, showAddSheet = showSheet)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudHubUiState(isLoading = true))

    /** Pending account id awaiting an OAuth result, mapped to provider. */
    private var pendingAccountId: String? = null
    private var pendingProvider: CloudProvider? = null
    private var activeService: AuthorizationService? = null

    fun onAddRequested() { showAddSheet.value = true }
    fun onDismissAddSheet() { showAddSheet.value = false }

    /**
     * Begins linking [provider]. Returns the authorization Intent to launch, or
     * null if the provider is not configured in this build (caller shows a message).
     */
    fun beginConnect(provider: CloudProvider, displayName: String): Intent? {
        showAddSheet.value = false
        val service = oauthService.authService()
        val intent = oauthService.buildAuthIntent(service, provider) ?: run {
            service.dispose(); return null
        }
        activeService = service
        pendingProvider = provider
        // Reserve an account id up-front so token persistence is keyed deterministically.
        pendingAccountId = java.util.UUID.randomUUID().toString()
        pendingDisplayName = displayName.ifBlank { provider.name }
        return intent
    }

    private var pendingDisplayName: String = ""

    /** Called by the screen with the Custom-Tabs result Intent. */
    fun onAuthResult(data: Intent?) {
        val service = activeService
        val accountId = pendingAccountId
        val provider = pendingProvider
        if (service == null || accountId == null || provider == null || data == null) {
            cleanupPending(); return
        }
        viewModelScope.launch {
            try {
                when (val tokenJson = oauthService.completeAuthorization(service, data)) {
                    is AppResult.Failure -> { /* surface via snackbar in screen if desired */ }
                    is AppResult.Success -> {
                        authRepository.saveAuthState(accountId, tokenJson.data)
                        // Persist provider name alongside tokens for later lookup.
                        // (CloudTokenStore.saveProvider is invoked through repository extension below.)
                        (authRepository as? com.jupiter.filemanager.data.cloud.CloudAuthRepositoryImpl)
                            ?.let { /* provider saved via token store */ }
                        val source = sourceProvider.sourceFor(provider)
                        val token = (authRepository.validAccessToken(accountId) as? AppResult.Success)?.data
                        val info = if (source != null && token != null) {
                            (source.accountInfo(token) as? AppResult.Success)?.data
                        } else null
                        connectionRepository.addCloudAccount(provider, info?.displayName ?: pendingDisplayName)
                    }
                }
            } finally {
                cleanupPending()
            }
        }
    }

    private fun cleanupPending() {
        activeService?.dispose()
        activeService = null
        pendingAccountId = null
        pendingProvider = null
    }

    /** Legacy inert add path, kept for unconfigured providers. */
    fun onAddAccount(provider: CloudProvider, displayName: String) {
        val name = displayName.trim()
        showAddSheet.value = false
        if (name.isEmpty()) return
        viewModelScope.launch { connectionRepository.addCloudAccount(provider, name) }
    }

    fun onRemoveAccount(id: String) {
        viewModelScope.launch {
            authRepository.clearAuthState(id)
            connectionRepository.removeCloudAccount(id)
        }
    }
}
```

> **Implementation note for the agent:** to keep provider lookup robust, also call `tokenStore.saveProvider(accountId, provider.name)` inside `CloudAuthRepositoryImpl.saveAuthState` is *not* sufficient (provider isn't known there); instead add a dedicated `linkProvider(accountId, provider)` method to `CloudAuthRepository`/Impl that writes `tokenStore.saveProvider(...)`, and call it from `onAuthResult` right after `saveAuthState`. Wire `CloudAuthRepositoryImpl.providerFor` to read it (already shown in §5.6). This avoids the reflective cast above — replace the cast with the clean call. (The cast is illustrative only; the agent must implement `linkProvider`.)

`CloudHubScreen.kt` — add an `onOpenAccount` parameter and an `ActivityResultLauncher` that launches `beginConnect(...)`'s Intent and forwards the result to `onAuthResult(...)`:

```kotlin
// In the @Composable signature:
fun CloudHubScreen(
    onBack: () -> Unit,
    onOpenAccount: (accountId: String, displayName: String) -> Unit = { _, _ -> },
    viewModel: CloudHubViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> viewModel.onAuthResult(result.data) }

    // In the provider-select sheet's confirm button:
    // val intent = viewModel.beginConnect(selectedProvider, name)
    // if (intent != null) authLauncher.launch(intent)
    // else Toast.makeText(context, R.string.cloud_not_configured, Toast.LENGTH_SHORT).show()
    // ...
    // On a connected account row tap: onOpenAccount(account.id, account.displayName)
}
```
Required new imports for `CloudHubScreen.kt`:
```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.jupiter.filemanager.R
```

---

## 7. Phase 4 — Configuration

### 7.1 `local.properties` (developer machine / CI secret) — NOT committed

```properties
GOOGLE_DRIVE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
DROPBOX_CLIENT_ID=xxxxxxxxxxxxxxx
ONEDRIVE_CLIENT_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```
On CI, inject these via a step that appends them to `local.properties` from repository secrets before the Gradle build. Blank values are tolerated — the corresponding provider renders as "not configured" and the OAuth Intent is null (no crash).

### 7.2 External service setup (human, one-time)

| Provider | Console URL | What to create |
|---|---|---|
| Google Drive | https://console.cloud.google.com/apis/credentials | OAuth client (Android), enable Drive API (https://console.cloud.google.com/apis/library/drive.googleapis.com), add SHA-1 of debug+release keystores, package `com.jupiter.filemanager` |
| Dropbox | https://www.dropbox.com/developers/apps | "Scoped access" app, add redirect URI `com.jupiter.filemanager.oauth:/oauth2redirect`, enable scopes `files.metadata.read files.content.read account_info.read` |
| OneDrive | https://entra.microsoft.com (App registrations) | Public client / mobile, redirect URI `com.jupiter.filemanager.oauth:/oauth2redirect`, delegated permissions `Files.Read offline_access User.Read` |

The redirect scheme `com.jupiter.filemanager.oauth` must match the `appAuthRedirectScheme` manifest placeholder (§4.2) in all three consoles.

### 7.3 ProGuard — append to `app/proguard-rules.pro`

```proguard
# AppAuth (Initiative #2): keeps reflective JSON (de)serialization of AuthState.
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# OkHttp (defensive; already used app-wide).
-dontwarn okhttp3.**
-dontwarn okio.**

# org.json is part of the Android platform; no rule needed. kotlinx-serialization
# models (if any are added) must be annotated @Serializable and kept by the
# kotlin-serialization plugin's bundled rules.
```

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. Add `GOOGLE_DRIVE_CLIENT_ID` to `local.properties`; build & install debug. Open **Cloud Hub**.
2. Tap **+** → choose **Google Drive** → Custom Tab opens → sign in → consent. App returns; account appears as connected with the real display name.
3. Tap the connected account → **Cloud Browser** lists root folders/files (folders first).
4. Tap a folder → it navigates deeper; **Back** returns to the parent.
5. Tap a file → it downloads to `Downloads/<name>`; a snackbar confirms "Saved to Downloads/…". Verify the file exists and opens.
6. Force a refresh after >1h (or revoke the token in the Google account security page) → next list triggers a silent refresh (success) or a clean "reconnect required" snackbar (no crash).
7. Repeat 2–5 for Dropbox and OneDrive (with their client ids set).
8. **Regression:** open **NAS / SMB / SFTP / FTP / WebDAV** connections (`NasConnectionsScreen` → `RemoteBrowserScreen`) and confirm listing/download still work unchanged.
9. **Unconfigured-provider check:** clear `DROPBOX_CLIENT_ID`, rebuild, tap Dropbox → a "not configured in this build" toast shows; no crash.
10. **Disconnect:** remove a connected account → it disappears and its encrypted tokens are purged (re-adding requires a fresh sign-in).

### 8.2 Recommended unit tests (`app/src/test/...`)

- `CloudOAuthConfigTest` — `forProvider` returns correct endpoints/scopes; `isConfigured` false when client id blank; ICLOUD/BOX/WEBDAV → null.
- `GoogleDriveFileSourceTest` / `DropboxFileSourceTest` / `OneDriveFileSourceTest` — feed canned JSON via OkHttp `MockWebServer` (add `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`); assert `list` maps fields to `RemoteEntry`, folders flagged correctly, 401 → `AppError.AccessDenied`, 404 → `NotFound`, IOException → `AppError.Io`.
- `CloudAuthRepositoryImplTest` — `validAccessToken` on a missing account returns `Failure`; corrupt JSON returns `Failure` (no throw).
- `CloudSourceProviderImplTest` — routing table maps each supported provider, returns null otherwise.

### 8.3 Recommended instrumented tests (`app/src/androidTest/...`)

- `CloudBrowserScreenTest` — with a fake `CloudSourceProvider`/`CloudAuthRepository` bound via a Hilt test module, assert folders render first, a file row shows a download icon, and tapping a folder invokes `onOpenFolder`.

---

## 9. Error handling & edge cases

| # | Scenario | Handling |
|---|---|---|
| 1 | **User cancels the consent screen** (closes Custom Tab) | `AuthorizationResponse.fromIntent` is null → `completeAuthorization` returns `AppResult.Failure(AppError.Io("Authorization cancelled"))`; ViewModel cleans up the pending service and no account is added. No crash. |
| 2 | **Refresh token revoked / expired** (provider returns `invalid_grant`) | `performActionWithFreshTokens` yields a null access token; `validAccessToken` returns `Failure("…reconnect required")`; browser shows a snackbar, list stays empty. User can disconnect + reconnect. |
| 3 | **No network during list/download** | OkHttp throws `IOException`; `CloudHttp.execute` maps it to `AppError.Io`; surfaced as a snackbar. `ACCESS_NETWORK_STATE` allows an optional pre-check, but the IO path is the safety net. |
| 4 | **HTTP 401/403 (token race or insufficient scope)** | `CloudHttp.mapHttpError` → `AppError.AccessDenied`; snackbar prompts re-auth; never crashes. |
| 5 | **HTTP 429 rate limiting** | `mapHttpError` → `AppError.Io("Rate limited … retry shortly")`; user can pull-to-refresh later. |
| 6 | **Provider not configured in this build** (blank client id) | `buildAuthIntent` / `forProvider` return null; `beginConnect` returns null; screen shows `cloud_not_configured` toast; legacy inert add path remains available. |
| 7 | **EncryptedSharedPreferences/KeyStore corruption** | `CloudTokenStore` falls back to plaintext `SharedPreferences` exactly like `CredentialStore`; the app never crashes (tokens still function for the session). |
| 8 | **Corrupt persisted AuthState JSON** | `AuthState.jsonDeserialize` wrapped in `runCatching`; null → `Failure("Corrupt auth state; reconnect required")`. |
| 9 | **Download into Downloads when filename collides** | File is overwritten in-place (same path); acceptable for v1. (A follow-up can de-duplicate by appending `(1)`.) |
| 10 | **Empty folder** | `list` returns an empty list → `CloudBrowserUiState.isEmpty` true → `EmptyView` shown, no error. |

---

## 10. Integration with other initiatives

- **#1 (if it adds a unified "Storage roots" / home tile list):** linked cloud accounts should appear as roots — expose `ConnectionRepository.observeCloudAccounts()` (already present) to the home aggregator; no change needed here.
- **Transfer Center / TransferTask initiative:** cloud downloads currently run in the ViewModel. If a background-transfer (WorkManager) initiative lands, route `CloudFileSource.download` through its queue; the `CloudFileSource` abstraction is the integration seam (it already returns `AppResult`).
- **Dual-pane / file-ops initiative:** `RemoteEntry` is reused, so a dual-pane copy that targets a cloud source can call `CloudSourceProvider.sourceFor(...).download(...)` with no new model.
- **Search initiative:** out of scope (cloud search would need provider-specific search endpoints) — explicitly deferred.
- **No hard build dependency** on any other initiative; this one is self-contained behind the new `data/cloud` package and can ship independently.

---

## 11. Rollback plan

This initiative is purely **additive**. To revert:

1. Delete the new packages/files: `data/cloud/**`, `domain/cloud/**`, `di/CloudModule.kt`, `feature/cloud/browser/**`.
2. Revert `CloudHubViewModel.kt` and `CloudHubScreen.kt` to their pre-initiative versions (git checkout); revert the `Destinations.kt` `CloudBrowser` block and the `JupiterNavHost.kt` cloud composables.
3. Remove the Gradle additions: `appauth` + `kotlinx-serialization-json` entries in `libs.versions.toml`, the `dependencies` lines and `buildConfigField`/`manifestPlaceholders` in `app/build.gradle.kts`, and the serialization plugin alias.
4. Remove the two `<uses-permission>` lines + cloud string resources + ProGuard additions.
5. `local.properties` keys can be left (unused) or removed.

Because nothing in `data/remote` / `domain/remote` / the persistence schema was mutated, existing SMB/SFTP/FTP/WebDAV connections and previously-linked inert cloud entries continue to work after rollback. `CloudAccount` persistence format is unchanged (`id|provider|displayName`), so no data migration/rollback is needed.

---

## 12. Definition of done

- [ ] `./gradlew assembleDebug` succeeds (CI green).
- [ ] `./gradlew testDebugUnitTest` succeeds (CI green), including the new `data/cloud` unit tests.
- [ ] AppAuth + kotlinx-serialization added via the version catalog (no hard-coded coordinates in `build.gradle.kts`).
- [ ] `INTERNET` + `ACCESS_NETWORK_STATE` permissions present; `appAuthRedirectScheme` placeholder wired.
- [ ] Google Drive: connect → list → open folder → download works end-to-end against a real account.
- [ ] Dropbox: connect → list → open folder → download works end-to-end against a real account.
- [ ] OneDrive: connect → list → open folder → download works end-to-end against a real account.
- [ ] Silent token refresh works (expired access token transparently refreshed; revoked token yields a clean "reconnect required" message, no crash).
- [ ] Tokens persisted only in `EncryptedSharedPreferences` (`jupiter_cloud_tokens`); none in plaintext DataStore; disconnect purges them.
- [ ] All cloud failures surface as `AppError`-backed snackbars; **no uncaught exception path** (every REST/OAuth call returns `AppResult`).
- [ ] Unconfigured provider (blank client id) shows "not configured" and never crashes.
- [ ] **No regression:** SMB/SFTP/FTP/WebDAV remote browsing & download (`NasConnectionsScreen` → `RemoteBrowserScreen`) still work unchanged.
- [ ] **No regression:** existing Cloud Hub link/unlink flow and `CloudAccount` persistence format (`id|provider|displayName`) still work; previously-linked entries still render.
- [ ] **No regression:** Vault (EncryptedSharedPreferences-based) and app launch/navigation unaffected.
- [ ] All new files have KDoc; all new public types are immutable where applicable (`CloudBrowserUiState`, `CloudAccountInfo`).
- [ ] ProGuard keep rules for AppAuth present so the release build's reflective `AuthState` (de)serialization survives shrinking.
