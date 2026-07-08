package com.jupiter.filemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing transient app-state flags (onboarding,
 * first-run gates, etc.).
 *
 * Declared at file scope so the single instance is shared across the process,
 * as required by the Preferences DataStore contract. Uses a name distinct from
 * the user-settings store ("jupiter_settings") so the two stores never collide.
 */
val Context.appStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_app_state")

/**
 * Persists lightweight application-state flags that are not user preferences,
 * such as whether the onboarding flow has been completed.
 *
 * Reads are exposed as cold [Flow]s that fall back to safe defaults when a key
 * is absent or the file cannot be read; writes are suspending and atomic.
 */
@Singleton
class AppStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore: DataStore<Preferences> = context.appStateDataStore

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_SEEN_WHATS_NEW_VERSION = intPreferencesKey("last_seen_whats_new_version")
    }

    /** Whether the user has finished the onboarding flow; defaults to false. */
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] ?: false }

    /**
     * The app [BuildConfig.VERSION_CODE] for which the "What's New" sheet was last
     * shown; defaults to 0 so the sheet appears on first run after this feature ships.
     */
    val lastSeenWhatsNewVersion: Flow<Int> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.LAST_SEEN_WHATS_NEW_VERSION] ?: 0 }

    suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setLastSeenWhatsNewVersion(value: Int) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SEEN_WHATS_NEW_VERSION] = value }
    }

    /**
     * Swallows [IOException]s raised while reading the persisted file (e.g. disk
     * errors) by emitting empty preferences, so collectors fall back to defaults
     * instead of crashing.
     */
    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { throwable ->
        if (throwable is IOException) {
            emit(androidx.datastore.preferences.core.emptyPreferences())
        } else {
            throw throwable
        }
    }
}
