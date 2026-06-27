package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.AutomationRule
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-defined [AutomationRule]s and exposes their current state.
 *
 * Rules are stored as simple "when / then" text pairs authored either manually or
 * with natural-language assistance. Actual execution of rules is a backend concern
 * that is not yet wired up; this repository only manages persistence and toggling.
 */
interface AutomationRepository {

    /**
     * Streams the current set of saved [AutomationRule]s, emitting on every change.
     */
    fun observeRules(): Flow<List<AutomationRule>>

    /**
     * Creates and persists a new rule with the given [name], trigger [whenText]
     * and action [thenText]. New rules are enabled by default.
     */
    suspend fun addRule(name: String, whenText: String, thenText: String)

    /**
     * Enables or disables the rule identified by [id] according to [enabled].
     */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /**
     * Permanently removes the rule identified by [id], if present.
     */
    suspend fun deleteRule(id: String)
}
