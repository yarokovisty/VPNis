package org.yarokovisty.vpnis.data.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.yarokovisty.vpnis.core.domain.model.Server
import org.yarokovisty.vpnis.core.domain.model.ServerId
import org.yarokovisty.vpnis.core.domain.repository.ServerRepository

/**
 * In-memory implementation of [ServerRepository] for Milestone 1.
 *
 * The server list is seeded at construction with [DEFAULT_SERVER] — the hardcoded
 * operator default that satisfies FR-50 (SRS §5.6). Room-backed persistence and
 * user-managed server CRUD are deferred to the "Servers" feature (epic after MVP).
 *
 * Thread safety: both [MutableStateFlow]s are thread-safe by construction; no
 * additional synchronisation is needed for the read paths. [selectServer] does a
 * read of [serversFlow] followed by a conditional write of [selectedIdFlow] — each
 * step is individually thread-safe, but the pair is not an atomic compare-and-swap.
 * This is safe in M1 because [serversFlow] is never mutated; once mutation is added
 * (Room migration), guard the check-then-act with a `Mutex` or move to a single
 * combined state.
 */
public class ServerRepositoryImpl : ServerRepository {

    private val serversFlow: MutableStateFlow<List<Server>> =
        MutableStateFlow(listOf(DEFAULT_SERVER))

    private val selectedIdFlow: MutableStateFlow<ServerId?> =
        MutableStateFlow(DEFAULT_SERVER.id)

    /**
     * Emits the currently selected [Server], or `null` when no server is selected
     * or the selected id no longer exists in the list (should not happen in M1, but
     * handled defensively for future list mutations).
     *
     * Both [selectedIdFlow] and [serversFlow] are observed via [combine] so the emission
     * stays correct if the list changes, not just the selection.
     */
    override fun observeSelectedServer(): Flow<Server?> = combine(
        selectedIdFlow,
        serversFlow,
    ) { id, servers ->
        servers.firstOrNull { it.id == id }
    }

    /** Emits the full list of available servers. */
    override fun observeServers(): Flow<List<Server>> = serversFlow

    /**
     * Changes the selected server to [id].
     *
     * If [id] does not exist in the current server list the call is a no-op —
     * the selection is left unchanged. This prevents the UI from entering an
     * inconsistent "selected id with no matching server" state.
     */
    override suspend fun selectServer(id: ServerId) {
        if (serversFlow.value.any { it.id == id }) {
            selectedIdFlow.value = id
        }
    }
}
