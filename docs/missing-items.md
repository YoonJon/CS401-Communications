# Work To Be Done & Missing Items (Client Team's Scope)

# Work To Be Done & Missing Items (Client Team's Scope)

These items were identified during compilation after merging `origin/main` into
`feature/networking-complete`. They are **not** related to the networking layer
and should be resolved by the team that owns the relevant code.

---

## 1. Compile Errors (Must Fix First)

### Missing `userDirectory` field in `src/client/ClientUI.java` (lines 60–61)

**Error:** `package userDirectory does not exist`  
**Cause:** Getter methods reference a top-level field `userDirectory` that was removed during the GUI refactor. The directory view now lives at `cards.main.directoryView`, but the getters were not updated.  
**Affected code:**
```java
public DefaultListModel getDirectoryViewModel() { return userDirectory.listModel; }
public void   setDirectoryQuery(String q)        { userDirectory.searchField.setText(q); }
public String getDirectoryQuery()                { return userDirectory.searchField.getText(); }
```
**Fix needed:** Replace `userDirectory.*` with `cards.main.directoryView.*`

---

### Missing `chatView` field in `src/client/ClientUI.java` (lines 63–64)

**Error:** `package chatView does not exist`  
**Cause:** Getter methods reference a top-level field `chatView` that was removed during the GUI refactor. The conversation view now lives at `cards.main.conversationView`, but the getters were not updated.  
**Affected code:**
```java
public DefaultListModel getConversationViewModel() { return chatView.listModel; }
public void   setConversationQuery(String q)        { chatView.text.setText(q); }
public String getConversationQuery()                { return chatView.text.getText(); }
```
**Fix needed:** Replace `chatView.*` with `cards.main.conversationView.*`

---

### Missing symbol in `src/client/ClientUI.java` (lines 54–55)

**Error:** `cannot find symbol`  
**Cause:** Two symbols referenced on lines 54–55 cannot be resolved. Likely additional fields or types removed or renamed during the GUI refactor.  
**Fix needed:** GUI team to inspect lines 54–55 and update to use the current field/class names.

---

## 2. Incomplete / TODO Methods in `src/client/ClientController.java`

The following methods exist but have no implementation (`// TODO`):

| Method | Purpose |
|--------|---------|
| `close()` | Clean up socket and threads on exit |
| `processResponse(Response)` | Dispatch server responses, update model, notify GUI |
| `ensureConnected()` | Lazily establish TCP connection if not yet connected |
| `register(userId, realName, loginName, password)` | Send registration request to server |
| `login(loginName, password)` | Send login request to server |
| `logout()` | Send logout request, reset state |
| `sendMessage(cId, m)` | Send a chat message to a conversation |
| `searchDirectory(query)` | Filter user directory list |
| `searchConversationList(query)` | Filter conversation list |
| `adminConversationSearch(query)` | Admin: search all conversations |
| `createConversation(participants)` | Create a new conversation |
| `addToConversation(participants, cId)` | Add users to existing conversation |
| `leaveConversation(cId)` | Leave a conversation |
| `adminGetUserConversations(userId)` | Admin: get all conversations for a user |
| `joinConversation(cId)` | Join an existing conversation |
| `getFilteredDirectory(query)` | Return filtered copy of currentDirectory |
| `getFilteredConversationList(query)` | Return filtered copy of currentConversationList |
| `getFilteredAdminConversationSearch(q)` | Return filtered admin search results |

Also: `ClientController` constructor calls `gui.showMainView()` at startup — should be `gui.showLoginView()` since the user is not logged in yet.

---

## 3. Incomplete Views in `src/client/ClientUI.java`

The following inner view classes are missing layout (`// TODO: lay out components`):

| View class | Missing |
|------------|---------|
| `ConversationView` | Full layout, `text` field never initialized (will NPE on `setConversationQuery`) |
| `ConversationListView` | Full layout |
| `SelectUserWindow` | Full layout |

Also: `showRegisterError()`, `showLoginError()`, `showCreateConversationWindow()`, `showAdminConversationSearchWindow()` in `ClientUI` are all empty stubs.

---

## 4. Tests to Write (in `test/client/`)

Once the TODOs above are implemented, the following tests should be written:

### `ClientControllerTest.java`
| Test name | What to verify |
|-----------|---------------|
| `loginSendsCorrectRequest` | Calling `login()` enqueues a `Request` with type `LOGIN` and correct `LoginCredentials` payload |
| `registerSendsCorrectRequest` | Calling `register()` enqueues a `Request` with type `REGISTER` and correct `RegisterCredentials` payload |
| `processLoginSuccessUpdatesState` | Given a successful `LoginResult` response, `currentUser` is set and GUI switches to main view |
| `processLoginFailureShowsError` | Given a failed `LoginResult`, `currentUser` stays null and `showLoginError` is called |
| `processRegisterSuccessNavigatesToLogin` | Given a successful `RegisterResult`, GUI navigates back to login |
| `logoutClearsCurrentUser` | After `logout()`, `currentUser` is null and GUI shows login view |
| `sendMessageEnqueuesRequest` | `sendMessage()` creates a `Request` with type `SEND_MESSAGE` and correct payload |
| `getFilteredDirectoryFiltersCorrectly` | `getFilteredDirectory("al")` returns only users whose name/id contains "al" |
| `getFilteredConversationListFiltersCorrectly` | Similar filter check for conversation list |

### `ClientUIConsistencyTest.java` (extend existing)
| Test name | What to verify |
|-----------|---------------|
| `conversationViewTextIsInitialized` | `cards.main.conversationView.text` is not null after construction |
| `conversationListViewModelIsNotNull` | `conversationList.messageModel` is not null |
| `selectUserWindowModelIsNotNull` | `selectUserWindow.model` is not null |
| `appStartsOnLoginScreen` | After construction, CardLayout shows the `"login"` card, not `"main"` |
| `showLoginErrorDisplaysMessage` | Calling `showLoginError(LoginStatus.INVALID_CREDENTIALS)` shows a dialog / updates UI |
| `showRegisterErrorDisplaysMessage` | Calling `showRegisterError(RegisterStatus.USER_ALREADY_EXISTS)` shows a dialog / updates UI |

---

## 5. Status

| Issue | File | Lines | Owner | Fixed? |
|-------|------|-------|-------|--------|
| Duplicate constructor (merge artifact) | `src/shared/payload/LoginResult.java` | — | Networking | ✅ Fixed |
| Duplicate constructor (merge artifact) | `src/shared/payload/UserInfo.java` | — | Networking | ✅ Fixed |
| Missing `userDirectory` field | `src/client/ClientUI.java` | 60–61 | GUI/Client team | ❌ Pending |
| Missing `chatView` field | `src/client/ClientUI.java` | 63–64 | GUI/Client team | ❌ Pending |
| Missing symbol (unknown) | `src/client/ClientUI.java` | 54–55 | GUI/Client team | ❌ Pending |
| `ConversationView.text` never initialized | `src/client/ClientUI.java` | ~300 | GUI/Client team | ❌ Pending |
| Startup shows main view instead of login | `src/client/ClientController.java` | ~42 | Client team | ❌ Pending |
| 18 TODO methods unimplemented | `src/client/ClientController.java` | various | Client team | ❌ Pending |
| 3 view layouts missing | `src/client/ClientUI.java` | various | Client team | ❌ Pending |
| Tests not written | `test/client/` | — | Client team | ❌ Pending |
