package com.tontine.repository;

import com.tontine.entity.CompteWallet;
import com.tontine.enums.PaiementMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompteWalletRepository extends JpaRepository<CompteWallet, Long> {

    List<CompteWallet> findByUtilisateurId(Long utilisateurId);

    Optional<CompteWallet> findByUtilisateurIdAndOperateur(Long utilisateurId, PaiementMode operateur);
}
