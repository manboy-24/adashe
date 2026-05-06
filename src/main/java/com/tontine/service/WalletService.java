package com.tontine.service;

import com.tontine.dto.request.CompteWalletRequest;
import com.tontine.dto.response.CompteWalletResponse;

import java.util.List;

public interface WalletService {

    List<CompteWalletResponse> getMesComptes(Long userId);

    CompteWalletResponse sauvegarderCompte(Long userId, CompteWalletRequest request);
}
