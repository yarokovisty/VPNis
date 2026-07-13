package org.yarokovisty.vpnis.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.yarokovisty.vpnis.core.domain.connection.VpnConnectionState
import java.time.Instant

class ModelSanityTest {

    // ---- TrafficStats.EMPTY ----

    @Test
    fun `TrafficStats EMPTY equals TrafficStats with all zeros`() {
        // Given
        val allZeros = TrafficStats(rxBytes = 0L, txBytes = 0L, rxBps = 0L, txBps = 0L)

        // When / Then
        assertEquals(allZeros, TrafficStats.EMPTY)
    }

    // ---- Connected.traffic nullability ----

    @Test
    fun `Connected with null traffic EXPECT traffic property is null`() {
        // Given
        val server = Server(id = ServerId("s1"), name = "S1", config = "cfg")
        val since = Instant.ofEpochSecond(0L)

        // When
        val state = VpnConnectionState.Connected(server = server, since = since, traffic = null)

        // Then
        assertNull(state.traffic)
    }

    @Test
    fun `Connected with non-null traffic EXPECT traffic property round-trips`() {
        // Given
        val server = Server(id = ServerId("s1"), name = "S1", config = "cfg")
        val since = Instant.ofEpochSecond(0L)
        val stats = TrafficStats(rxBytes = 1024L, txBytes = 512L, rxBps = 100L, txBps = 50L)

        // When
        val state = VpnConnectionState.Connected(server = server, since = since, traffic = stats)

        // Then
        assertEquals(stats, state.traffic)
    }

    // ---- ServerId value-class equality ----

    @Test
    fun `ServerId instances with same value EXPECT equal`() {
        // Given
        val id1 = ServerId("abc")
        val id2 = ServerId("abc")

        // When / Then
        assertEquals(id1, id2)
    }

    @Test
    fun `ServerId instances with different values EXPECT not equal`() {
        // Given
        val id1 = ServerId("abc")
        val id2 = ServerId("xyz")

        // When / Then
        assertNotEquals(id1, id2)
    }

    // ---- ConnectionError variants ----

    @Test
    fun `ConnectionError Unknown with null message EXPECT message is null`() {
        // Given / When
        val error = ConnectionError.Unknown(message = null)

        // Then
        assertNull(error.message)
    }

    @Test
    fun `ConnectionError Unknown with message EXPECT message round-trips`() {
        // Given / When
        val error = ConnectionError.Unknown(message = "timeout")

        // Then
        assertEquals("timeout", error.message)
    }

    @Test
    fun `ConnectionError Unknown instances with same message EXPECT equal`() {
        // Given
        val a = ConnectionError.Unknown(message = "oops")
        val b = ConnectionError.Unknown(message = "oops")

        // When / Then
        assertEquals(a, b)
    }

    @Test
    fun `ConnectionError Unknown instances with different messages EXPECT not equal`() {
        // Given
        val a = ConnectionError.Unknown(message = "oops")
        val b = ConnectionError.Unknown(message = "other")

        // When / Then
        assertNotEquals(a, b)
    }

    @Test
    fun `ConnectionError PermissionDenied is a singleton`() {
        // Given / When
        val ref1 = ConnectionError.PermissionDenied
        val ref2 = ConnectionError.PermissionDenied

        // Then
        assertSame(ref1, ref2)
    }

    @Test
    fun `ConnectionError ServerUnreachable is a singleton`() {
        // Given / When
        val ref1 = ConnectionError.ServerUnreachable
        val ref2 = ConnectionError.ServerUnreachable

        // Then
        assertSame(ref1, ref2)
    }

    @Test
    fun `ConnectionError TunnelSetupFailed is a singleton`() {
        // Given / When
        val ref1 = ConnectionError.TunnelSetupFailed
        val ref2 = ConnectionError.TunnelSetupFailed

        // Then
        assertSame(ref1, ref2)
    }

    @Test
    fun `ConnectionError Revoked is a singleton`() {
        // Given / When
        val ref1 = ConnectionError.Revoked
        val ref2 = ConnectionError.Revoked

        // Then
        assertSame(ref1, ref2)
    }

    @Test
    fun `ConnectionError object variants are distinct from each other`() {
        // Given / When / Then
        assertTrue(ConnectionError.PermissionDenied != ConnectionError.ServerUnreachable)
        assertTrue(ConnectionError.PermissionDenied != ConnectionError.TunnelSetupFailed)
        assertTrue(ConnectionError.PermissionDenied != ConnectionError.Revoked)
        assertTrue(ConnectionError.ServerUnreachable != ConnectionError.TunnelSetupFailed)
        assertTrue(ConnectionError.ServerUnreachable != ConnectionError.Revoked)
        assertTrue(ConnectionError.TunnelSetupFailed != ConnectionError.Revoked)
    }

    @Test
    fun `ConnectionError object variants are not equal to Unknown`() {
        // Given
        val unknown = ConnectionError.Unknown(null)

        // When / Then
        assertFalse(ConnectionError.PermissionDenied == unknown)
        assertFalse(ConnectionError.ServerUnreachable == unknown)
        assertFalse(ConnectionError.TunnelSetupFailed == unknown)
        assertFalse(ConnectionError.Revoked == unknown)
    }
}
