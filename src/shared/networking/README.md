# Networking Layer — Implementation Overview

## What Was Built

The networking layer provides the full TCP transport between clients and the server.
Prior to this work all networking classes existed as empty stubs with TODO bodies.
Everything listed below was designed and implemented from scratch.

---

## Work Completed

### Transport Design

- **Protocol** — Java object serialization over a persistent TCP socket.
  Every exchange is a typed `Request` (client → server) or `Response` (server → client).
  Both carry an enum type tag and a `Payload` object.

- **Multithreaded model** — each connection spawns two child threads inside one handler:
  one thread reads incoming requests; a second daemon thread drains the outbound queue.
  The listener itself accepts connections on a cached thread pool so many clients can be
  served simultaneously without blocking each other.

- **Stream-header ordering** — `ObjectOutputStream` is created and flushed before
  `ObjectInputStream` on both the server side and the client side.
  This is required to avoid a known Java stream-header deadlock when both ends open
  streams concurrently.

- **Idempotent shutdown** — an `AtomicBoolean` guards the close sequence so calling
  `close()` from multiple threads or multiple times is safe.

- **Session lifecycle** — the handler monitors every `LOGIN_RESULT` response.
  On success it calls `ServerController.addSession()` with the user's employee ID.
  On logout or disconnect it calls `ServerController.removeSession()`.
  The session map in `ServerController` was changed to `ConcurrentHashMap` to match.

- **PING / heartbeat** — the handler recognises `PING` requests and records a
  `lastPingReceived` timestamp without forwarding them to the application layer.
  The server returns `PONG` immediately.

- **Thread-safe response delivery** — outbound responses are placed on a
  `LinkedBlockingQueue`. The `ResponseSender` thread dequeues and writes them in
  strict FIFO order, so response ordering is guaranteed regardless of how fast
  the application layer produces them.

- **OS-assigned ports for tests** — `ConnectionListener` accepts port `0`, which lets
  the OS pick a free port. A `CountDownLatch` signals once the socket is bound so tests
  can retrieve the actual port number with `getLocalPort()` without polling.

---

## Files — Production (`src/shared/networking/`)

| File | Purpose |
|---|---|
| `ConnectionListener.java` | Opens a `ServerSocket`, waits for incoming TCP connections in a loop, and submits each accepted socket to a cached thread pool as a new `ConnectionHandler`. Exposes `getLocalPort()` for test port discovery and `close()` for clean shutdown. |
| `ConnectionHandler.java` | Manages one client connection end-to-end. Contains the `RequestListener` inner thread (reads requests, dispatches to `ServerController`, enqueues responses) and the `ResponseSender` inner daemon thread (drains the `BlockingQueue` and writes responses). Handles auth-session transitions and disconnect cleanup. |
| `Request.java` | Serializable message sent from a client to the server. Carries a `RequestType` enum tag and a `Payload`. |
| `Response.java` | Serializable message sent from the server to a client. Carries a `ResponseType` enum tag and a `Payload`. |

---

## Files — Test (`test/shared/networking/`)

| File | Purpose |
|---|---|
| `NetworkingTest.java` | JUnit 5 test class. Starts a real `ConnectionListener` on a loopback socket for each test and connects one or more `TestClient` helpers. Covers the full TCP path end-to-end with no mocked I/O. |

---

## Files — Test Fixtures (`test/shared/networking/fixtures/`)

| File | Purpose |
|---|---|
| `FakePayload.java` | Minimal `Payload` implementation carrying a single string. Used in tests to verify payload identity after a full serialization round-trip without depending on any real payload class. |
| `FakeServerController.java` | Controllable stub extending `ServerController`. Records every request it receives in a thread-safe list. The response logic is swappable per-test via `setRequestHandler()`. Session management methods are inherited from the real `ServerController` so auth-transition assertions work correctly. |
| `NetworkingSeedData.java` | Hard-coded, deterministic test data factory. Provides fixed user constants, a preloaded employee roster, and factory methods for all request and response types used in tests. No file I/O or database dependency. |

