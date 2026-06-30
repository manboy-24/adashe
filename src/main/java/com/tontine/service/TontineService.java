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
    /** Le gagnant accepte (true) ou décline (false) sa cagnotte — fenêtre de 15 min après le tirage. */
    TirageResponse repondreTirage(Long tontineId, Long tirageId, Long userId, boolean accepte);
    TirageResponse confirmerTirage(Long tontineId, Long tirageId, Long adminId);
    /** S'enregistrer (ou se retirer) comme intéressé pour recevoir la cagnotte du cycle en cours. */
    ApiResponse<String> exprimerInteret(Long tontineId, Long userId, boolean interesse);
    /** Membres ayant exprimé leur intérêt pour le cycle en cours — aide l'admin à choisir un remplaçant. */
    List<MembreResponse> getInteresses(Long tontineId, Long userId);
    /** Remplace le bénéficiaire d'un tirage DECLINE — relance la fenêtre de réponse de 15 min pour le nouveau. */
    TirageResponse choisirRemplacant(Long tontineId, Long tirageId, Long nouveauMembreId, Long adminId);
    /** N'importe quel membre peut signaler un problème — suspend le tirage jusqu'à résolution par l'admin. */
    TirageLitigeResponse signalerLitige(Long tontineId, Long tirageId, Long userId, String motif);
    /** Historique des signalements pour un tirage (créateur/admin). */
    List<TirageLitigeResponse> getLitiges(Long tontineId, Long tirageId, Long userId);
    /** L'admin tranche : confirme (tirage invalidé → DECLINE, à remplacer) ou rejette (reprend son cours). */
    TirageLitigeResponse resoudreLitige(Long tontineId, Long tirageId, Long litigeId, boolean confirme, String commentaire, Long adminId);
    List<TirageResponse> getHistoriqueTirages(Long tontineId, Long userId);
    StatistiquesResponse getStatistiques(Long tontineId, Long userId);
    byte[] exportCotisationsCsv(Long tontineId, Long userId);
    DashboardResponse getDashboard(Long userId);
    /** Redéfinit l'ordre de passage des membres (rotatif). Admin only, avant ou pendant la tontine. */
    void modifierOrdrePassage(Long tontineId, OrdrePassageRequest request, Long adminId);
}