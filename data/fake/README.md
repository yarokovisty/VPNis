# :data:fake — Configurable VPN Fakes

Pure-JVM module providing configurable in-memory implementations of
`ConnectionController` and `ServerRepository` for development builds and unit
tests (#58). No Android framework dependency — consumable as a plain JVM library
in both the Android app and `androidTest`.

## Swap invariant

`fakeVpnModule` binds `FakeConnectionController` and `FakeServerRepository`
as Koin singletons. In epic B (#66) this module is replaced by `:data:vpn`
(which registers the same interfaces) without touching `:feature:home` or `:app`.

---

## FakeScenario acceptance checklist

These are binary pass/fail criteria. Automated tests live in #58 (will inject
`UnconfinedTestDispatcher` / `TestCoroutineScheduler` via constructor).

### HappyPath

- **Given** `scenario = HappyPath`, **When** `connect(server)` is called
- **Then** state transitions: `Disconnected → Connecting(server)`
- **Then** within ≤ `handshakeDelayMs`, state transitions to `Connected(server, since, TrafficStats.EMPTY)`
- **Then** every `trafficTickMs`, a new `Connected` emission with `rxBytes > 0`, `txBytes > 0`,
  `rxBps > 0`, `txBps > 0` appears (traffic is live)
- **When** `disconnect()` is subsequently called
- **Then** terminal state is `Disconnected`, no further `Connected` emissions leak

### HandshakeTimeout

- **Given** `scenario = HandshakeTimeout`, **When** `connect(server)` is called
- **Then** state: `Disconnected → Connecting(server)`
- **Then** within ≤ `timeoutDelayMs`, state transitions to `Error(ServerUnreachable)`
- **Then** no `Connected` emission appears

### ServerError

- **Given** `scenario = ServerError`, **When** `connect(server)` is called
- **Then** state: `Disconnected → Connecting(server)`
- **Then** within ≤ `quickDelayMs`, state transitions to `Error(TunnelSetupFailed)`
- **Then** no `Connected` emission appears

### SuddenRevoke

- **Given** `scenario = SuddenRevoke`, **When** `connect(server)` is called
- **Then** state: `Disconnected → Connecting(server) → Connected(server, ...)`
- **Then** within ≤ `revokeDelayMs` after Connected, state transitions to `Error(Revoked)`
- **Then** no further emissions after `Error(Revoked)`

### ConnectDisconnectRace

- **Given** `scenario = ConnectDisconnectRace`, **When** `connect(server)` is called
  and `disconnect()` is called before the handshake delay elapses
- **Then** terminal state is `Disconnected`
- **Then** no `Connected` emission appears (the in-flight handshake job was cancelled)

---

## All transitions satisfy `isLegalTransition`

`FakeConnectionController.transition()` validates every emission against the domain
rule before writing to the `MutableStateFlow`. Illegal transitions are silently
dropped rather than crashing — this guards against bugs in scenario logic.
