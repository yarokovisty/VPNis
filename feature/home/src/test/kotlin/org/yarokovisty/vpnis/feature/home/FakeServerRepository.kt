package org.yarokovisty.vpnis.feature.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

internal class FakeServerRepository(initialSelected: Server? = null, initialList: List<Server> = emptyList()) :
    ServerRepository {

    private val selectedServerFlow = MutableStateFlow<Server?>(initialSelected)
    private val serversFlow = MutableStateFlow(initialList)

    val selectServerCalls: MutableList<ServerId> = mutableListOf()

    override fun observeSelectedServer(): Flow<Server?> = selectedServerFlow

    override fun observeServers(): Flow<List<Server>> = serversFlow

    override suspend fun selectServer(id: ServerId) {
        selectServerCalls.add(id)
        selectedServerFlow.value = serversFlow.value.firstOrNull { it.id == id }
    }

    fun setSelectedServer(server: Server?) {
        selectedServerFlow.value = server
    }
}
