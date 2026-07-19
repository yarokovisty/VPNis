package org.yarokovisty.vpnis.data.fake

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [FakeNotificationPermissionState] (issue #127, task T-5; updated T-1).
 *
 * The three core cases together falsify a naïve implementation that writes [isGranted] directly
 * from [setGranted]: in the naïve version, case (b) would emit `false` without [refresh],
 * making the test trivially green regardless of whether [refresh] does anything.
 *
 * Additional cases (d)/(e) cover the new T-1 contract: [FakeNotificationPermissionState.refresh]
 * must return the backing value, and [FakeNotificationPermissionState.channelId] must equal the
 * expected tunnel channel id.
 */
class FakeNotificationPermissionStateTest {

    // -------------------------------------------------------------------------
    // (a) Default state — isGranted emits true at construction
    // -------------------------------------------------------------------------

    @Test
    fun `default state EXPECT isGranted emits true`() = runTest {
        // Given
        val fake = FakeNotificationPermissionState()

        // When / Then — no action needed; assert the initial emission
        fake.isGranted.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // (b) setGranted(false) alone — isGranted must NOT change (pull semantics)
    // -------------------------------------------------------------------------

    @Test
    fun `setGranted false with no refresh EXPECT isGranted still emits true`() = runTest {
        // Given
        val fake = FakeNotificationPermissionState()

        // When
        fake.setGranted(false)

        // Then — backing changed, but flow must not have emitted yet
        fake.isGranted.test {
            assertEquals(true, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // (c) setGranted(false) + refresh() — isGranted must surface the new value
    // -------------------------------------------------------------------------

    @Test
    fun `setGranted false then refresh EXPECT isGranted emits false`() = runTest {
        // Given
        val fake = FakeNotificationPermissionState()

        // When
        fake.setGranted(false)
        fake.refresh()

        // Then — refresh() is what surfaces the backing value into the flow
        fake.isGranted.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // (d) refresh() return value — must equal the backing value (T-1)
    // -------------------------------------------------------------------------

    @Test
    fun `refresh returns true when backing is true`() = runTest {
        // Given — default backing = true
        val fake = FakeNotificationPermissionState()

        // When
        val returned = fake.refresh()

        // Then — return value matches the value pushed into isGranted
        assertEquals(true, returned)
        fake.isGranted.test {
            assertEquals(returned, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh returns false when backing is false`() = runTest {
        // Given
        val fake = FakeNotificationPermissionState()
        fake.setGranted(false)

        // When
        val returned = fake.refresh()

        // Then — return value matches the value pushed into isGranted
        assertEquals(false, returned)
        fake.isGranted.test {
            assertEquals(returned, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // (e) channelId — must equal the tunnel channel literal (T-1)
    // -------------------------------------------------------------------------

    @Test
    fun `channelId EXPECT vpnis_tunnel`() {
        val fake = FakeNotificationPermissionState()
        assertEquals("vpnis_tunnel", fake.channelId)
    }
}
