package com.jupiter.filemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing all persisted Jupiter settings.
 *
 * Declared at file scope so the single instance is shared across the process,
 * as required by the Preferences DataStore contract.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_settings")

/**
 * Persists user preferences using Jetpack Preferences DataStore.
 *
 * All reads are exposed as cold [Flow]s that fall back to sensible defaults when
 * a key is absent or the underlying value is malformed. Writes are suspending and
 * performed atomically via [androidx.datastore.preferences.core.edit].
 *
 * [SortOption] is serialized via the enum [Enum.name] of its [SortField]/[SortDirection]
 * components plus a boolean for [SortOption.foldersFirst].
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_DIRECTION = stringPreferencesKey("sort_direction")
        val SORT_FOLDERS_FIRST = booleanPreferencesKey("sort_folders_first")
        val DUAL_PANE_ENABLED = booleanPreferencesKey("dual_pane_enabled")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val ACCENT_COLOR_ARGB = longPreferencesKey("accent_color_argb")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ANALYTICS_OPT_IN = booleanPreferencesKey("analytics_opt_in")
        val PRO_UNLOCKED = booleanPreferencesKey("pro_unlocked")
    }

    /** Current theme mode; defaults to [ThemeMode.SYSTEM]. */
    val themeMode: Flow<ThemeMode> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.THEME_MODE]
                ?.let { name -> enumValueOrNull<ThemeMode>(name) }
                ?: ThemeMode.SYSTEM
        }

    /** Whether hidden files should be shown; defaults to false. */
    val showHidden: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.SHOW_HIDDEN] ?: false }

    /** Persisted directory listing sort configuration; defaults to [SortOption]. */
    val sortOption: Flow<SortOption> = dataStore.data
        .safe()
        .map { prefs ->
            val defaults = SortOption()
            val field = prefs[Keys.SORT_FIELD]
                ?.let { name -> enumValueOrNull<SortField>(name) }
                ?: defaults.field
            val direction = prefs[Keys.SORT_DIRECTION]
                ?.let { name -> enumValueOrNull<SortDirection>(name) }
                ?: defaults.direction
            val foldersFirst = prefs[Keys.SORT_FOLDERS_FIRST] ?: defaults.foldersFirst
            SortOption(field = field, direction = direction, foldersFirst = foldersFirst)
        }

    /** Whether the dual-pane browser layout is enabled; defaults to false. */
    val dualPaneEnabled: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.DUAL_PANE_ENABLED] ?: false }

    /** Whether AI-assisted features are enabled; defaults to false. */
    val aiEnabled: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.AI_ENABLED] ?: false }

    /** The persisted Claude API key used to enable AI features; defaults to "". */
    val aiApiKey: Flow<String> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.AI_API_KEY] ?: "" }

    /**
     * Custom accent color packed as an ARGB [Long]; defaults to 0L meaning
     * "use the dynamic/brand default" (no custom accent applied).
     */
    val accentColorArgb: Flow<Long> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ACCENT_COLOR_ARGB] ?: 0L }

    /** Whether dark theme should use pure-black (AMOLED) surfaces; defaults to false. */
    val amoledBlack: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.AMOLED_BLACK] ?: false }

    /** Whether Material You dynamic color is enabled (on S+); defaults to true. */
    val dynamicColor: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.DYNAMIC_COLOR] ?: true }

    /** Whether the user has opted in to anonymous analytics; defaults to false. */
    val analyticsOptIn: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ANALYTICS_OPT_IN] ?: false }

    /**
     * Whether Jupiter Pro is unlocked; defaults to true so no existing feature is
     * gated or regressed until a billing product is actually configured.
     */
    val proUnlocked: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.PRO_UNLOCKED] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setShowHidden(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.SHOW_HIDDEN] = value }
    }

    suspend fun setSortOption(option: SortOption) {
        dataStore.edit { prefs ->
            prefs[Keys.SORT_FIELD] = option.field.name
            prefs[Keys.SORT_DIRECTION] = option.direction.name
            prefs[Keys.SORT_FOLDERS_FIRST] = option.foldersFirst
        }
    }

    suspend fun setDualPaneEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DUAL_PANE_ENABLED] = value }
    }

    suspend fun setAiEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AI_ENABLED] = value }
    }

    suspend fun setAiApiKey(value: String) {
        dataStore.edit { prefs -> prefs[Keys.AI_API_KEY] = value }
    }

    suspend fun setAccentColorArgb(value: Long) {
        dataStore.edit { prefs -> prefs[Keys.ACCENT_COLOR_ARGB] = value }
    }

    suspend fun setAmoledBlack(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AMOLED_BLACK] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DYNAMIC_COLOR] = value }
    }

    suspend fun setAnalyticsOptIn(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ANALYTICS_OPT_IN] = value }
    }

    suspend fun setProUnlocked(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.PRO_UNLOCKED] = value }
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

/**
 * Resolves an enum constant by [name], returning null instead of throwing when
 * no matching constant exists (e.g. a value persisted by an older app version).
 */
private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
