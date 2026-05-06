package com.tontine.service.impl;

import com.tontine.dto.request.CompteWalletRequest;
import com.tontine.dto.response.CompteWalletResponse;
import com.tontine.entity.CompteWallet;
import com.tontine.entity.Utilisateur;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.repository.CompteWalletRepository;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final CompteWalletRepository compteWalletRepository;
    private final UtilisateurRepository  utilisateurRepository;

    @Override
    public List<CompteWalletResponse> getMesComptes(Long userId) {
        return compteWalletRepository.findByUtilisateurId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CompteWalletResponse sauvegarderCompte(Long userId, CompteWalletRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Upsert : mise à jour si le compte existe déjà pour cet opérateur
        CompteWallet compte = compteWalletRepository
                .findByUtilisateurIdAndOperateur(userId, request.getOperateur())
                .orElseGet(() -> CompteWallet.builder()
                        .utilisateur(utilisateur)
                        .operateur(request.getOperateur())
                        .build());

        compte.setTelephone(request.getTelephone());
        compte.setActif(request.getActif());

        return toResponse(compteWalletRepository.save(compte));
    }

    private CompteWalletResponse toResponse(CompteWallet c) {
        return CompteWalletResponse.builder()
                .id(c.getId())
                .operateur(c.getOperateur())
                .telephone(c.getTelephone())
                .actif(c.getActif())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
