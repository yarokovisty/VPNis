package org.yarokovisty.vpnis.data.fake

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [FakeNotificationPermissionState] (issue #127, task T-5).
 *
 * The three cases together falsify a naïve implementation that writes [isGranted] directly
 * from [setGranted]: in the naïve version, case (b) would emit `false` without [refresh],
 * making the test trivially green regardless of whether [refresh] does anything.
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
}
