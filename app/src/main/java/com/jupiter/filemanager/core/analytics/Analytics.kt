package com.jupiter.filemanager.core.analytics

import com.jupiter.filemanager.data.preferences.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single, immutable analytics event.
 *
 * @property name a short, stable identifier for the event (e.g. "screen_view").
 * @property params optional anonymous, non-PII key/value metadata. Defaults to empty.
 */
data class AnalyticsEvent(
    val name: String,
    val params: Map<String, String> = emptyMap(),
)

/**
 * Sink for product analytics.
 *
 * Implementations MUST honor an explicit opt-in: nothing is tracked unless the user
 * has enabled analytics. No implementation may transmit personally identifiable
 * information, and none may perform network I/O unless the user has opted in.
 */
interface Analytics {

    /** Records [event] if (and only if) analytics are currently enabled. */
    fun track(event: AnalyticsEvent)

    /** Enables or disables tracking. When disabled, [track] is a no-op. */
    fun setEnabled(enabled: Boolean)
}

/**
 * Default, privacy-preserving [Analytics] implementation that does nothing observable.
 *
 * It mirrors the user's [SettingsDataStore.analyticsOptIn] preference into its enabled
 * state and provides an imperative [setEnabled] override, but never sends data anywhere:
 * it is a pure no-op sink. This keeps analytics OFF by default and guarantees no PII and
 * no network access regardless of the opt-in flag. A real backend can replace this binding
 * later without touching call sites.
 */
@Singleton
class NoOpAnalytics @Inject constructor(
    settings: SettingsDataStore,
) : Analytics {

    private val scope = CoroutineScope(SupervisorJob())

    /** Tracks whether tracking is currently permitted; defaults to OFF until opt-in resolves. */
    private val enabled = MutableStateFlow(false)

    init {
        settings.analyticsOptIn
            .onEach { optIn -> enabled.value = optIn }
            .launchIn(scope)
    }

    override fun track(event: AnalyticsEvent) {
        if (!enabled.value) return
        // No-op sink: opted-in events are intentionally dropped. No PII is read,
        // no network call is made. A real backend can be swapped in via DI later.
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}
