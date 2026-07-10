package com.example.collabodraw.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;

/**
 * Database configuration (generic MySQL / Aiven)
 */
@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    private final DataSource dataSource;
    private final Environment environment;

    public DatabaseConfig(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @PostConstruct
    public void verifyConnection() {
        String resolvedUrl = environment.getProperty("spring.datasource.url", "<missing>");
        String resolvedUser = environment.getProperty("spring.datasource.username", "<missing>");
        log.info("Resolved datasource url: {}", resolvedUrl);
        log.info("Resolved datasource username present: {}", !"<missing>".equals(resolvedUser));

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("SELECT 1");
            log.info("Database connection verified (schema managed externally)");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                log.warn("Database connectivity check failed (non-fatal). SQLState={}, cause={}", sqlException.getSQLState(), root.getMessage());
            } else {
                log.warn("Database connectivity check failed (non-fatal): {}", root.getMessage());
            }
        }
    }
}
