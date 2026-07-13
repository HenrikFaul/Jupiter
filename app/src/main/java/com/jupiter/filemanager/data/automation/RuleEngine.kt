package com.jupiter.filemanager.data.automation

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.domain.model.AutomationSafety
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Result of a mutation-free Automation preview. */
data class AutomationPreview(
    val matchingFiles: Int,
    val actionSupported: Boolean,
    val destructiveBlocked: Boolean,
)

/**
 * Interprets simple human-authored automation rules and applies safe file operations.
 *
 * Rules are free-form "when / then" text pairs (see [AutomationRule]). [RuleEngine]
 * reads them heuristically:
 *
 *  - **when** clauses may reference well-known buckets ("screenshot", "download"),
 *    a file type / extension (e.g. "pdf", ".jpg"), and/or an age ("older than N days").
 *  - **then** clauses may "move to <folder>" or "favorite".
 *
 * Only safe organization operations **within primary external storage** are performed; any
 * resolved target that would escape the storage root is skipped. Every file-system
 * interaction is guarded, so a malformed rule can never crash the caller. Destructive actions are
 * rejected even for legacy/manipulated persisted rules. [applyAll] returns the number of files
 * affected.
 *
 * All work runs on the injected [IoDispatcher]; it is cooperative with cancellation.
 */
