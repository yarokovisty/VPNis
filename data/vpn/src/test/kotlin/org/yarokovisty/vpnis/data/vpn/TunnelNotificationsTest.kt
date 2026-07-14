package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelNotificationsTest {

    // contentFor is the pure, Context-free seam consumed by build(). It is the only unit-
    // testable part of TunnelNotifications; the rest needs the Android framework (device /
    // Robolectric) and is covered by device QA (issue #67). Issue #63 adds NotificationContent
    // subtypes and matching branches here — this test locks the current contract.

    @Test
    fun `contentFor Default EXPECT VPNis title`() {
        // Given / When
        val (title, _) = TunnelNotifications.contentFor(NotificationContent.Default)

        // Then
        assertEquals("VPNis", title)
    }

    @Test
    fun `contentFor Default EXPECT tunnel active text`() {
        // Given / When
        val (_, text) = TunnelNotifications.contentFor(NotificationContent.Default)

        // Then
        assertEquals("Tunnel active", text)
    }
}
