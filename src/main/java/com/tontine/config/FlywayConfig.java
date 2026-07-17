package com.tontine.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    /**
     * repair() avant migrate() : purge les entrées de migrations échouées dans
     * flyway_schema_history et réaligne les checksums avant d'appliquer les
     * migrations en attente.
     *
     * Sans cela, une migration à moitié appliquée (ex. V20 lors d'un déploiement
     * interrompu) laisse une entrée success=0 qui fait échouer la validation
     * Flyway à chaque démarrage : l'app ne démarre jamais, le healthcheck
     * /api/actuator/health reste indisponible et le déploiement Railway expire.
     */
    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
