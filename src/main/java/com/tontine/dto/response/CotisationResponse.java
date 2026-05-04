package com.tontine.dto.response;
import com.tontine.enums.PaiementStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CotisationResponse {
    private Long id;
    private Long tontineId;
    private String tontineNom;
    private Long membreId;
    private String membreNom;
    private BigDecimal montant;
    private BigDecimal montantAmende;
    private Integer numeroCycle;
    private PaiementStatus statut;
    private LocalDate dateEcheance;
    private LocalDate datePaiement;
    private String modePaiement;
    private String referenceTransaction;
    private LocalDateTime createdAt;
    private Boolean estEnRetard;
}
