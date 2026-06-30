package com.jupiter.filemanager.di

import com.jupiter.filemanager.feature.ai.AiAssistant
import com.jupiter.filemanager.feature.ai.AnthropicAiAssistant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the [AiAssistant] interface to the real [AnthropicAiAssistant] implementation,
 * which talks to the Anthropic Messages API when a Claude API key is configured and
 * otherwise reports itself disabled.
 *
 * [AnthropicAiAssistant] is a `@Singleton`-annotated class with an `@Inject` constructor,
 * so Hilt knows how to create it; this module only declares the interface binding.
 * [com.jupiter.filemanager.feature.ai.NoOpAiAssistant] remains available as an inert
 * fallback implementation but is no longer bound here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindAiAssistant(impl: AnthropicAiAssistant): AiAssistant
}
