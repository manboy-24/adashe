package com.tontine.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // Crée la table ShedLock si elle n'existe pas encore (idempotent)
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS shedlock (
                name       VARCHAR(64)  NOT NULL,
                lock_until TIMESTAMP(3) NOT NULL,
                locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                locked_by  VARCHAR(255) NOT NULL,
                PRIMARY KEY (name)
            )
        """);
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .usingDbTime()   // utilise l'heure DB, pas l'heure du serveur
                        .build()
        );
    }
}
