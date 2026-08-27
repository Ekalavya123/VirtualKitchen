package com.processVisualisation.virtualKitchen.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SupabaseStorageServiceTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @Test
    void shouldUploadTestImage() throws Exception {

        // Create a simple 200x200 test image
        BufferedImage image =
                new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);

        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 200, 200);
        graphics.setColor(Color.RED);
        graphics.fillRect(50, 50, 100, 100);
        graphics.dispose();

        // Convert image to PNG bytes
        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        ImageIO.write(image, "png", outputStream);

        byte[] imageBytes = outputStream.toByteArray();

        // Unique path so repeated tests don't overwrite the image
        String path =
                "test/step-" + UUID.randomUUID() + ".png";

        // Upload
        String imageUrl = imageStorageService.upload(
                imageBytes,
                "image/png",
                path
        );

        System.out.println("Uploaded image:");
        System.out.println(imageUrl);

        assertNotNull(imageUrl);
    }
}