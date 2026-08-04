package com.example.restaurant_saas.controller;

import com.example.restaurant_saas.dto.response.BackupResponse;
import com.example.restaurant_saas.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackupControllerIntegrationTest {

    // Matches backup.trigger-token in src/test/resources/application.yml.
    private static final String VALID_TOKEN = "test-backup-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    @Test
    void run_withoutToken_shouldReturn401AndNotRunBackup() throws Exception {
        mockMvc.perform(post("/api/v1/internal/backups"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(backupService);
    }

    @Test
    void run_withWrongToken_shouldReturn401AndNotRunBackup() throws Exception {
        mockMvc.perform(post("/api/v1/internal/backups")
                        .header("X-Backup-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(backupService);
    }

    @Test
    void run_withValidToken_shouldRunBackupAndReturnResult() throws Exception {
        BackupResponse response = BackupResponse.builder()
                .filename("backup-20260804T060000Z.dump")
                .sizeBytes(1024L)
                .durationMs(500L)
                .deletedCount(1)
                .build();
        when(backupService.runBackup()).thenReturn(response);

        mockMvc.perform(post("/api/v1/internal/backups")
                        .header("X-Backup-Token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("backup-20260804T060000Z.dump"))
                .andExpect(jsonPath("$.sizeBytes").value(1024))
                .andExpect(jsonPath("$.deletedCount").value(1));

        verify(backupService).runBackup();
    }
}
