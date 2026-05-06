package com.tontine.dto.response;

import com.tontine.enums.PaiementMode;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CompteWalletResponse {
    private Long id;
    private PaiementMode operateur;
    private String telephone;
    private Boolean actif;
    private LocalDateTime updatedAt;
}
