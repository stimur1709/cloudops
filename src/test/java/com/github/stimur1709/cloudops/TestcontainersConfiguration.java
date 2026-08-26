package com.github.stimur1709.cloudops;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    @Bean
    SqlStatementRecorder sqlStatementRecorder() {
        return new SqlStatementRecorder();
    }

    @Bean
    HibernatePropertiesCustomizer statementInspectorCustomizer(SqlStatementRecorder recorder) {
        return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, recorder);
    }
}
