# Product Demo Checklist (Jon, Quan, Harumi)

Use this as a literal step-by-step script during the live demo.

## Roles and projector policy

- [ ] **Harumi = admin + presenter** (projector shows Harumi terminal whenever a single client is enough).
- [ ] **Quan = secondary client** (used when multi-client behavior must be shown).
- [ ] **Jon = secondary client** (used when multi-client behavior must be shown).
- [ ] **Server terminal** is shown only for startup/shutdown.

## Terminal mapping

- [ ] Open 4 terminals at repo root.
- [ ] Terminal 1 = **Server**
- [ ] Terminal 2 = **Jon**
- [ ] Terminal 3 = **Quan**
- [ ] Terminal 4 = **Harumi**

## Pre-demo setup

- [ ] (Optional) Re-seed for predictable state:
  - `mkdir -p out`
  - `javac -d out -sourcepath src $(find src -name "*.java")`
  - `javac -d out -cp out -sourcepath utils utils/SeedSerializedData.java`
  - `java -cp out SeedSerializedData data 80`
- [ ] Build app:
  - `mkdir -p out`
  - `javac -d out -sourcepath src $(find src -name "*.java")`

## Server startup

- [ ] **Jon** (Terminal 1): run the provided server `.jar` that includes the IP finder utility.
- [ ] Capture the IPv4 printed by the utility (this is the host IP clients will use).
- [ ] Confirm the server process is running and listening on port `8080`.

## Client startup

- [ ] **Jon** (Terminal 2): `java -cp out client.ClientController <SERVER_IP>`
- [ ] **Quan** (Terminal 3): `java -cp out client.ClientController <SERVER_IP>`
- [ ] **Harumi** (Terminal 4): `java -cp out client.ClientController <SERVER_IP>`
- [ ] Confirm all three show login screen.

## Demo identities

Seeded defaults:
- Jon: `pretzul` / `a`
- Quan: `user123` / `a`
- Harumi: `user456` / `a`

- [ ] Confirm Harumi account has Admin button (if not, swap roles so the admin user becomes the single-client presenter).

---

## UC-01 Account Creation (single-client, projector = Harumi)

- [ ] **Harumi** opens register screen.
- [ ] **Harumi** enters valid employee ID + matching name + unique login + password.
- [ ] **Harumi** submits and confirms success.
- [ ] **Harumi** corner case: invalid name/employee ID -> rejection.
- [ ] **Harumi** corner case: duplicate login -> rejection.

## UC-02 System Login (all clients)

- [ ] **Jon** logs in on Jon client.
- [ ] **Quan** logs in on Quan client.
- [ ] **Harumi** logs in on Harumi client.
- [ ] Confirm all reach main interface.
- [ ] **Harumi** corner case on projector: wrong password -> rejection.
- [ ] **Quan** corner case: attempt duplicate session login for Harumi account -> rejection.

## UC-03 Send Message (multi-client)

- [ ] **Jon** opens an existing conversation and sends non-empty message.
- [ ] **Quan** confirms message appears in same conversation.
- [ ] **Harumi** confirms recency ordering updates in conversation list.
- [ ] **Harumi** corner case: empty message send attempt -> not sent.

## UC-04 View User Directory (single-client, projector = Harumi)

- [ ] **Harumi** types partial name in directory search (example `Har`).
- [ ] **Harumi** confirms matches appear.
- [ ] **Harumi** clears search and confirms full list returns.
- [ ] **Harumi** corner case: nonsense query -> no results.

## UC-05 View Conversation History (single-client, projector = Harumi)

- [ ] **Harumi** searches conversation list by participant name/ID.
- [ ] **Harumi** confirms filtered results appear.
- [ ] **Harumi** opens one result.
- [ ] **Harumi** corner case: no-match query -> empty results.

## UC-06 View Conversation (single-client, projector = Harumi)

- [ ] **Harumi** selects a conversation.
- [ ] **Harumi** confirms messages render.
- [ ] **Harumi** scrolls to show longer history.

## UC-07 Create New Conversation (multi-client)

- [ ] **Jon** private flow: create conversation with **Quan** only.
- [ ] **Quan** confirms private conversation appears.
- [ ] **Jon** group flow: create conversation with **Quan + Harumi**.
- [ ] **Quan** and **Harumi** confirm group conversation appears.

## UC-08 Add User to Existing Group Conversation (multi-client)

- [ ] **Jon** opens a group conversation and clicks Add.
- [ ] **Jon** selects participant not in group and confirms.
- [ ] Added user confirms conversation appears.
- [ ] **Jon** alternate flow mention: adding from private thread forks to a new conversation.

## UC-09 Leave Conversation (multi-client)

- [ ] **Quan** leaves a group conversation.
- [ ] **Quan** confirms conversation disappears from Quan list.
- [ ] **Jon** and/or **Harumi** confirm conversation remains for remaining participants.

## UC-10 Logout from Client Application (single-client, projector = Harumi)

- [ ] **Harumi** clicks Logout and returns to login screen.
- [ ] **Harumi** logs back in.
- [ ] **Harumi** states alternate flows: inactivity auto-logout, network-loss re-login behavior.

## UC-11 Join/View Conversation (Admin) (single-client, projector = Harumi)

- [ ] **Harumi (admin)** clicks Admin button.
- [ ] **Harumi** searches by user ID/name.
- [ ] **Harumi** selects one conversation and opens full snapshot.
- [ ] **Harumi** states this is read-only admin view.

---

## End-to-end completion checks

- [ ] All three logged in successfully.
- [ ] Register/login corner cases shown.
- [ ] Send/receive + recency ordering shown.
- [ ] Directory and conversation search shown.
- [ ] Private + group creation shown.
- [ ] Add participant + leave shown.
- [ ] Admin read-only view shown.
- [ ] Logout/re-login shown.

## Server shutdown

- [ ] **Jon** asks all users to logout or close clients.
- [ ] **Jon** stops server in Terminal 1 with `Ctrl+C`.
- [ ] Confirm clean server exit.

