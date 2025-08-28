package io.github.twwch.openai.sdk;

import io.github.twwch.openai.sdk.util.ImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 图片压缩演示
 */
public class ImageCompressionDemo {
    
    public static void main(String[] args) {
        try {
            // 读取demo.jpg
            File inputFile = new File("demo.jpg");
            if (!inputFile.exists()) {
                System.err.println("demo.jpg not found!");
                return;
            }
            
            // 读取图片字节
            byte[] originalBytes = Files.readAllBytes(inputFile.toPath());
            System.out.println("Original file size: " + originalBytes.length + " bytes (" + 
                              (originalBytes.length / 1024 / 1024.0) + " MB)");
            
            // 压缩到5MB以内
            int maxSize = 5 * 1024 * 1024; // 5MB
            byte[] compressedBytes = ImageUtils.compressImage(originalBytes, maxSize);
            
            System.out.println("Compressed file size: " + compressedBytes.length + " bytes (" + 
                              (compressedBytes.length / 1024 / 1024.0) + " MB)");
            
            // 保存压缩后的图片
            File outputFile = new File("demo2.jpg");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(compressedBytes);
            }
            
            System.out.println("Compressed image saved to: " + outputFile.getAbsolutePath());
            System.out.println("Compression ratio: " + 
                              String.format("%.2f%%", (1 - (double)compressedBytes.length / originalBytes.length) * 100));
            
        } catch (IOException e) {
            System.err.println("Error processing image: " + e.getMessage());
            e.printStackTrace();
        }
    }
}