package com.example.restaurant_saas.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

abstract class AbstractFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Override
    public String storeProductImage(MultipartFile file) {
        return store(file, "products");
    }

    @Override
    public String storeRestaurantLogo(MultipartFile file) {
        return store(file, "logos");
    }

    @Override
    public String storeCategoryBanner(MultipartFile file) {
        return store(file, "category-banners");
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

        return save(file, subdir, filename, contentType);
    }

    protected abstract String save(MultipartFile file, String subdir, String filename, String contentType);
}
