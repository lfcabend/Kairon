package com.kairon;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the whole application against a real PostgreSQL (Testcontainers) and
 * checks that the context loads and Flyway has applied V001. Requires a running
 * Docker daemon.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class KaironApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoadsAndFlywayCreatedTheAppUserTable() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(appliedMigrations).isGreaterThanOrEqualTo(1);

        Boolean appUserExists = jdbc.queryForObject(
                "SELECT exists (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_user')",
                Boolean.class);
        assertThat(appUserExists).isTrue();
    }
}
