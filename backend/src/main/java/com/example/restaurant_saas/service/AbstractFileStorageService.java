package com.example.restaurant_saas.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

abstract class AbstractFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    // Client-supplied Content-Type is just a header the caller chose to send - it proves nothing
    // about what's actually in the file. Sniffing the real magic bytes stops someone uploading
    // arbitrary content (HTML, a script, malware) declared as "image/jpeg" and having it stored
    // under this app's own domain behind a UUID filename.
    private static final int MAGIC_BYTES_TO_READ = 12;

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
        if (!contentType.equals(sniffImageContentType(file))) {
            throw new IllegalArgumentException("File content does not match a JPEG, PNG or WEBP image.");
        }

        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + extension;

        return save(file, subdir, filename, contentType);
    }

    /**
     * Detects the real format from the file's leading bytes, independent of whatever Content-Type
     * the client declared. Returns null if the bytes don't match any of the three allowed formats.
     */
    private String sniffImageContentType(MultipartFile file) {
        byte[] header = new byte[MAGIC_BYTES_TO_READ];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read uploaded file.", ex);
        }

        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return "image/png";
        }
        if (read >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    protected abstract String save(MultipartFile file, String subdir, String filename, String contentType);
}
