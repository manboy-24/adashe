package com.tontine.repository;

import com.tontine.entity.TirageInteret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TirageInteretRepository extends JpaRepository<TirageInteret, Long> {
    Optional<TirageInteret> findByTontineIdAndNumeroCycleAndMembreId(
            Long tontineId, Integer numeroCycle, Long membreId);

    List<TirageInteret> findByTontineIdAndNumeroCycle(Long tontineId, Integer numeroCycle);

    void deleteByTontineIdAndNumeroCycleAndMembreId(Long tontineId, Integer numeroCycle, Long membreId);
}
