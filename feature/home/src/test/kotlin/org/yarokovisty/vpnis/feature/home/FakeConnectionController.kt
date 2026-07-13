package org.yarokovisty.vpnis.feature.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.yarokovisty.vpnis.core.domain.connection.ConnectionController
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import org.yarokovisty.vpnis.core.domain.model.Server

internal class FakeConnectionController(initialState: VpnConnectionState = VpnConnectionState.Loading) :
    ConnectionController {

    private val _state = MutableStateFlow(initialState)

    override val state: Flow<VpnConnectionState> = _state

    val connectCalls: MutableList<Server> = mutableListOf()
    var disconnectCount: Int = 0

    fun emit(state: VpnConnectionState) {
        _state.value = state
    }

    override suspend fun connect(server: Server) {
        connectCalls.add(server)
    }

    override suspend fun disconnect() {
        disconnectCount++
    }
}
