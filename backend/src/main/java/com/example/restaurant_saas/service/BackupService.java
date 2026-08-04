package com.example.restaurant_saas.service;

import com.example.restaurant_saas.dto.response.BackupResponse;
import com.example.restaurant_saas.exception.BackupProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps the whole (single, shared) Postgres database via {@code pg_dump} and uploads it to a
 * private Supabase Storage bucket, pruning old backups past the configured retention count.
 * Talks to Supabase Storage the same way {@link SupabaseFileStorageService} does: a plain
 * authenticated REST call with the service-role key, no SDK.
 */
@Service
public class BackupService {

    private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("^jdbc:postgresql://([^/]+)/(.+)$");

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String bucket;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final int retentionCount;

    public BackupService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            @Value("${supabase.backup-bucket}") String bucket,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${backup.retention-count}") int retentionCount
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .build();
        this.bucket = bucket;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.retentionCount = retentionCount;
    }

    public BackupResponse runBackup() {
        long start = System.currentTimeMillis();
        String filename = "backup-" + FILENAME_TIMESTAMP.format(Instant.now().atZone(ZoneOffset.UTC)) + ".dump";

        byte[] dump = dumpDatabase();
        uploadBackup(filename, dump);
        int deletedCount = pruneOldBackups();

        return BackupResponse.builder()
                .filename(filename)
                .sizeBytes(dump.length)
                .durationMs(System.currentTimeMillis() - start)
                .deletedCount(deletedCount)
                .build();
    }

    static String buildConnectionUri(String datasourceUrl, String username) {
        Matcher matcher = JDBC_URL_PATTERN.matcher(datasourceUrl);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported JDBC URL format: " + datasourceUrl);
        }
        String hostAndPort = matcher.group(1);
        String dbAndQuery = matcher.group(2);
        String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return "postgresql://" + encodedUser + "@" + hostAndPort + "/" + dbAndQuery;
    }

    private byte[] dumpDatabase() {
        String connectionUri = buildConnectionUri(datasourceUrl, datasourceUsername);
        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "--format=custom",
                "--no-password",
                "--dbname=" + connectionUri
        );
        pb.environment().put("PGPASSWORD", datasourcePassword);

        try {
            Process process = pb.start();
            byte[] dump;
            byte[] stderr;
            try (var stdout = process.getInputStream(); var stderrStream = process.getErrorStream()) {
                dump = stdout.readAllBytes();
                stderr = stderrStream.readAllBytes();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BackupProcessingException("pg_dump exited with code " + exitCode + ": " + new String(stderr, StandardCharsets.UTF_8));
            }
            return dump;
        } catch (IOException e) {
            throw new BackupProcessingException("Failed to run pg_dump. Is the postgresql client installed?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupProcessingException("pg_dump was interrupted.", e);
        }
    }

    private void uploadBackup(String filename, byte[] dump) {
        try {
            restClient.post()
                    .uri("/object/{bucket}/{filename}", bucket, filename)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(dump)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new BackupProcessingException("Failed to upload backup to Supabase Storage.", e);
        }
    }

    private int pruneOldBackups() {
        List<String> names = listBackupFilenames();
        if (names.size() <= retentionCount) {
            return 0;
        }

        List<String> toDelete = names.stream()
                .sorted(Comparator.reverseOrder())
                .skip(retentionCount)
                .toList();

        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("/object/{bucket}", bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", toDelete))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new BackupProcessingException("Failed to prune old backups from Supabase Storage.", e);
        }

        return toDelete.size();
    }

    private List<String> listBackupFilenames() {
        try {
            String body = restClient.post()
                    .uri("/object/list/{bucket}", bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefix", "", "limit", 1000))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            List<String> names = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    JsonNode nameNode = node.get("name");
                    if (nameNode != null && nameNode.asText().endsWith(".dump")) {
                        names.add(nameNode.asText());
                    }
                }
            }
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse Supabase Storage list response.", e);
        } catch (Exception e) {
            throw new BackupProcessingException("Failed to list existing backups from Supabase Storage.", e);
        }
    }
}
