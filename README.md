# CS401-Communications
**Group 4** — Java chat application with a client/server architecture over TCP.

---

## What This Project Is

A multi-user messaging system where:
- Clients connect over the network, log in, and send/receive messages in conversations.
- The server receives all requests, processes them, and pushes responses back to the right clients.
- All communication travels as typed Java objects serialized over a persistent TCP socket.

---

## Project Structure

```
CS401-Communications/
├── src/
│   ├── client/          # Client-side application
│   ├── server/          # Server-side application
│   └── shared/          # Code used by both client and server
│       ├── enums/       # Shared enum types
│       ├── networking/  # TCP connection logic
│       └── payload/     # Objects sent across the network
├── test/
│   └── shared/
│       └── networking/  # Tests for the networking layer
└── lib/
    └── junit-platform-console-standalone-1.10.5.jar
```

---

## Source Files

### `src/client/`

| File | What it does |
|---|---|
| `ClientController.java` | The brain of the client. Manages the connection to the server, maintains login state, tracks the current user's conversations and directory, and drives the UI. |
| `ClientUI.java` | The Swing GUI. Shows login, registration, chat, and directory screens. Talks to `ClientController` for data. |

### `src/server/`

| File | What it does |
|---|---|
| `ServerController.java` | The brain of the server. Receives every `Request` from a connected client, routes it to `DataManager` for processing, and returns a `Response`. Tracks all active logged-in sessions in a thread-safe map. |
| `DataManager.java` | Handles all data: users, conversations, messages, and the employee roster. Loads data from disk on startup and will persist changes on shutdown. |
| `User.java` | Internal server-side representation of a registered user (ID, name, login name, hashed password, role, and per-conversation read timestamps). |

### `src/shared/networking/`

These files handle the raw TCP communication — everything below `ServerController` and `ClientController`.

| File | What it does |
|---|---|
| `ConnectionListener.java` | Opens a server socket on a given port. Loops accepting incoming client connections and hands each one to a new `ConnectionHandler` running on a thread pool. Call `close()` to stop cleanly. |
| `ConnectionHandler.java` | Manages one client connection for its entire lifetime. Internally runs two threads: one reads incoming `Request` objects and dispatches them to the server; the other writes outgoing `Response` objects back. Handles login/logout session transitions automatically. |
| `Request.java` | A message sent **from the client to the server**. Carries a `RequestType` tag, an optional `RequestPayload`, and an optional sender ID. |
| `Response.java` | A message sent **from the server to the client**. Carries a `ResponseType` tag and an optional `ResponsePayload`. |

### `src/shared/enums/`

Small enum types shared between client and server.

| File | What it represents |
|---|---|
| `RequestType.java` | Every action a client can request (e.g. `LOGIN`, `REGISTER`, `MESSAGE`, `PING`, `LOGOUT`, …) |
| `ResponseType.java` | Every response the server can send back (e.g. `LOGIN_RESULT`, `PONG`, `DIRECTORY_RESULT`, …) |
| `LoginStatus.java` | Outcome of a login attempt (`SUCCESS`, `INVALID_CREDENTIALS`, …) |
| `RegisterStatus.java` | Outcome of a registration attempt (`SUCCESS`, `USER_ID_INVALID`, `USER_ID_TAKEN`) |
| `ConnectionStatus.java` | Client-side connection state (`NOT_CONNECTED`, `CONNECTED`, …) |
| `UserType.java` | Role of a user (`USER`, `ADMIN`) |
| `ConversationType.java` | Whether a conversation is a direct message or a group chat |

### `src/shared/payload/`

These are the objects placed inside a `Request` or `Response`. Each one corresponds to a specific action.

