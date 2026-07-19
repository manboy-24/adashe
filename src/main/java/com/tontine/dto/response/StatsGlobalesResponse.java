package com.tontine.dto.response;
import lombok.*;
import java.util.List;

/**
 * Réponse agrégée de l'écran Statistiques mobile : stats + cotisations de toutes
 * les tontines de l'utilisateur en un seul appel réseau (au lieu de 2 par tontine).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatsGlobalesResponse {
    private List<TontineStatsBloc> tontines;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TontineStatsBloc {
        private Long tontineId;
        /** null si l'utilisateur n'est pas créateur/admin de cette tontine. */
        private StatistiquesResponse statistiques;
        private List<CotisationResponse> cotisations;
    }
}
