package org.yarokovisty.vpnis.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId

/**
 * Persistence contract for the server list and the user's current selection.
 *
 * Implementations live in the data layer and are backed by local storage (Room/DataStore).
 */
public interface ServerRepository {
    public fun observeSelectedServer(): Flow<Server?>
    public fun observeServers(): Flow<List<Server>>
    public suspend fun selectServer(id: ServerId)
}
