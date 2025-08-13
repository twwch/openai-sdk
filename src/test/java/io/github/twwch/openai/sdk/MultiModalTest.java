package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.model.chat.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试多模态内容支持
 */
public class MultiModalTest {
    
    @Test
    public void testStringContent() {
        ChatMessage message = ChatMessage.user("Hello, world!");
        assertEquals("Hello, world!", message.getContentAsString());
        assertTrue(message.getContent() instanceof String);
    }
    
    @Test
    public void testArrayContent() {
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("Here is an image:"),
            ChatMessage.ContentPart.imageUrl("https://example.com/image.jpg")
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        assertEquals("Here is an image:", message.getContentAsString());
        assertTrue(message.getContent() instanceof ChatMessage.ContentPart[]);
        
        ChatMessage.ContentPart[] contentParts = (ChatMessage.ContentPart[]) message.getContent();
        assertEquals(2, contentParts.length);
        assertEquals("text", contentParts[0].getType());
        assertEquals("image_url", contentParts[1].getType());
    }
    
    @Test
    public void testBase64ImageContent() {
        String base64Image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
        
        ChatMessage.ContentPart[] parts = new ChatMessage.ContentPart[] {
            ChatMessage.ContentPart.text("What's in this image?"),
            ChatMessage.ContentPart.imageUrl(base64Image)
        };
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(parts);
        
        assertEquals("What's in this image?", message.getContentAsString());
        
        ChatMessage.ContentPart[] contentParts = (ChatMessage.ContentPart[]) message.getContent();
        assertEquals(base64Image, contentParts[1].getImageUrl().getUrl());
    }
    
    @Test
    public void testMixedContentParsing() {
        // 模拟OpenAI格式的输入
        String jsonInput = "[{\"text\":\"Here are some pictures uploaded by users for your reference\",\"type\":\"text\"},{\"image_url\":{\"url\":\"https://cdn-aws.iweaver.ai/docx/2025/08/07/image.jpeg\"},\"type\":\"image_url\"}]";
        
        // 测试内容部分的创建
        ChatMessage.ContentPart textPart = new ChatMessage.ContentPart();
        textPart.setType("text");
        textPart.setText("Here are some pictures uploaded by users for your reference");
        
        ChatMessage.ContentPart imagePart = new ChatMessage.ContentPart();
        imagePart.setType("image_url");
        ChatMessage.ContentPart.ImageUrl imageUrl = new ChatMessage.ContentPart.ImageUrl();
        imageUrl.setUrl("https://cdn-aws.iweaver.ai/docx/2025/08/07/image.jpeg");
        imagePart.setImageUrl(imageUrl);
        
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent(new ChatMessage.ContentPart[] { textPart, imagePart });
        
        assertEquals("Here are some pictures uploaded by users for your reference", message.getContentAsString());
    }
}