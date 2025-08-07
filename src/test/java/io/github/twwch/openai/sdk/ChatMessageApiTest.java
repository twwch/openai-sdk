package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试ChatMessage新API
 */
public class ChatMessageApiTest {
    
    @Test
    public void testUserWithSingleImage() {
        ChatMessage message = ChatMessage.userWithImage(
            "What's in this image?",
            "https://example.com/image.jpg"
        );
        
        assertEquals("user", message.getRole());
        assertNotNull(message.getContent());
        assertTrue(message.getContent() instanceof ChatMessage.ContentPart[]);
        
        ChatMessage.ContentPart[] parts = (ChatMessage.ContentPart[]) message.getContent();
        assertEquals(2, parts.length);
        assertEquals("text", parts[0].getType());
        assertEquals("What's in this image?", parts[0].getText());
        assertEquals("image_url", parts[1].getType());
        assertEquals("https://example.com/image.jpg", parts[1].getImageUrl().getUrl());
    }
    
    @Test
    public void testUserWithMultipleImages() {
        ChatMessage message = ChatMessage.userWithImages(
            "Compare these images",
            "https://example.com/image1.jpg",
            "https://example.com/image2.jpg",
            "data:image/png;base64,abc123"
        );
        
        assertEquals("user", message.getRole());
        ChatMessage.ContentPart[] parts = (ChatMessage.ContentPart[]) message.getContent();
        assertEquals(4, parts.length); // 1 text + 3 images
        
        assertEquals("text", parts[0].getType());
        assertEquals("Compare these images", parts[0].getText());
        
        for (int i = 1; i <= 3; i++) {
            assertEquals("image_url", parts[i].getType());
            assertNotNull(parts[i].getImageUrl());
        }
    }
    
    @Test
    public void testUserWithContentParts() {
        // 测试使用ContentPart可变参数
        ChatMessage message = ChatMessage.user(
            ChatMessage.ContentPart.text("First text"),
            ChatMessage.ContentPart.imageUrl("https://example.com/img.jpg"),
            ChatMessage.ContentPart.text("Second text")
        );
        
        assertEquals("user", message.getRole());
        ChatMessage.ContentPart[] parts = (ChatMessage.ContentPart[]) message.getContent();
        assertEquals(3, parts.length);
        
        assertEquals("text", parts[0].getType());
        assertEquals("First text", parts[0].getText());
        assertEquals("image_url", parts[1].getType());
        assertEquals("text", parts[2].getType());
        assertEquals("Second text", parts[2].getText());
    }
    
    @Test
    public void testAssistantWithContentParts() {
        // 测试助手消息也支持ContentPart
        ChatMessage message = ChatMessage.assistant(
            ChatMessage.ContentPart.text("Here's my analysis")
        );
        
        assertEquals("assistant", message.getRole());
        ChatMessage.ContentPart[] parts = (ChatMessage.ContentPart[]) message.getContent();
        assertEquals(1, parts.length);
        assertEquals("text", parts[0].getType());
    }
    
    @Test
    public void testBackwardCompatibility() {
        // 确保向后兼容性 - 旧的字符串方式仍然工作
        ChatMessage userMsg = ChatMessage.user("Hello");
        assertEquals("user", userMsg.getRole());
        assertEquals("Hello", userMsg.getContent());
        assertEquals("Hello", userMsg.getContentAsString());
        
        ChatMessage assistantMsg = ChatMessage.assistant("Hi there");
        assertEquals("assistant", assistantMsg.getRole());
        assertEquals("Hi there", assistantMsg.getContent());
        assertEquals("Hi there", assistantMsg.getContentAsString());
        
        ChatMessage systemMsg = ChatMessage.system("You are helpful");
        assertEquals("system", systemMsg.getRole());
        assertEquals("You are helpful", systemMsg.getContent());
        assertEquals("You are helpful", systemMsg.getContentAsString());
    }
    
    @Test
    public void testGetContentAsString() {
        // 测试getContentAsString方法
        
        // 字符串内容
        ChatMessage msg1 = ChatMessage.user("Simple text");
        assertEquals("Simple text", msg1.getContentAsString());
        
        // ContentPart数组
        ChatMessage msg2 = ChatMessage.user(
            ChatMessage.ContentPart.text("Text 1"),
            ChatMessage.ContentPart.imageUrl("https://example.com/img.jpg"),
            ChatMessage.ContentPart.text(" Text 2")
        );
        assertEquals("Text 1 Text 2", msg2.getContentAsString());
        
        // 只有图片的ContentPart数组
        ChatMessage msg3 = ChatMessage.user(
            ChatMessage.ContentPart.imageUrl("https://example.com/img.jpg")
        );
        assertEquals("", msg3.getContentAsString());
    }
}