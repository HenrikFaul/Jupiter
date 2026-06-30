package com.jupiter.filemanager.data.automation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
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
 * [AutomationRepository], filters to the enabled ones, and hands them to [RuleEngine]
 * which performs the safe file operations off the main thread.
 *
 * Constructed by Hilt's `HiltWorkerFactory` (wired via
 * [com.jupiter.filemanager.JupiterApp]'s `Configuration.Provider`). Any failure is
 * caught and reported as [Result.failure] so a bad rule never crashes the process.
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
        ruleEngine.applyAll(rules)
        Result.success()
    } catch (_: Exception) {
        Result.failure()
    }

    companion object {
        /** Unique work name used when enqueuing a manual automation run. */
        const val UNIQUE_WORK_NAME: String = "automation_run_now"
    }
}
