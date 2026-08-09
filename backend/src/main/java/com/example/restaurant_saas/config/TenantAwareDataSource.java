package com.example.restaurant_saas.config;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Stamps every connection handed out by the delegate pool with the current tenant, so Postgres
 * Row-Level Security policies (which read {@code current_setting('app.tenant_id', true)}) see the
 * right restaurant. Hooking {@link #getConnection()} rather than a Hibernate {@code Interceptor} or
 * {@code TransactionSynchronizationManager} callback means this fires once per physical connection
 * borrowed from the pool, which is the right granularity regardless of how many logical
 * {@code @Transactional} units of work run against it.
 * <p>
 * Uses the session-level form of {@code set_config} (third argument {@code false}), not the
 * transaction-local form: this call runs before Hibernate starts its own transaction, while the
 * connection is still in autocommit mode, so a transaction-local value would revert immediately -
 * before the queries it's meant to protect ever run. Session-level is safe here specifically
 * because every connection borrowed from this pool passes through this method first, so
 * {@code app.tenant_id} is always overwritten (to the real tenant, or {@code ''} when
 * {@link TenantContext} is empty) before any query executes on it - there is no code path in this
 * app that obtains a raw {@link Connection} from this {@link DataSource} outside Hibernate.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = obtainTargetDataSource().getConnection();
        applyTenant(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = obtainTargetDataSource().getConnection(username, password);
        applyTenant(connection);
        return connection;
    }

    private void applyTenant(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.getCurrentTenant();
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
            ps.setString(1, tenantId != null ? tenantId.toString() : "");
            ps.execute();
        }
    }
}
