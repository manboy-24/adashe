package com.tontine.dto.request;
import com.tontine.enums.FrequenceType;
import com.tontine.enums.TirageType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TontineRequest {
    @NotBlank private String nom;
    private String description;
    @NotNull @DecimalMin("100") private BigDecimal montantContribution;
    @NotNull private FrequenceType frequence;
    @NotNull private TirageType typeTirage;
    @NotNull private LocalDate dateDebut;
    @Min(2) @Max(50) private Integer nombreMaxMembres = 20;
    private String devise = "FCFA";
    private Boolean cotisant = true;
}
