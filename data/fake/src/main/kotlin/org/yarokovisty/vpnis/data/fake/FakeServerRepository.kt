package org.yarokovisty.vpnis.data.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

/**
 * In-memory fake implementation of [ServerRepository].
 *
 * Backed by [MutableStateFlow] — state changes are immediately reflected in
 * all active collectors, matching the behaviour of a Room-backed repository.
 *
 * @param initialServers The server list to start with. Defaults to [listOf(DEFAULT_SERVER)]
 *                       so the Home screen shows a real-looking entry without any
 *                       additional setup. Pass an empty list to exercise the "no servers
 *                       configured" empty state.
 * @param selectFirst    When true and [initialServers] is non-empty, the first server
 *                       is pre-selected so the Home screen renders in the "ready to
 *                       connect" Disconnected state immediately. Set to false to start
 *                       with no selection (shows the "Add server" empty prompt).
 */
public class FakeServerRepository(initialServers: List<Server> = listOf(DEFAULT_SERVER), selectFirst: Boolean = true) :
    ServerRepository {

    private val selectedFlow = MutableStateFlow<Server?>(
        if (selectFirst) initialServers.firstOrNull() else null,
    )
    private val serversFlow = MutableStateFlow(initialServers)

    override fun observeSelectedServer(): Flow<Server?> = selectedFlow

    override fun observeServers(): Flow<List<Server>> = serversFlow

    override suspend fun selectServer(id: ServerId) {
        val server = serversFlow.value.firstOrNull { it.id == id }
        selectedFlow.value = server
    }

    /**
     * Replaces the entire server list and clears the selection if the previously
     * selected server is no longer in the new list.
     */
    public fun setServers(servers: List<Server>) {
        serversFlow.value = servers
        if (selectedFlow.value?.id !in servers.map { it.id }) {
            selectedFlow.value = null
        }
    }

    /**
     * Directly sets the selected server without going through [selectServer].
     * Useful in tests to put the repository into a specific state.
     */
    public fun setSelectedServer(server: Server?) {
        selectedFlow.value = server
    }

    public companion object {
        /**
         * A realistic-looking default server used when no explicit list is provided.
         * The config value is a placeholder URI — the domain layer does not parse it.
         */
        public val DEFAULT_SERVER: Server = Server(
            id = ServerId("nl-1"),
            name = "Нидерланды · Амстердам",
            config = "vless://placeholder-uuid@nl-1.example.com:443?type=tcp&security=reality",
        )
    }
}