| File | Direction | Used for |
|---|---|---|
| `LoginCredentials.java` | Client → Server | Username and password for login |
| `LoginResult.java` | Server → Client | Login outcome, user info, and conversation list on success |
| `RegisterCredentials.java` | Client → Server | Employee ID, login name, password, and legal name for registration |
| `RegisterResult.java` | Server → Client | Registration outcome |
| `UserInfo.java` | Server → Client | Public info about a user (employee ID, display name, role) |
| `RawMessage.java` | Client → Server | A message text and the target conversation ID |
| `Message.java` | Server → Client | A delivered message with sender info and timestamp |
| `Conversation.java` | Server → Client | A full conversation object (metadata + messages) |
| `ConversationMetadata.java` | Server → Client | Lightweight conversation summary (ID, name, type, members) |
| `CreateConversationPayload.java` | Client → Server | Name, type, and initial member list for a new conversation |
| `JoinConversationPayload.java` | Client → Server | Conversation ID to join |
| `LeaveConversationPayload.java` | Client → Server | Conversation ID to leave |
| `LeaveResult.java` | Server → Client | Outcome of a leave request |
| `AddToConversationPayload.java` | Client → Server | Conversation ID and user ID to add |
| `DirectoryQuery.java` | Client → Server | Search string for looking up users |
| `DirectoryResult.java` | Server → Client | List of `UserInfo` matching the search |
| `UpdateReadMessages.java` | Client → Server | Mark messages in a conversation as read |
| `ReadMessagesUpdated.java` | Server → Client | Confirmation that the read marker was updated |
| `AdminConversationQuery.java` | Client → Server | Admin search for a conversation by name or member |
| `AdminConversationResult.java` | Server → Client | Results of an admin conversation search |

---

## Test Files

### `test/shared/networking/`

| File | What it does |
|---|---|
| `NetworkingTest.java` | 14 JUnit 5 tests covering the full TCP path. Each test starts a real server socket on a loopback port and uses a `TestClient` helper to send requests and receive responses over an actual socket. No mocked I/O. |

### `test/shared/networking/fixtures/`

| File | What it does |
|---|---|
| `FakePayload.java` | A minimal payload carrying a single string. Used in tests to verify objects survive serialization without depending on any real payload class. |
| `FakeServerController.java` | A test-only stub of `ServerController`. Records every request received and lets each test set a custom response handler via `setRequestHandler()`. |
| `NetworkingSeedData.java` | Hard-coded test data factory. Provides fixed user accounts (Alice, Bob, Carol, Admin), a preloaded employee roster, and factory methods for every request/response type tested. |

---

## How a Request Travels Through the System

```
ClientController
    └─► Request object created with a RequestType + Payload
            └─► Sent over TCP socket
                    └─► ConnectionListener accepts the connection
                            └─► ConnectionHandler reads the Request
                                    └─► ServerController.processRequest()
                                            └─► DataManager does the work
                                    └─► Response built and enqueued
            └─► Response received over TCP socket
    └─► ClientController updates state / UI
```

---

## How to Build and Run Tests

**1. Compile production sources**
```
javac -cp lib\junit-platform-console-standalone-1.10.5.jar -sourcepath src -d out\production src\client\*.java src\server\*.java src\shared\enums\*.java src\shared\networking\*.java src\shared\payload\*.java
```

**2. Compile test sources**
```
javac -cp "out\production;lib\junit-platform-console-standalone-1.10.5.jar" -sourcepath test -d out\test test\shared\networking\fixtures\*.java test\shared\networking\NetworkingTest.java
```

**3. Run networking tests**
```
java -jar lib\junit-platform-console-standalone-1.10.5.jar --classpath "out\production;out\test" --select-class=shared.networking.NetworkingTest --disable-banner
```

---

## Employee-ID Model

Every user account is tied to a company-issued **employee ID** (not a user-chosen name).
The server holds a preloaded roster (`DataManager.authorizedUsers`) mapping each employee ID to their legal name.
Registration only succeeds when the submitted employee ID **and** legal name both match an entry in that roster.
This prevents outsiders from creating accounts.

