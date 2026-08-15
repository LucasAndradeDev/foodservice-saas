package com.example.restaurant_saas.service;

import com.example.restaurant_saas.exception.MenuImportProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates and reads uploaded PDF/image files for the menu-import-from-document flow.
 * Unlike {@link SpreadsheetExtractionService}, it does no parsing - the raw bytes are sent
 * to Gemini as-is, since the model reads document/image layout natively.
 */
@Service
public class DocumentExtractionService {

    private static final int MAX_FILES = 8;
    // Comfortably under Gemini's ~20MB inline-request limit, leaving room for base64's
    // ~33% size overhead once these bytes are encoded.
    private static final long MAX_TOTAL_BYTES = 14L * 1024 * 1024;

    public List<GeminiDocument> extractDocuments(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Envie ao menos um arquivo.");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Envie no máximo " + MAX_FILES + " arquivos por importação.");
        }

        List<GeminiDocument> documents = new ArrayList<>();
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Um dos arquivos enviados está vazio.");
            }

            totalBytes += file.getSize();
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException(
                        "O total dos arquivos enviados é muito grande. Envie menos fotos ou um PDF menor por vez.");
            }

            String mimeType = detectMimeType(file);
            try {
                documents.add(new GeminiDocument(mimeType, file.getBytes()));
            } catch (IOException e) {
                throw new MenuImportProcessingException("Não foi possível ler um dos arquivos enviados.", e);
            }
        }
        return documents;
    }

    private String detectMimeType(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (filename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (filename.endsWith(".png")) {
            return "image/png";
        }
        if (filename.endsWith(".webp")) {
            return "image/webp";
        }
        throw new IllegalArgumentException(
                "Formato não suportado (" + file.getOriginalFilename() + "). Envie PDF, JPG, PNG ou WEBP.");
    }
}
