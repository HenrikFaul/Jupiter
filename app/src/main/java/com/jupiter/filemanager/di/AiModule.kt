package com.jupiter.filemanager.di

import com.jupiter.filemanager.feature.ai.AiAssistant
import com.jupiter.filemanager.feature.ai.NoOpAiAssistant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the [AiAssistant] interface to its default, inert [NoOpAiAssistant]
 * implementation used when no real AI backend is configured.
 *
 * [NoOpAiAssistant] is a `@Singleton`-annotated class with an `@Inject` constructor,
 * so Hilt knows how to create it; this module only declares the interface binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    abstract fun bindAiAssistant(impl: NoOpAiAssistant): AiAssistant
}
