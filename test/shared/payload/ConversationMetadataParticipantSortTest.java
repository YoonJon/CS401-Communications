package shared.payload;

import org.junit.jupiter.api.Test;
import shared.enums.ConversationType;
import shared.enums.UserType;
import shared.networking.User;
import shared.networking.User.UserInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #214 — verifies {@link ConversationMetadata} sorts both participant lists
 * (active and historical) case-insensitively by name with userId tie-break, in both
 * the base and full constructors.
 */
class ConversationMetadataParticipantSortTest {

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
    void baseConstructor_reverseAlphabeticInput_sortsBothLists() {
        ConversationMetadata meta = new ConversationMetadata(
                10L,
                roster(info("u3", "Zoe"), info("u1", "Alice"), info("u2", "Bob")),
                roster(info("u3", "Zoe"), info("u1", "Alice"), info("u2", "Bob")),
                ConversationType.GROUP);

        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(meta.getParticipants()));
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(meta.getHistoricalParticipants()));
    }

    @Test
    void fullConstructor_sortsBothLists() {
        ConversationMetadata meta = new ConversationMetadata(
                11L,
                roster(info("u3", "Zoe"), info("u1", "Alice")),
                roster(info("u3", "Zoe"), info("u1", "Alice"), info("u2", "Bob")),
                ConversationType.GROUP,
                "preview", 1234L, 5, "Group");

        assertEquals(List.of("Alice", "Zoe"), namesOf(meta.getParticipants()));
        assertEquals(List.of("Alice", "Bob", "Zoe"), namesOf(meta.getHistoricalParticipants()));
        assertEquals("preview", meta.getLastMessagePreview());
        assertEquals(5, meta.getUnreadCount());
        assertEquals("Group", meta.getDisplayName());
    }

    @Test
    void mixedCase_sortsCaseInsensitively() {
        ConversationMetadata meta = new ConversationMetadata(
                12L,
                roster(info("u2", "bob"), info("u1", "ALICE"), info("u3", "Carol")),
                roster(info("u2", "bob"), info("u1", "ALICE"), info("u3", "Carol")),
                ConversationType.GROUP);
        assertEquals(List.of("ALICE", "bob", "Carol"), namesOf(meta.getParticipants()));
    }

    @Test
    void equalNames_tieBreakOnUserId() {
        ConversationMetadata meta = new ConversationMetadata(
                13L,
                roster(info("u_b", "Sam"), info("u_a", "Sam")),
                roster(info("u_b", "Sam"), info("u_a", "Sam")),
                ConversationType.PRIVATE);
        ArrayList<UserInfo> result = meta.getParticipants();
        assertEquals("u_a", result.get(0).getUserId());
        assertEquals("u_b", result.get(1).getUserId());
    }

    @Test
    void nullParticipants_normalizedToEmpty() {
        ConversationMetadata meta = new ConversationMetadata(
                14L, null, null, ConversationType.GROUP);
        assertNotNull(meta.getParticipants());
        assertEquals(0, meta.getParticipants().size());
        assertNotNull(meta.getHistoricalParticipants());
        assertEquals(0, meta.getHistoricalParticipants().size());
    }
}
