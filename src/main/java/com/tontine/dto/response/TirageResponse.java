package com.tontine.dto.response;
import com.tontine.enums.TirageType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TirageResponse {
    private Long id;
    private Long tontineId;
    private String tontineNom;
    private Long beneficiaireId;
    private String beneficiaireNom;
    private String beneficiaireAvatarId;
    private Integer numeroCycle;
    private BigDecimal montantDistribue;
    private BigDecimal commissionPrelevee;
    private TirageType methodeTirage;
    private LocalDate dateTirage;
    private Boolean confirme;
    private LocalDateTime createdAt;
}
