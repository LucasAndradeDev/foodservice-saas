package com.example.restaurant_saas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${app.upload-dir}")
    private String uploadDir;

    public String storeProductImage(MultipartFile file) {
        return store(file, "products");
    }

    public String storeRestaurantLogo(MultipartFile file) {
        return store(file, "logos");
    }

    private String store(MultipartFile file, String subdir) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG or WEBP images are allowed.");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty.");
        }

        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + extension;

        try {
            Path targetDir = Path.of(uploadDir, subdir);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(filename);
            file.transferTo(targetFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file.", e);
        }

        return "/api/v1/public/uploads/" + subdir + "/" + filename;
    }
}
