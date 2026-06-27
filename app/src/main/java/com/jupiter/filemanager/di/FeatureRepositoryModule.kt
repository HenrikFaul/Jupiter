package com.jupiter.filemanager.di

import com.jupiter.filemanager.data.activity.ActivityRepositoryImpl
import com.jupiter.filemanager.data.automation.AutomationRepositoryImpl
import com.jupiter.filemanager.data.connection.ConnectionRepositoryImpl
import com.jupiter.filemanager.data.sync.SyncRepositoryImpl
import com.jupiter.filemanager.data.tag.TagRepositoryImpl
import com.jupiter.filemanager.data.transfer.TransferRepositoryImpl
import com.jupiter.filemanager.data.version.VersionRepositoryImpl
import com.jupiter.filemanager.data.workspace.WorkspaceRepositoryImpl
import com.jupiter.filemanager.domain.repository.ActivityRepository
import com.jupiter.filemanager.domain.repository.AutomationRepository
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import com.jupiter.filemanager.domain.repository.SyncRepository
import com.jupiter.filemanager.domain.repository.TagRepository
import com.jupiter.filemanager.domain.repository.TransferRepository
import com.jupiter.filemanager.domain.repository.VersionRepository
import com.jupiter.filemanager.domain.repository.WorkspaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the newly added feature data-layer repository implementations to their
 * domain-layer interfaces.
 *
 * Each implementation is a `@Singleton`-annotated class with an `@Inject`
 * constructor, so Hilt knows how to construct it; this module only declares the
 * interface-to-impl bindings via `@Binds`.
 *
 * This is a separate module from [RepositoryModule] and does not modify it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureRepositoryModule {

    @Binds
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    abstract fun bindAutomationRepository(impl: AutomationRepositoryImpl): AutomationRepository

    @Binds
    abstract fun bindWorkspaceRepository(impl: WorkspaceRepositoryImpl): WorkspaceRepository

    @Binds
    abstract fun bindConnectionRepository(impl: ConnectionRepositoryImpl): ConnectionRepository

    @Binds
    abstract fun bindTransferRepository(impl: TransferRepositoryImpl): TransferRepository

    @Binds
    abstract fun bindVersionRepository(impl: VersionRepositoryImpl): VersionRepository

    @Binds
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}
