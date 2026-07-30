package com.example.restaurant_saas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService extends AbstractFileStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    protected String save(MultipartFile file, String subdir, String filename, String contentType) {
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
