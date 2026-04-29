# Issue Resolution Tracker

Track issues we investigate/fix during this session so we can review what should be cleaned up or excluded before opening a PR.

## Active Issues

- [ ] (none)

## Resolved Issues

- [x] #195 - [Conversation List] message plates in Conversation view squash the conversation list.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/195
  - Resolution: Added/verified minimum width guard behavior so the conversation list remains legible and no longer gets squashed by conversation view message plates.
- [x] #196 - [Conversation View] long message wrapping makes another message plate.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/196
  - Resolution: Reworked message-plate renderer to `JTextArea`-based wrapping for header/body, fixed resize relayout behavior, and removed own-name from self-message headers (timestamp only).
- [x] #199 - [Conversation List] Recency order resets after relogin when updates occurred offline.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/199
  - Resolution: Conversation list ordering now sorts by latest message sequence number (descending) during initial paint and refreshes.
- [x] #200 - [Conversation View] Sender/timestamp badge truncates on long header text.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/200
  - Resolution: Message header rendering switched to wrapping text area behavior so long sender/timestamp lines no longer truncate.
- [x] #190 - [Conversation View] Unread markers not appearing.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/190
  - Resolution: Unread marker rendering now uses pre-open read-state snapshot and corrected selection ordering so markers appear before read acknowledgement updates.
- [x] #192 - [Create Conversation Window] first selection does not work properly.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/192
  - Resolution: Create dialog now pre-seeds the currently selected directory user, so first selection is honored without requiring reselection.
- [x] #189 - [Conversation View] text input not enabled upon auto-switch.
  - Link: https://github.com/YoonJon/CS401-Communications/issues/189
  - Resolution: Auto-switch flows now route through `ConversationView.setListModel(...)`, clear lingering directory selection, and explicitly focus the conversation message input.
- [x] [Conversation List] Newly created empty conversations not bumped by recency ordering.
  - Link: (new issue draft)
  - Resolution: Recency sorting now uses latest message sequence number as primary and conversationId fallback for empty conversations, so new empty threads still bump correctly.


## PR Cleanup Checklist

- [ ] Remove any temporary debug-only commands/scripts introduced during troubleshooting.
- [ ] Confirm only intentional source changes are included in PR diff.
- [ ] Re-run app manually (server + clients) in local user terminal before PR.
