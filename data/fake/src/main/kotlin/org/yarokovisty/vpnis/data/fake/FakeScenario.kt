package org.yarokovisty.vpnis.data.fake

/**
 * Controls which simulated lifecycle [FakeConnectionController] replays.
 *
 * Switch at any time via [FakeConnectionController.scenario]; the change takes
 * effect on the next [FakeConnectionController.connect] call.
 *
 * ### Acceptance checklist (automated coverage lands in #58)
 *
 * - **HappyPath** — Given scenario=HappyPath, When connect(), Then uiState transitions
 *   Connecting → Connected(traffic non-null) within ~handshakeDelayMs. Traffic counters
 *   tick upward every ~1 s while connected. When disconnect(), terminal state is
 *   Disconnected.
 *
 * - **HandshakeTimeout** — Given scenario=HandshakeTimeout, When connect(), Then
 *   uiState transitions Connecting → Error(ServerUnreachable) within ~timeoutDelayMs.
 *
 * - **ServerError** — Given scenario=ServerError, When connect(), Then uiState
 *   transitions Connecting → Error(TunnelSetupFailed) quickly (within ~quickDelayMs).
 *
 * - **SuddenRevoke** — Given scenario=SuddenRevoke, When connect() reaches Connected,
 *   Then after ~revokeDelayMs uiState transitions to Error(Revoked) — simulating an
 *   OS-initiated tunnel revocation.
 *
 * - **ConnectDisconnectRace** — Given scenario=ConnectDisconnectRace, When connect()
 *   is called followed immediately by disconnect(), Then the terminal state is
 *   Disconnected and no late Connected emission leaks (the in-flight handshake job is
 *   cancelled cleanly).
 */
public enum class FakeScenario {
    HappyPath,
    HandshakeTimeout,
    ServerError,
    SuddenRevoke,
    ConnectDisconnectRace,
}
