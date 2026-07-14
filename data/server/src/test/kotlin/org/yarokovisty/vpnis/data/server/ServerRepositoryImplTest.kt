package org.yarokovisty.vpnis.data.server

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.model.ServerId

class ServerRepositoryImplTest {

    private fun repository(): ServerRepositoryImpl = ServerRepositoryImpl()

    // (a) First-launch: observeSelectedServer emits the default server immediately.
    @Test
    fun `observeSelectedServer emits DefaultServer on first launch`() = runTest {
        val repo = repository()
        val selected = repo.observeSelectedServer().first()
        assertEquals(DEFAULT_SERVER, selected)
    }

    // (b) observeServers emits a list that contains the default server.
    @Test
    fun `observeServers emits list containing DefaultServer`() = runTest {
        val repo = repository()
        val servers = repo.observeServers().first()
        assertEquals(1, servers.size)
        assertEquals(DEFAULT_SERVER, servers.first())
    }

    // (c) selectServer with an existing id updates the selected server.
    @Test
    fun `selectServer with existing id updates the selected server`() = runTest {
        val repo = repository()
        // Selecting the default server's id explicitly — selection must reflect it.
        repo.selectServer(DEFAULT_SERVER.id)
        val selected = repo.observeSelectedServer().first()
        assertEquals(DEFAULT_SERVER, selected)
    }

    // (d) selectServer with an unknown id leaves the selection unchanged (no-op).
    @Test
    fun `selectServer with unknown id leaves selection unchanged`() = runTest {
        val repo = repository()
        val unknownId = ServerId("does-not-exist")
        repo.selectServer(unknownId)
        val selected = repo.observeSelectedServer().first()
        // Selection must still be the default — the unknown id must not clear or change it.
        assertEquals(DEFAULT_SERVER, selected)
    }

    // observeSelectedServer must never emit null on a fresh repository.
    @Test
    fun `observeSelectedServer is non-null on a fresh repository`() = runTest {
        val repo = repository()
        val selected = repo.observeSelectedServer().first()
        assertNotNull(selected)
        assertEquals(DEFAULT_SERVER.id, selected?.id)
    }
}
