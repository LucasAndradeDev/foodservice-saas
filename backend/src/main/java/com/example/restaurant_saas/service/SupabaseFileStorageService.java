package com.example.restaurant_saas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Uploads go straight to Supabase Storage's REST API (no SDK dependency needed —
 * it's a plain authenticated POST). Render's free web service has no persistent
 * disk, so anything written to local disk is lost on the next deploy/restart;
 * this is the provider used in production instead of {@link LocalFileStorageService}.
 */
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "supabase")
public class SupabaseFileStorageService extends AbstractFileStorageService {

    private final RestClient restClient;
    private final String publicBaseUrl;
    private final String bucket;

    public SupabaseFileStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            @Value("${supabase.storage.bucket}") String bucket
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .build();
        this.publicBaseUrl = supabaseUrl + "/storage/v1/object/public";
        this.bucket = bucket;
    }

    @Override
    protected String save(MultipartFile file, String subdir, String filename, String contentType) {
        String objectPath = subdir + "/" + filename;
        try {
            restClient.post()
                    .uri("/object/{bucket}/{path}", bucket, objectPath)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file.", e);
        }

        return publicBaseUrl + "/" + bucket + "/" + objectPath;
    }
}
