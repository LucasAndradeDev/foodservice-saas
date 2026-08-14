package com.example.warehouse.controller;

import com.example.warehouse.service.WarehouseSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WarehouseSyncControllerIntegrationTest {

    // Matches sync.trigger-token in src/test/resources/application.yml.
    private static final String VALID_TOKEN = "test-sync-trigger-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WarehouseSyncService warehouseSyncService;

    @Test
    void triggerSync_withoutToken_returns401AndDoesNotSync() throws Exception {
        mockMvc.perform(post("/api/v1/internal/sync"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(warehouseSyncService);
    }

    @Test
    void triggerSync_withWrongToken_returns401AndDoesNotSync() throws Exception {
        mockMvc.perform(post("/api/v1/internal/sync")
                        .header("X-Sync-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(warehouseSyncService);
    }

    @Test
    void triggerSync_withValidToken_runsSync() throws Exception {
        mockMvc.perform(post("/api/v1/internal/sync")
                        .header("X-Sync-Token", VALID_TOKEN))
                .andExpect(status().isOk());

        verify(warehouseSyncService).syncAll();
    }
}
