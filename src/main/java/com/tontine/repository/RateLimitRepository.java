package com.tontine.repository;

import com.tontine.entity.RateLimit;
import com.tontine.entity.RateLimitId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RateLimitRepository extends JpaRepository<RateLimit, RateLimitId> {

    /**
     * UPSERT atomique MySQL :
     * - Première requête de l'IP sur cet endpoint → INSERT avec hits = 1.
     * - Requête suivante dans la même fenêtre → hits + 1.
     * - Fenêtre expirée → remet hits à 1 et reset window_start.
     *
     * L'atomicité est garantie par le moteur InnoDB (verrou de ligne sur la PK).
     * Pas besoin de synchronized côté applicatif.
     */
    @Modifying
    @Query(value = """
            INSERT INTO rate_limit (ip, endpoint, hits, window_start)
            VALUES (:ip, :endpoint, 1, NOW(3))
            ON DUPLICATE KEY UPDATE
              hits         = IF(TIMESTAMPDIFF(SECOND, window_start, NOW(3)) > :windowSec, 1, hits + 1),
              window_start = IF(TIMESTAMPDIFF(SECOND, window_start, NOW(3)) > :windowSec, NOW(3), window_start)
            """, nativeQuery = true)
    void upsert(@Param("ip")        String ip,
                @Param("endpoint")  String endpoint,
                @Param("windowSec") long   windowSec);

    @Query("SELECT r.hits FROM RateLimit r WHERE r.id.ip = :ip AND r.id.endpoint = :endpoint")
    Optional<Integer> findHits(@Param("ip") String ip, @Param("endpoint") String endpoint);

    @Modifying
    @Query("DELETE FROM RateLimit r WHERE r.windowStart < :threshold")
    void deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
