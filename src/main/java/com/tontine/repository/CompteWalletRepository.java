package com.tontine.repository;

import com.tontine.entity.CompteWallet;
import com.tontine.enums.PaiementMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompteWalletRepository extends JpaRepository<CompteWallet, Long> {

    List<CompteWallet> findByUtilisateurId(Long utilisateurId);

    Optional<CompteWallet> findByUtilisateurIdAndOperateur(Long utilisateurId, PaiementMode operateur);

    /** Retourne les wallets actifs (actif=true, téléphone renseigné) de l'utilisateur. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT w FROM CompteWallet w WHERE w.utilisateur.id = :userId AND w.actif = true AND w.telephone IS NOT NULL")
    List<CompteWallet> findActifsByUtilisateurId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
