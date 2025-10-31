package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试ChatMessage.getContentAsString()在各种情况下不返回null
 */
public class ChatMessageContentTest {

    @Test
    public void testGetContentAsString_withTextString() {
        ChatMessage msg = ChatMessage.user("Hello world");
        assertNotNull(msg.getContentAsString(), "getContentAsString() should never return null");
        assertEquals("Hello world", msg.getContentAsString());
    }

    @Test
    public void testGetContentAsString_withTextWithCache() {
        String context = "This is a long context for caching";
        ChatMessage msg = ChatMessage.user(ChatMessage.ContentPart.textWithCache(context, true));

        assertNotNull(msg.getContentAsString(), "getContentAsString() should never return null");
        assertEquals(context, msg.getContentAsString());

        // 验证isEmpty()调用不会抛出NullPointerException
        assertFalse(msg.getContentAsString().isEmpty(), "Content should not be empty");
    }

    @Test
    public void testGetContentAsString_withMultipleParts() {
        String part1 = "First part";
        String part2 = "Second part";
        ChatMessage msg = ChatMessage.user(
            ChatMessage.ContentPart.textWithCache(part1, true),
            ChatMessage.ContentPart.text(part2)
        );

        assertNotNull(msg.getContentAsString(), "getContentAsString() should never return null");
        assertEquals(part1 + part2, msg.getContentAsString());
        assertFalse(msg.getContentAsString().isEmpty(), "Content should not be empty");
    }

    @Test
    public void testGetContentAsString_withNullContent() {
        ChatMessage msg = new ChatMessage();
        msg.setRole("user");
        msg.setContent(null);

        // 即使content为null，也应该返回空字符串而不是null
        assertNotNull(msg.getContentAsString(), "getContentAsString() should return empty string for null content");
        assertEquals("", msg.getContentAsString());
        assertTrue(msg.getContentAsString().isEmpty(), "Content should be empty");
    }

    @Test
    public void testGetContentAsString_withEmptyArray() {
        ChatMessage msg = new ChatMessage();
        msg.setRole("user");
        msg.setContent(new ChatMessage.ContentPart[0]);

        assertNotNull(msg.getContentAsString(), "getContentAsString() should return empty string for empty array");
        assertEquals("", msg.getContentAsString());
        assertTrue(msg.getContentAsString().isEmpty(), "Content should be empty");
    }

    @Test
    public void testGetContentAsString_withNullPartInArray() {
        ChatMessage msg = new ChatMessage();
        msg.setRole("user");
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[2];
        parts[0] = ChatMessage.ContentPart.text("Valid part");
        parts[1] = null; // null part
        msg.setContent(parts);

        assertNotNull(msg.getContentAsString(), "getContentAsString() should handle null parts");
        assertEquals("Valid part", msg.getContentAsString());
    }

    @Test
    public void testGetContentAsString_neverThrowsNullPointerException() {
        // 测试所有可能导致NPE的情况
        ChatMessage[] testMessages = new ChatMessage[] {
            new ChatMessage("user", null),
            new ChatMessage("user", ""),
            new ChatMessage("user", "text"),
            ChatMessage.user(ChatMessage.ContentPart.textWithCache("text", true)),
            new ChatMessage()
        };

        for (ChatMessage msg : testMessages) {
            try {
                String content = msg.getContentAsString();
                assertNotNull(content, "getContentAsString() should never return null");
                // 这个调用不应该抛出NPE
                content.isEmpty();
            } catch (NullPointerException e) {
                fail("getContentAsString() threw NullPointerException for message: " + msg.getRole());
            }
        }
    }
}