---

## Supporting Changes (outside networking/)

| File | Change |
|---|---|
| `src/shared/payload/UserInfo.java` | Added parameterised constructor `(userId, name, userType)` so the networking layer can build `UserInfo` objects from a login result without reflection. |
| `src/shared/payload/LoginResult.java` | Added constructor `(LoginStatus, UserInfo, ArrayList<Conversation>)` so a successful login response carries the full user profile. |
| `src/server/ServerController.java` | Changed `activeSessions` from `HashMap` to `ConcurrentHashMap` to support concurrent access from multiple handler threads. |

---

## Employee-ID Model

`userId` in this system is a company-issued employee ID, not a user-chosen value.
The server holds a preloaded roster mapping each employee ID to their legal name.
Registration succeeds only when the submitted `(userId, name)` pair exists in that roster.

`NetworkingSeedData` models this with:
- `PRELOADED_EMPLOYEES` — unmodifiable map of employee ID → legal name (Alice, Bob, Carol, Admin)
- `PRELOADED_ADMIN_IDS` — list of IDs that receive admin privileges
- Registration request factories for the valid first-registration path (Carol),
  the duplicate-account path (Alice already registered), the wrong-name path,
  and the unknown-ID path

---

## Test Coverage

**Test file:** `test/shared/networking/NetworkingTest.java`  
**Total tests:** 14  
**Result:** 14 / 14 passed  

| # | Test | What is verified |
|---|---|---|
| 1 | `requestSerializesAndDeserializes` | A `Request` object survives a full Java serialization round-trip in memory with payload value intact |
| 2 | `responseSerializesAndDeserializes` | A `Response` object survives a full Java serialization round-trip in memory with payload value intact |
| 3 | `loginResultSerializeWithUserInfo` | A `LoginResult` response carrying a populated `UserInfo` (employee ID + name + type) round-trips correctly |
| 4 | `pingPongRoundTrip` | A single `PING` request sent over a live loopback socket receives a `PONG` response |
| 5 | `listenerForwardsRequestToServerController` | `ConnectionListener` accepts the connection and the received request reaches `ServerController.processRequest()` |
| 6 | `responsesDeliveredInFifoOrder` | Five requests sent in sequence receive their five distinct responses back in the same order (FIFO queue guarantee) |
| 7 | `clientDisconnectStopsHandlerCleanly` | When the client closes the socket, the handler's reader and writer threads both stop without hanging |
| 8 | `multipleConcurrentClientsGetIsolatedResponses` | Ten clients connecting and sending simultaneously each receive their own correct `PONG` without cross-contamination |
| 9 | `pingUpdatesLastPingTimestamp` | A `PING` sent after a successful login reaches the handler and produces a `PONG`, confirming the heartbeat path runs |
| 10 | `successfulLoginRegistersSession` | After a `LOGIN_RESULT SUCCESS` response, the user's employee ID appears in `ServerController.activeSessions` |
| 11 | `failedLoginDoesNotRegisterSession` | After a `LOGIN_RESULT INVALID_CREDENTIALS` response, no session entry is added |
| 12 | `listenerStopsAcceptingAfterClose` | After `ConnectionListener.close()` is called, a new connection attempt is refused |
| 13 | `sessionRemovedOnDisconnect` | After a logged-in client disconnects, the handler removes the session from `activeSessions` |
| 14 | `multipleSequentialRequestsOnOneConnection` | Twenty requests sent one after another on a single connection all receive correct responses |

---

## Build & Run

**Compile production sources**
```
javac -cp lib\junit-platform-console-standalone-1.10.5.jar -sourcepath src -d out\production <src/**/*.java>
```

**Compile test sources**
```
javac -cp "out\production;lib\junit-platform-console-standalone-1.10.5.jar" -sourcepath test -d out\test <test/**/*.java>
```

**Run tests**
```
java -jar lib\junit-platform-console-standalone-1.10.5.jar \
  --classpath "out\production;out\test" \
  --select-class=shared.networking.NetworkingTest \
  --disable-banner
```
