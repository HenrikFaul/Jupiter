package com.jupiter.filemanager.domain.model

/**
 * Built-in examples that make Automation understandable on first open.
 *
 * Stable ids make the presets ordinary persisted rules after the first mutation: users can edit,
 * rename, suspend, activate, or remove them without a later app start restoring deleted presets.
 * Every preset starts suspended so installing/upgrading Jupiter can never reorganize files without
 * an explicit user decision.
 */
object AutomationDefaults {
    val rules: List<AutomationRule> = listOf(
        AutomationRule(
            id = "preset-download-pdf",
            name = "File downloaded PDFs",
            enabled = false,
            whenText = "PDF files in Downloads",
            thenText = "move to Documents",
        ),
        AutomationRule(
            id = "preset-download-apk",
            name = "Collect downloaded installers",
            enabled = false,
            whenText = "APK files in Downloads",
            thenText = "move to APKs",
        ),
        AutomationRule(
            id = "preset-download-zip",
            name = "Archive downloaded ZIP files",
            enabled = false,
            whenText = "ZIP files in Downloads",
            thenText = "move to Archives",
        ),
        AutomationRule(
            id = "preset-download-music",
            name = "Sort downloaded music",
            enabled = false,
            whenText = "MP3 files in Downloads",
            thenText = "move to Music",
        ),
        AutomationRule(
            id = "preset-favorite-screenshots",
            name = "Favorite screenshots",
            enabled = false,
            whenText = "screenshots",
            thenText = "favorite",
        ),
    )
}

/** Safety contract shared by authoring, preview, and execution. */
object AutomationSafety {
    private val destructiveWords = Regex(
        pattern = """\b(delete|erase|remove|wipe|destroy|trash)\b""",
        option = RegexOption.IGNORE_CASE,
    )
    private val moveTo = Regex(
        pattern = """\bmove(?:\s+it)?\s+to\b""",
        option = RegexOption.IGNORE_CASE,
    )

    /** Returns true for actions that could be interpreted as deleting user data. */
    fun isDestructiveAction(action: String): Boolean = destructiveWords.containsMatchIn(action)

    /**
     * Jupiter intentionally supports only organization actions. Unknown actions remain saved as
     * readable drafts but cannot be previewed or executed until the user changes them.
     */
    fun isSupportedAction(action: String): Boolean {
        if (isDestructiveAction(action)) return false
        val normalized = action.lowercase()
        return moveDestination(action) != null ||
            normalized.contains("favorite") ||
            normalized.contains("favourite")
    }

    /** Extracts the destination portion from both "move to" and natural "move it to" actions. */
    fun moveDestination(action: String): String? {
        val match = moveTo.find(action) ?: return null
        return action.substring(match.range.last + 1).trim().takeIf { it.isNotBlank() }
    }
}
