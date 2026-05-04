package com.tontine.service;
import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface TontineService {
    TontineResponse creerTontine(TontineRequest request, Long createurId);
    List<TontineResponse> getMesTontines(Long userId, Pageable pageable);
    TontineResponse getTontineById(Long tontineId, Long userId);
    MembreResponse ajouterMembre(Long tontineId, AjoutMembreRequest request, Long adminId);
    ApiResponse<String> rejoindreParCode(String code, Long userId);
    ApiResponse<String> accepterInvitation(Long tontineId, Long userId);
    ApiResponse<String> declinerInvitation(Long tontineId, Long userId);
    ApiResponse<String> retirerMembre(Long tontineId, Long membreId, Long adminId);
    ApiResponse<String> supprimerTontine(Long tontineId, Long adminId);
    TontineResponse demarrerTontine(Long tontineId, Long adminId);
    TontineResponse terminerTontine(Long tontineId, Long createurId);
    TontineResponse modifierTontine(Long tontineId, ModifierTontineRequest request, Long adminId);
    TontineResponse configurerTontine(Long tontineId, ConfigurerTontineRequest request, Long createurId);
    CotisationResponse enregistrerCotisation(CotisationRequest request, Long adminId);
    List<CotisationResponse> getCotisationsTontine(Long tontineId, Long userId, Pageable pageable);
    TirageResponse effectuerTirage(Long tontineId, TirageRequest request, Long adminId);
    TirageResponse confirmerTirage(Long tontineId, Long tirageId, Long adminId);
    List<TirageResponse> getHistoriqueTirages(Long tontineId, Long userId);
    StatistiquesResponse getStatistiques(Long tontineId, Long userId);
    byte[] exportCotisationsCsv(Long tontineId, Long userId);
    DashboardResponse getDashboard(Long userId);
}