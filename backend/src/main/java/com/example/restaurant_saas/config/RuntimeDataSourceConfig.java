package com.example.restaurant_saas.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Hibernate/JPA connects through the restricted {@code app_runtime} Postgres role (subject to
 * Row-Level Security), bound from {@code app.datasource.runtime.*}. Flyway migrations and
 * {@code BackupService}'s pg_dump keep using {@code spring.datasource.*} (the table-owning role)
 * directly — see docs/RLS_DESIGN.md. This is the only {@code DataSource} bean in the context, so
 * Spring Boot's JPA autoconfiguration builds the EntityManagerFactory/TransactionManager around it
 * transparently; {@code spring.flyway.url/user/password} in application.yml keep Flyway on the
 * owner role instead of picking up this bean.
 */
@Configuration
public class RuntimeDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("app.datasource.runtime")
    public DataSourceProperties runtimeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    public DataSource dataSource(DataSourceProperties runtimeDataSourceProperties) {
        return new TenantAwareDataSource(runtimeDataSourceProperties.initializeDataSourceBuilder().build());
    }
}
