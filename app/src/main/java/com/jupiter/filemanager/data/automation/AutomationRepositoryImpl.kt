package com.jupiter.filemanager.data.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.domain.repository.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing the persisted automation rules.
 *
 * Declared at file scope so the single instance is shared across the process,
 * as required by the Preferences DataStore contract. Uses a unique store name
 * so it never collides with the other feature stores.
 */
val Context.rulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_rules")

/**
 * Preferences-DataStore-backed implementation of [AutomationRepository].
 *
 * The full set of user-defined rules is serialized as a single JSON array string
 * under one key. New rules are appended and enabled by default. Actual execution
 * of rules is a backend concern that is not yet wired up; this implementation only
 * persists and toggles the rule definitions.
 */
@Singleton
class AutomationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutomationRepository {

    private val dataStore: DataStore<Preferences> = context.rulesDataStore

    private object Keys {
        val RULES = stringPreferencesKey("automation_rules")
    }

    override fun observeRules(): Flow<List<AutomationRule>> = dataStore.data
        .safe()
        .map { prefs -> decode(prefs[Keys.RULES]) }

    override suspend fun addRule(name: String, whenText: String, thenText: String) {
        val rule = AutomationRule(
            id = UUID.randomUUID().toString(),
            name = name,
            enabled = true,
            whenText = whenText,
            thenText = thenText,
        )
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.RULES])
            prefs[Keys.RULES] = encode(current + rule)
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.RULES])
            val updated = current.map { rule ->
                if (rule.id == id) rule.copy(enabled = enabled) else rule
            }
            prefs[Keys.RULES] = encode(updated)
        }
    }

    override suspend fun deleteRule(id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.RULES])
            prefs[Keys.RULES] = encode(current.filterNot { it.id == id })
        }
    }

    private fun encode(rules: List<AutomationRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject().apply {
                put(FIELD_ID, rule.id)
                put(FIELD_NAME, rule.name)
                put(FIELD_ENABLED, rule.enabled)
                put(FIELD_WHEN, rule.whenText)
                put(FIELD_THEN, rule.thenText)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<AutomationRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<AutomationRule>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString(FIELD_ID)
                if (id.isNullOrBlank()) continue
                result.add(
                    AutomationRule(
                        id = id,
                        name = obj.optString(FIELD_NAME),
                        enabled = obj.optBoolean(FIELD_ENABLED, true),
                        whenText = obj.optString(FIELD_WHEN),
                        thenText = obj.optString(FIELD_THEN),
                    ),
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Swallows [IOException]s raised while reading the persisted file by emitting
     * empty preferences, so collectors fall back to an empty rule set instead of
     * crashing.
     */
    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    private companion object {
        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_ENABLED = "enabled"
        const val FIELD_WHEN = "whenText"
        const val FIELD_THEN = "thenText"
    }
}
