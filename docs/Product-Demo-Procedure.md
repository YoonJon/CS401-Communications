# Product Demo Checklist (Jon, Quan, Harumi)

Use this as a literal step-by-step script during the live demo.

**Headnote (pre-demo state assumed complete):**
- Build is already compiled.
- Any desired reseed has already been run.
- Server jar with IP launcher is available and ready.
- Jon/Quan/Harumi demo credentials are already prepared.

## Roles and projector policy

- [ ] **Jon = admin + presenter** (projector shows Jon terminal whenever a single client is enough).
- [ ] **Quan = secondary client** (used when multi-client behavior must be shown).
- [ ] **Harumi = secondary client** (used when multi-client behavior must be shown).
- [ ] **Server terminal** is shown only for startup/shutdown.

## Terminal mapping

- [ ] Open 4 terminals at repo root.
- [ ] Terminal 1 = **Server**
- [ ] Terminal 2 = **Jon**
- [ ] Terminal 3 = **Quan**
- [ ] Terminal 4 = **Harumi**

## Server startup

- [ ] **Jon** (Terminal 1): run the provided server `.jar` that includes the IP finder utility.
- [ ] When the jar prompts for runmode, enter `2` (withHeroes) for the product demo (premade Jon/Quan/Harumi accounts).
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

- [ ] Confirm Jon account has Admin button (if not, swap roles so the admin user becomes the single-client presenter).

---

## UC-01 Account Creation (single-client, projector = Jon)

- [ ] **Jon** opens register screen.
- [ ] **Jon** enters valid employee ID + matching name + unique login + password.
- [ ] **Jon** submits and confirms success.
- [ ] **Jon** corner case: invalid name/employee ID -> rejection.
- [ ] **Jon** corner case: duplicate login -> rejection.

## UC-02 System Login (all clients)

- [ ] **Jon** logs in on Jon client.
- [ ] **Quan** logs in on Quan client.
- [ ] **Harumi** logs in on Harumi client.
- [ ] Confirm all reach main interface.
- [ ] **Jon** corner case on projector: wrong password -> rejection.
- [ ] **Quan** corner case: attempt duplicate session login for Jon account -> rejection.

## UC-03 Send Message (multi-client)

- [ ] **Jon -> Quan**: Jon opens (or creates) a DM with Quan and sends: `Hi Quan, this is Jon (UC-03).`
- [ ] **Quan** confirms Jon's message appears in that Jon/Quan conversation.
- [ ] **Quan -> Jon**: Quan replies: `Received, thanks Jon.`
- [ ] **Jon** confirms Quan's reply appears in the same conversation.
- [ ] **Jon** confirms Jon/Quan conversation moves up by recency in conversation list.
- [ ] **Jon** corner case: empty message send attempt -> not sent.

## UC-04 View User Directory (single-client, projector = Jon)

- [ ] **Jon** types partial name in directory search (example `Har`).
- [ ] **Jon** confirms matches appear.
- [ ] **Jon** clears search and confirms full list returns.
- [ ] **Jon** corner case: nonsense query -> no results.

## UC-05 View Conversation History (single-client, projector = Jon)

- [ ] **Jon** searches conversation list by participant name/ID.
- [ ] **Jon** confirms filtered results appear.
- [ ] **Jon** opens one result.
- [ ] **Jon** corner case: no-match query -> empty results.

## UC-06 View Conversation (single-client, projector = Jon)

- [ ] **Jon** selects a conversation.
- [ ] **Jon** confirms messages render.
- [ ] **Jon** scrolls to show longer history.

## UC-07 Create New Conversation (multi-client)

- [ ] **Jon** private flow: create a new private conversation with **Quan** only.
- [ ] **Jon** sends first DM to Quan: `Starting private thread: Jon + Quan.`
- [ ] **Quan** confirms the Jon/Quan private conversation appears and contains Jon's message.
- [ ] **Jon** group flow: create a new group conversation with **Quan + Harumi**.
- [ ] **Jon** sends first group message: `Starting group thread: Jon + Quan + Harumi.`
- [ ] **Quan** and **Harumi** confirm the new group conversation appears and shows Jon's message.

## UC-08 Add User to Existing Group Conversation (multi-client)

- [ ] Use a group conversation that currently has **Jon + Quan** only.
- [ ] **Jon** opens that group conversation and clicks Add.
- [ ] **Jon** adds **Harumi** and confirms.
- [ ] **Harumi** confirms the conversation appears on Harumi's client.
- [ ] **Harumi** sends: `Harumi joined this group.`
- [ ] **Jon** and **Quan** confirm Harumi's message appears in that same conversation.
- [ ] **Jon** alternate flow mention: adding from private thread forks to a new conversation.

## Fork-from-Private Demonstration (explicit alternate flow)

- [ ] Start from an existing **private Jon <-> Quan** conversation (exactly 2 participants).
- [ ] **Jon** opens that private conversation and clicks Add.
- [ ] **Jon** adds **Harumi** and confirms.
- [ ] **Expected behavior**: system creates a **new forked conversation** for **Jon + Quan + Harumi** (does not mutate original private thread in place).
- [ ] **Jon** confirms he now sees both:
  - original private Jon/Quan thread
  - new 3-person forked thread
- [ ] **Quan** confirms same: original private thread still present, plus new forked thread.
- [ ] **Harumi** confirms only the new forked thread appears (not the historical private Jon/Quan thread).
- [ ] In the new forked thread, **Jon** sends: `Fork demo: this is the new 3-person conversation.`
- [ ] **Quan** and **Harumi** confirm message appears in the forked thread.
- [ ] **Jon** re-opens original Jon/Quan private thread and confirms it remains unchanged (no Harumi participant, no migrated history).

## UC-09 Leave Conversation (multi-client)

- [ ] In the **Jon + Quan + Harumi** group conversation, **Quan** clicks Leave.
- [ ] **Quan** confirms conversation disappears from Quan list.
- [ ] **Jon** confirms the conversation still exists for Jon.
- [ ] **Harumi** confirms the conversation still exists for Harumi.
- [ ] **Jon -> Harumi**: Jon sends `Quan has left; confirming remaining participants.`
- [ ] **Harumi** confirms Jon's post-leave message appears.

## UC-10 Logout from Client Application (single-client, projector = Jon)

- [ ] **Jon** clicks Logout and returns to login screen.
- [ ] **Jon** logs back in.
- [ ] **Jon** states alternate flows: inactivity auto-logout, network-loss re-login behavior.

## UC-11 Join/View Conversation (Admin) (single-client, projector = Jon)

- [ ] **Jon (admin)** clicks Admin button.
- [ ] **Jon** searches by user ID/name.
- [ ] **Jon** selects one conversation and opens full snapshot.
- [ ] **Jon** states this is read-only admin view.

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

