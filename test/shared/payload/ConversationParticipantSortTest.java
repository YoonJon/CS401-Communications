package shared.payload;

import org.junit.jupiter.api.Test;
import shared.enums.ConversationType;
import shared.enums.UserType;
import shared.networking.User;
import shared.networking.User.UserInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #214 — verifies that {@link Conversation} keeps participants and historical participants
 * ordered case-insensitively by name, with userId as tie-breaker, regardless of insertion order
 * (constructor, {@link Conversation#addParticipants}, deserialization of pre-existing files).
 */
class ConversationParticipantSortTest {

    private static UserInfo info(String userId, String name) {
        return new User(userId, name, userId + "_login", "pw", UserType.USER).toUserInfo();
    }

    private static ArrayList<UserInfo> roster(UserInfo... members) {
        ArrayList<UserInfo> list = new ArrayList<>();
        for (UserInfo u : members) list.add(u);
        return list;
    }

    private static List<String> namesOf(List<UserInfo> participants) {
        ArrayList<String> names = new ArrayList<>();
        for (UserInfo u : participants) names.add(u.getName());
        return names;
    }

    @Test
    void constructor_reverseAlphabeticInput_sortedOutput() {
        Conversation c = new Conversation(1L, roster(
                info("u3", "Zoe"),
                info("u1", "Alice"),
                info("u2", "Bob")));
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(c.getParticipants()));
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(c.getHistoricalParticipants()));
    }

    @Test
    void constructor_mixedCase_sortsCaseInsensitively() {
        Conversation c = new Conversation(2L, roster(
                info("u2", "bob"),
                info("u1", "ALICE"),
                info("u3", "Carol")));
        assertEquals(List.of("ALICE", "bob", "Carol"), namesOf(c.getParticipants()));
    }

    @Test
    void constructor_equalNames_tieBreakOnUserId() {
        Conversation c = new Conversation(3L, roster(
                info("u_b", "Sam"),
                info("u_a", "Sam")));
        ArrayList<UserInfo> result = c.getParticipants();
        assertEquals("u_a", result.get(0).getUserId());
        assertEquals("u_b", result.get(1).getUserId());
    }

    @Test
    void addParticipants_interleavesAlphabeticallyAndHistoryMatches() {
        Conversation c = new Conversation(4L, roster(
                info("u1", "Alice"),
                info("u3", "Carol")));
        ArrayList<UserInfo> toAdd = roster(info("u2", "Bob"));
        c.addParticipants(toAdd);

        assertEquals(List.of("Alice", "Bob", "Carol"), namesOf(c.getParticipants()));
        assertEquals(List.of("Alice", "Bob", "Carol"), namesOf(c.getHistoricalParticipants()));
    }

    @Test
    void addParticipants_skipsExistingUserIdAndKeepsSorted() {
        Conversation c = new Conversation(5L, roster(
                info("u1", "Alice"),
                info("u2", "Bob")));
        c.addParticipants(roster(info("u2", "Bob"), info("u3", "Carol")));
        assertEquals(List.of("Alice", "Bob", "Carol"), namesOf(c.getParticipants()));
        // Original Alice+Bob historical plus newly added Carol; duplicate u2 not re-added.
        assertEquals(List.of("Alice", "Bob", "Carol"), namesOf(c.getHistoricalParticipants()));
    }

    @Test
    void toMetadata_lists_areSorted() {
        Conversation c = new Conversation(6L, roster(
                info("u3", "Zoe"),
                info("u1", "Alice"),
                info("u2", "Bob")));
        ConversationMetadata meta = c.toMetadata();
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(meta.getParticipants()));
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(meta.getHistoricalParticipants()));
        assertEquals(ConversationType.GROUP, meta.getType());
    }

    @Test
    void deserialize_legacyUnsortedConversation_resortsOnRead() throws Exception {
        // Build a legacy-state Conversation: bypass the constructor (which sorts) by mutating
        // the internal lists via reflection so the on-disk shape simulates pre-fix data.
        Conversation legacy = new Conversation(7L, roster(info("u1", "Alice"), info("u2", "Bob")));
        Field participantsField = Conversation.class.getDeclaredField("participants");
        participantsField.setAccessible(true);
        Field historicalField = Conversation.class.getDeclaredField("historicalParticipants");
        historicalField.setAccessible(true);

        @SuppressWarnings("unchecked")
        ArrayList<UserInfo> liveParts = (ArrayList<UserInfo>) participantsField.get(legacy);
        liveParts.clear();
        liveParts.add(info("u3", "Zoe"));
        liveParts.add(info("u1", "Alice"));
        liveParts.add(info("u2", "Bob"));

        @SuppressWarnings("unchecked")
        ArrayList<UserInfo> liveHist = (ArrayList<UserInfo>) historicalField.get(legacy);
        liveHist.clear();
        liveHist.add(info("u3", "Zoe"));
        liveHist.add(info("u1", "Alice"));
        liveHist.add(info("u2", "Bob"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(legacy);
        }
        Conversation rehydrated;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            rehydrated = (Conversation) in.readObject();
        }

        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(rehydrated.getParticipants()));
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(rehydrated.getHistoricalParticipants()));
    }

    @Test
    void getParticipants_returnsDefensiveCopy_externalSortDoesNotAffectModel() {
        Conversation c = new Conversation(8L, roster(
                info("u1", "Alice"),
                info("u2", "Bob"),
                info("u3", "Carol")));
        ArrayList<UserInfo> snapshot = c.getParticipants();
        snapshot.sort(Comparator.comparing(UserInfo::getName).reversed());
        // model state intact
        assertEquals(List.of("Alice", "Bob", "Carol"), namesOf(c.getParticipants()));
    }
}
