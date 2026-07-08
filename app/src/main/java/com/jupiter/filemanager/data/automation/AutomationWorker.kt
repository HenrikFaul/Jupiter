package com.jupiter.filemanager.data.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.jupiter.filemanager.domain.repository.AutomationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker that runs the user's enabled automation rules exactly once.
 *
 * Enqueued explicitly (for example from the Automation screen's "Run now" action) —
 * never silently or on a schedule. It snapshots the currently persisted rules from
 * [AutomationRepository], filters to the enabled ones (delegated to [RuleEngine]),
 * and hands them to the engine which performs the safe file operations off the main
 * thread, returning the number of files affected.
 *
 * Constructed by Hilt's `HiltWorkerFactory` (wired via
 * [com.jupiter.filemanager.JupiterApp]'s `Configuration.Provider`). Any failure is
 * caught and reported as [Result.failure] so a bad rule never crashes the process; on
 * success the affected count is surfaced as output [Data] under [KEY_AFFECTED_COUNT].
 */
@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val automationRepository: AutomationRepository,
    private val ruleEngine: RuleEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val rules = automationRepository.observeRules().first()
        val affected = ruleEngine.applyAll(rules)
        val output = Data.Builder()
            .putInt(KEY_AFFECTED_COUNT, affected)
            .build()
        Result.success(output)
    } catch (_: Exception) {
        Result.failure()
    }

    companion object {
        /** Unique work name used when enqueuing a manual automation run. */
        const val UNIQUE_WORK_NAME: String = "automation_run_now"

        /** Output-[Data] key carrying the number of files affected by the run. */
        const val KEY_AFFECTED_COUNT: String = "affected_count"
    }
}
