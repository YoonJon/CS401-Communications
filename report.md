# CS401-Communications — Code Review Report

**Date:** 2026-04-27  
**Reviewed by:** Static analysis + manual inspection  
**Scope:** All `.java` files under `src/`

---

## Summary

Compilation succeeds for 35 of 36 source files (JUnit 5 missing for `ClientUITest.java`).
`javac -Xlint:all` produced **33 warnings** and manual review found **9 distinct bugs or conflicts** across 5 categories.

---

## Critical Bugs

### 1. `handleJoinConversation` does not add user to Conversation participants

**File:** `src/server/DataManager.java:747–753`

```java
public Response handleJoinConversation(Request request) {
    ...
    linkUserToConversation(userId, conversationId);   // updates index maps only
    return new Response(ResponseType.CONVERSATION, conversationsByConversationID.get(conversationId));
}
```

`linkUserToConversation` updates the two lookup maps but never calls `conversation.addParticipants(...)`. The user does not appear in `Conversation.participants`, so they are invisible to other participants and cannot receive future messages broadcast to that conversation. Compare with `handleAddToConversation` which correctly calls `existing.addParticipants(netNew)`.

**Fix:** Retrieve the `Conversation` object and call `conversation.addParticipants(...)` before returning, then `persistConversation(conversation)`.

---

### 2. Resource leak — `ObjectInputStream` never closed in conversation load loop

**File:** `src/server/DataManager.java:343–356`

```java
ObjectInputStream in = null;
try {
    for (File f : listOfConversationFiles) {
        FileInputStream fs = new FileInputStream(f);
        in = new ObjectInputStream(fs);   // previous `in` overwritten without close
        ...
    }
} catch (...) { ... }
```

Each loop iteration overwrites `in` without closing the previous stream. On a server with many conversations this exhausts file descriptors at startup.

**Fix:** Use try-with-resources inside the loop:
```java
for (File f : listOfConversationFiles) {
    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
        ...
    }
}
```

---

### 3. `writeDirty` clears dirty sets before writes complete — silent data loss on failure

**File:** `src/server/DataManager.java:204–229`

```java
Set<String> userIds = new HashSet<>(dirtyUsers);
dirtyUsers.clear();          // IDs cleared BEFORE writes succeed
...
for (String userId : userIds) {
    // if this throws, the ID is gone from the dirty set and never retried
}
```

If any file write fails mid-loop the dirty ID is already cleared, so the change is never retried and the in-memory state diverges from disk.

**Fix:** Only remove an ID from the dirty set after its file write succeeds, or restore failed IDs back into the dirty set in a `catch` block.

---

## Functional Bugs

### 4. `DUPLICATE_SESSION` status never returned

**File:** `src/server/DataManager.java:586–617`

`ClientController.handleLoginResultResponse` (line 252) handles `LoginStatus.DUPLICATE_SESSION`, but `DataManager.handleLogin` never checks whether the user already has an active session in `ServerController`. Logging in from two clients simultaneously silently replaces the first session with no warning to either client.

**Fix:** Pass `ServerController` (or a `hasActiveSession` predicate) to `DataManager`, or check in `ServerController.processRequest` before calling `handleLogin`.

---

### 5. `ServerController.main` ignores port argument — always binds to 8080

**File:** `src/server/ServerController.java:41–46`

```java
String dataRootPath = args.length > 0 ? args[0] : "data";
// args[1] is never read; port is hardcoded
ServerController serverController = new ServerController(dataRootPath, 8080);
```

The constructor and Javadoc both document a configurable port, but `main` never reads `args[1]`.

**Fix:**
```java
int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
new ServerController(dataRootPath, port);
```

---

### 6. Admin conversation results do not update the GUI

**File:** `src/client/ClientController.java:348–352`

`handleAdminConversationResultResponse` stores results in `currentAdminConversationSearch` but never notifies the GUI. The admin search window will remain blank until the user manually triggers a refresh (if one exists).

**Fix:** Add a `SwingUtilities.invokeLater` call to refresh the admin search view after updating the list.

---

## Design / Consistency Issues

### 7. Static counters shared across all `DataManager` instances

**File:** `src/server/DataManager.java:34,40`

```java
private static final AtomicLong messageSequenceCounter = new AtomicLong(0);
private static final AtomicLong conversationIdCounter  = new AtomicLong(0);
```

Being `static`, these counters are shared across every `DataManager` in the JVM. Multiple instances (e.g., parallel test runs) corrupt each other's IDs.

**Fix:** Change to instance fields (`private final AtomicLong`).

---

### 8. `UserInfo.setLastRead` mutates a supposedly immutable snapshot

**File:** `src/shared/networking/User.java:117–119`

`UserInfo` fields are `final`, and `getLastReadMap()` returns an unmodifiable view, yet `setLastRead` directly mutates the internal `HashMap`. The class contracts immutability but allows mutation through a public method.

**Fix:** Remove `setLastRead` from `UserInfo` (clients should optimistically update local state, not mutate the server-sourced snapshot) or make the mutability explicit in the Javadoc.

---

### 9. `LOGIN_NAME_INVALID` status is unreachable

**File:** `src/server/DataManager.java:558–582` / `src/shared/enums/RegisterStatus.java`

`RegisterStatus.LOGIN_NAME_INVALID` is defined and handled client-side, but the server never validates login name format and never returns this status, leaving the enum value dead.

**Fix:** Add login name validation (e.g., non-empty, no special characters) in `handleRegister` and return `LOGIN_NAME_INVALID` when the check fails.

---

## Compilation Issue

### 10. `ClientUITest.java` — JUnit 5 dependency missing

**File:** `src/client/ClientUITest.java`

The file imports `org.junit.jupiter.api.Assertions` and `org.junit.jupiter.api.Test` but the project has no build file and no JUnit JARs on the classpath. The class body is also empty. It cannot be compiled as-is.

**Fix:** Add JUnit 5 JARs to the classpath (or adopt Maven/Gradle) and add actual test cases.

---

## Compiler Warnings (33 total)

| Category | Count | Details |
|---|---|---|
| Missing `serialVersionUID` | 19 | All `Serializable` payload and UI classes. Risk: `InvalidClassException` when reading persisted `.user` / `.conversation` files after a recompile. |
| `this-escape` | 3 | `ClientController` (line 106), `DataManager` (line 120), `ServerController` (line 79) — `this` passed to threads/objects before the constructor finishes. |
| Non-serializable field in `Serializable` class | 2 | `User.lastRead` and `UserInfo.lastRead` (`Map<Long, Long>`) — `HashMap` is serializable in practice but the compiler cannot verify it statically. |

---

## Priority Summary

| Priority | Issue |
|---|---|
| High | #1 — `handleJoinConversation` broken |
| High | #2 — File handle leak in `loadData` |
| High | #3 — `writeDirty` data loss on write failure |
| Medium | #4 — No duplicate session detection |
| Medium | #5 — Port argument ignored in `main` |
| Medium | #6 — Admin results don't update GUI |
| Low | #7 — Static counters shared across instances |
| Low | #8 — `UserInfo` mutability inconsistency |
| Low | #9 — `LOGIN_NAME_INVALID` unreachable |
| Info | #10 — `ClientUITest` uncompilable / empty |
