package com.jupiter.filemanager.domain.remote

import com.jupiter.filemanager.domain.model.ConnectionType

/** Resolves the [RemoteFileSource] implementation for a given [ConnectionType]. */
interface RemoteSourceProvider {
    fun sourceFor(type: ConnectionType): RemoteFileSource?
}
