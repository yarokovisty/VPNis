package org.yarokovisty.vpnis.data.server

import org.junit.Assert.assertTrue
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.model.ServerId

class DefaultServerTest {

    @Test
    fun `DEFAULT_SERVER config is not blank`() {
        assertTrue(DEFAULT_SERVER.config.isNotBlank())
    }

    @Test
    fun `DEFAULT_SERVER config starts with vless scheme`() {
        assertTrue(DEFAULT_SERVER.config.startsWith("vless://"))
    }

    @Test
    fun `DEFAULT_SERVER id is vpnis-default-nl-1`() {
        assertTrue(DEFAULT_SERVER.id == ServerId("vpnis-default-nl-1"))
    }

    @Test
    fun `DEFAULT_SERVER name is non-blank`() {
        assertTrue(DEFAULT_SERVER.name.isNotBlank())
    }
}
