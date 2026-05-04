package com.tontine.dto.response;

import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class VirementAmendeResponse {
    private Long id;
    private Long paiementId;
    private BigDecimal montant;
    private PaiementMode operateur;
    private String numeroBeneficiaire;
    private String referenceTontine;
    private VirementAmendeStatut statut;
    private String referenceTransfert;
    private String messageErreur;
    private LocalDateTime createdAt;
    private LocalDateTime dateVirement;
}
