package com.example.restaurant_saas.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the connection-pool-recycling assumption behind {@link TenantAwareDataSource} before
 * anything else in the app relies on it: with the pool pinned to a single physical connection,
 * two sequential borrows under different {@link TenantContext} values must each see their own
 * tenant in {@code current_setting('app.tenant_id', true)} - i.e. no leftover value leaks from
 * one tenant's request into the next request that happens to reuse the same connection.
 */
class TenantAwareDataSourceTest {

    private HikariDataSource pooled;
    private TenantAwareDataSource tenantAware;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (pooled != null) {
            pooled.close();
        }
    }

    @Test
    void reusedConnectionReflectsTheCurrentTenantOnEachBorrow() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/restaurant_saas");
        config.setUsername("app_runtime");
        config.setPassword("app_runtime_local_dev_only");
        config.setMaximumPoolSize(1);
        pooled = new HikariDataSource(config);
        tenantAware = new TenantAwareDataSource(pooled);

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        TenantContext.setCurrentTenant(tenantA);
        assertThat(currentTenantSetting()).isEqualTo(tenantA.toString());

        TenantContext.setCurrentTenant(tenantB);
        assertThat(currentTenantSetting()).isEqualTo(tenantB.toString());

        TenantContext.clear();
        // TenantAwareDataSource writes '' (not SQL NULL) when TenantContext is empty - the RLS
        // policies rely on current_setting returning NULL only when the GUC was never set at all,
        // which can't happen here since every borrow runs set_config unconditionally.
        assertThat(currentTenantSetting()).isEmpty();
    }

    private String currentTenantSetting() throws Exception {
        try (Connection connection = tenantAware.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT current_setting('app.tenant_id', true)")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
