package org.yarokovisty.vpnis.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LibXrayCoreImpl].
 *
 * Uses hand-written recording fakes consistent with the rest of :data:vpn's test style
 * (no mocking framework). [android.util.Log] stubs are silenced via
 * `testOptions { unitTests { isReturnDefaultValues = true } }`.
 */
class LibXrayCoreImplTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Encodes [json] as a base64 string the same way [LibxrayApi.runFromJson] returns
     * it. Uses `java.util.Base64` — safe in both unit-test JVM and Android runtimes.
     */
    private fun base64(json: String): String =
        java.util.Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    private val successResponse = base64("""{"success":true}""")
    private val failureResponse = base64("""{"success":false,"error":"boom"}""")

    private fun makeImpl(api: FakeLibxrayApi = FakeLibxrayApi(), datDir: String = "/data"): LibXrayCoreImpl =
        LibXrayCoreImpl(api = api, datDir = datDir)

    // -------------------------------------------------------------------------
    // Ordering — registerDialerController BEFORE runFromJson
    // -------------------------------------------------------------------------

    @Test
    fun `start EXPECT registerDialerController is called before runFromJson`() {
        // Given
        val api = FakeLibxrayApi(runFromJsonResult = successResponse)
        val impl = makeImpl(api)

        // When
        impl.start(configJson = "{}", protector = FakeVpnSocketProtector())

        // Then
        val registerIndex = api.callOrder.indexOf("registerDialerController")
        val runIndex = api.callOrder.indexOf("runFromJson")
        assertTrue("registerDialerController must appear in call log", registerIndex >= 0)
        assertTrue("runFromJson must appear in call log", runIndex >= 0)
        assertTrue(
            "registerDialerController (index $registerIndex) must come before runFromJson (index $runIndex)",
            registerIndex < runIndex,
        )
    }

    // -------------------------------------------------------------------------
    // Protector delegation — the registered lambda forwards to VpnSocketProtector
    // -------------------------------------------------------------------------

    @Test
    fun `start EXPECT registered lambda calls protector protect with the same fd`() {
        // Given
        val protector = FakeVpnSocketProtector(returnValue = true)
        val api = FakeLibxrayApi(runFromJsonResult = successResponse)
        val impl = makeImpl(api)
        impl.start(configJson = "{}", protector = protector)
        val capturedLambda = requireNotNull(api.capturedOnProtect) { "onProtect was never registered" }

        // When
        capturedLambda.invoke(42)

        // Then
        assertEquals(42, protector.lastProtectedFd)
    }

    @Test
    fun `start EXPECT registered lambda returns true when protector protect returns true`() {
        // Given
        val protector = FakeVpnSocketProtector(returnValue = true)
        val api = FakeLibxrayApi(runFromJsonResult = successResponse)
        val impl = makeImpl(api)
        impl.start(configJson = "{}", protector = protector)
        val capturedLambda = requireNotNull(api.capturedOnProtect)

        // When
        val result = capturedLambda.invoke(7)

        // Then
        assertTrue(result)
    }

    @Test
    fun `start EXPECT registered lambda returns false when protector protect returns false`() {
        // Given
        val protector = FakeVpnSocketProtector(returnValue = false)
        val api = FakeLibxrayApi(runFromJsonResult = successResponse)
        val impl = makeImpl(api)
        impl.start(configJson = "{}", protector = protector)
        val capturedLambda = requireNotNull(api.capturedOnProtect)

        // When
        val result = capturedLambda.invoke(7)

        // Then
        assertFalse(result)
    }

    // -------------------------------------------------------------------------
    // CallResponse decoding — success=true
    // -------------------------------------------------------------------------

    @Test
    fun `start with success CallResponse EXPECT returns true`() {
        // Given
        val api = FakeLibxrayApi(runFromJsonResult = successResponse)
        val impl = makeImpl(api)

        // When
        val result = impl.start(configJson = "{}", protector = FakeVpnSocketProtector())

        // Then
        assertTrue(result)
    }

    // -------------------------------------------------------------------------
    // CallResponse decoding — success=false
    // -------------------------------------------------------------------------

    @Test
    fun `start with failure CallResponse EXPECT returns false`() {
        // Given
        val api = FakeLibxrayApi(runFromJsonResult = failureResponse)
        val impl = makeImpl(api)

        // When
        val result = impl.start(configJson = "{}", protector = FakeVpnSocketProtector())

        // Then
        assertFalse(result)
    }

    // -------------------------------------------------------------------------
    // stop — delegation
    // -------------------------------------------------------------------------

    @Test
    fun `stop EXPECT api stop is called exactly once`() {
        // Given
        val api = FakeLibxrayApi(runFromJsonResult = successResponse)
        val impl = makeImpl(api)

        // When
        impl.stop()

        // Then
        assertEquals(1, api.stopCount)
    }
}

// -------------------------------------------------------------------------
// Fake collaborators
// -------------------------------------------------------------------------

/**
 * Recording fake [LibxrayApi].
 *
 * Tracks invocation order in [callOrder], captures the [onProtect] lambda so tests can
 * invoke it independently, and counts [stop] calls.
 */
private class FakeLibxrayApi(private val runFromJsonResult: String = "") : LibxrayApi {

    /** Ordered log of method names that have been called. */
    val callOrder: MutableList<String> = mutableListOf()

    /** The lambda most recently passed to [registerDialerController], or null if never called. */
    var capturedOnProtect: ((Int) -> Boolean)? = null
        private set

    var stopCount: Int = 0
        private set

    override fun registerDialerController(onProtect: (fd: Int) -> Boolean) {
        callOrder += "registerDialerController"
        capturedOnProtect = onProtect
    }

    override fun runFromJson(datDir: String, configJson: String): String {
        callOrder += "runFromJson"
        return runFromJsonResult
    }

    override fun stop() {
        stopCount++
    }
}

/**
 * Recording fake [VpnSocketProtector].
 *
 * Returns [returnValue] from every [protect] call and records the last fd passed to it.
 */
private class FakeVpnSocketProtector(private val returnValue: Boolean = true) : VpnSocketProtector {

    var lastProtectedFd: Int = -1
        private set

    override fun protect(socket: Int): Boolean {
        lastProtectedFd = socket
        return returnValue
    }
}
