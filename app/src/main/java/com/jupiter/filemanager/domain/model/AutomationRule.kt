package com.jupiter.filemanager.domain.model

/**
 * A user-defined automation rule expressed as a simple "when / then" pair.
 *
 * The [whenText] describes the triggering condition (e.g. "a screenshot is added")
 * and [thenText] describes the resulting action (e.g. "move it to /Screenshots").
 * These are stored as free-form text so they can be authored either manually or
 * via natural-language assistance. The safe automation engine can preview every rule and executes
 * only explicitly activated, user-confirmed organization actions; destructive actions are blocked.
 *
 * @param id stable unique identifier for the rule.
 * @param name human-readable name shown to the user.
 * @param enabled whether the rule is currently active.
 * @param whenText the trigger condition, in human-readable text.
 * @param thenText the action to perform, in human-readable text.
 */
data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val whenText: String,
    val thenText: String,
)