@Singleton
class RuleEngine @Inject constructor(
    private val fileRepository: FileRepository,
    private val bookmarkRepository: BookmarkRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * Applies every rule in [rules] (in order), returning the total number of files
     * affected (moved or marked favorite). Disabled rules are skipped.
     */
    suspend fun applyAll(rules: List<AutomationRule>): Int = withContext(dispatcher) {
        val root = File(fileRepository.rootDirectory())
        val canonicalRoot = try {
            root.canonicalPath
        } catch (_: Exception) {
            return@withContext 0
        }

        var affected = 0
        for (rule in rules) {
            coroutineContext.ensureActive()
            if (!rule.enabled) continue
            affected += try {
                applyRule(rule, root, canonicalRoot)
            } catch (_: Exception) {
                0
            }
        }
        affected
    }

    /**
     * Dry-runs one rule and reports how many files currently match. No bookmark or file-system
     * mutation is performed, and suspended rules can also be previewed safely.
     */
    suspend fun preview(rule: AutomationRule): AutomationPreview = withContext(dispatcher) {
        val root = File(fileRepository.rootDirectory())
        val actionSupported = parseAction(rule.thenText) != null
        val destructiveBlocked = AutomationSafety.isDestructiveAction(rule.thenText)
        val matchingFiles = try {
            val condition = parseCondition(rule.whenText, root)
            condition.searchRoots
                .flatMap { collectFiles(it) }
                .count { matches(it, condition) }
        } catch (_: Exception) {
            0
        }
        AutomationPreview(
            matchingFiles = matchingFiles,
            actionSupported = actionSupported,
            destructiveBlocked = destructiveBlocked,
        )
    }

    // region Internals --------------------------------------------------------

    /** Applies a single [rule], returning how many files it affected. */
    private suspend fun applyRule(rule: AutomationRule, root: File, canonicalRoot: String): Int {
        val condition = parseCondition(rule.whenText, root)
        val action = parseAction(rule.thenText) ?: return 0

        val candidates = condition.searchRoots
            .flatMap { collectFiles(it) }
            .filter { matches(it, condition) }

        var affected = 0
        for (file in candidates) {
            coroutineContext.ensureActive()
            val ok = when (action) {
                is RuleAction.Favorite -> markFavorite(file, canonicalRoot)
                is RuleAction.MoveTo -> safeMove(file, action.folderName, root, canonicalRoot)
            }
            if (ok) affected += 1
        }
        return affected
    }

    /** Parsed trigger condition for a rule. */
    private data class Condition(
        val searchRoots: List<File>,
        val nameContains: String?,
        val extension: String?,
        val olderThanMillis: Long?,
    )

    /** Parsed action for a rule. */
    private sealed interface RuleAction {
        data class MoveTo(val folderName: String) : RuleAction
        data object Favorite : RuleAction
    }

    /**
     * Derives a [Condition] from the human "when" text. Recognizes the screenshot and
     * download buckets, a file extension/type keyword, and an "older than N days" clause.
     */
    private fun parseCondition(whenText: String, root: File): Condition {
        val text = whenText.lowercase()

        val searchRoots = ArrayList<File>()
        var nameContains: String? = null
        var extension: String?

        when {
            text.contains("screenshot") -> {
                val pictures = File(root, "Pictures/Screenshots")
                val dcim = File(root, "DCIM/Screenshots")
                if (pictures.isDirectory) searchRoots.add(pictures)
                if (dcim.isDirectory) searchRoots.add(dcim)
                if (searchRoots.isEmpty()) searchRoots.add(root)
                nameContains = "screenshot"
            }
            text.contains("download") -> {
                val downloads = File(root, "Download")
                searchRoots.add(if (downloads.isDirectory) downloads else root)
            }
            else -> searchRoots.add(root)
        }

        // Extension / type: look for an explicit ".ext" or a known type keyword.
        val explicit = EXTENSION_REGEX.find(text)?.groupValues?.getOrNull(1)
        extension = explicit ?: TYPE_KEYWORDS.firstOrNull { text.contains(it) }

        val olderThan = OLDER_THAN_REGEX.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val olderThanMillis = olderThan?.let { System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it) }

        return Condition(
            searchRoots = searchRoots.distinct(),
            nameContains = nameContains,
            extension = extension,
            olderThanMillis = olderThanMillis,
        )
    }

    /** Derives a [RuleAction] from the human "then" text, or null when none is recognized. */
    private fun parseAction(thenText: String): RuleAction? {
        if (!AutomationSafety.isSupportedAction(thenText)) return null
        val text = thenText.lowercase()
        return when {
            text.contains("favorite") || text.contains("favourite") -> RuleAction.Favorite
            AutomationSafety.moveDestination(thenText) != null -> {
                val folder = AutomationSafety.moveDestination(thenText).orEmpty()
                    .trim('/', '\\', '"', '\'')
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                if (folder.isBlank()) null else RuleAction.MoveTo(folder)
            }
            else -> null
        }
    }

    /** Returns whether [file] satisfies [condition]'s name/extension/age constraints. */
    private fun matches(file: File, condition: Condition): Boolean {
        if (!file.isFile) return false
        val name = file.name.lowercase()
        if (condition.nameContains != null && !name.contains(condition.nameContains)) return false
        if (condition.extension != null && !name.endsWith("." + condition.extension)) return false
        if (condition.olderThanMillis != null && file.lastModified() > condition.olderThanMillis) {
            return false
        }
        return true
    }

    /**
     * Lists regular files directly under [dir] (one level deep) to keep the scan bounded
     * and predictable; returns an empty list on any access error.
     */
    private fun collectFiles(dir: File): List<File> = try {
        if (!dir.isDirectory) {
            emptyList()
        } else {
            dir.listFiles()?.filter { it.isFile } ?: emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Moves [file] into a sibling [folderName] under [root], guarded against escaping the root. */
    private suspend fun safeMove(
        file: File,
        folderName: String,
        root: File,
        canonicalRoot: String,
    ): Boolean = try {
        val destDir = File(root, folderName)
        if (!isWithin(destDir, canonicalRoot)) {
            false
        } else {
            when (val item = fileRepository.getFile(file.absolutePath)) {
                is AppResult.Failure -> false
                is AppResult.Success -> fileRepository
                    .move(listOf(item.data), destDir.absolutePath)
                    .lastOrNull()
                    ?.state == OperationState.COMPLETED
            }
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Marks [file] as a favorite by persisting a bookmark via [BookmarkRepository], but
     * only when it resolves inside the storage root. Any persistence failure is swallowed
     * and reported as "not affected" so a misbehaving store can never abort a rule run.
     */
    private suspend fun markFavorite(file: File, canonicalRoot: String): Boolean = try {
        if (!file.isFile || !isWithin(file, canonicalRoot)) {
            false
        } else {
            bookmarkRepository.addBookmark(file.absolutePath, file.name)
            true
        }
    } catch (_: Exception) {
        false
    }

    /** Returns whether [file] resolves at or below [canonicalRoot]. */
    private fun isWithin(file: File, canonicalRoot: String): Boolean = try {
        val canonical = file.canonicalPath
        canonical == canonicalRoot || canonical.startsWith(canonicalRoot + File.separator)
    } catch (_: Exception) {
        false
    }

    // endregion

    private companion object {
        /** Matches an explicit extension like ".pdf" or " pdf files". */
        val EXTENSION_REGEX = Regex("""\.([a-z0-9]{1,5})\b""")

        /** Matches "older than N days". */
        val OLDER_THAN_REGEX = Regex("""older than\s+(\d+)\s*day""")

        /** Bare type keywords that imply a file extension. */
        val TYPE_KEYWORDS: List<String> = listOf(
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "mp4", "mkv", "mp3",
            "wav", "zip", "rar", "apk", "txt", "doc", "docx", "xls", "xlsx",
        )
    }
}
