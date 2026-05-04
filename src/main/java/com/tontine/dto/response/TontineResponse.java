package com.tontine.dto.response;
import com.tontine.enums.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TontineResponse {
    private Long id;
    private String nom;
    private String description;
    private BigDecimal montantContribution;
    private String devise;
    private FrequenceType frequence;
    private TirageType typeTirage;
    private TontineStatus statut;
    private LocalDate dateDebut;
    private LocalDate dateProchainCycle;
    private String tirageHeure;    // "HH:mm" ex: "18:00"
    private Integer cycleActuel;
    private Integer nombreMaxMembres;
    private String codeInvitation;  // null si non-créateur
    private BigDecimal totalCollecte;
    private Integer nombreMembresActifs;
    private Boolean estCreateur;    // true si l'utilisateur courant est le créateur
    private Float commissionPourcent;
    private BigDecimal montantAmende;
    private String numeroMtnMomo;
    private String numeroOrangeMomo;
    private LocalDateTime createdAt;
    private List<MembreResponse> membres;
}
