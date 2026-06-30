package com.tontine.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class OrdrePassageRequest {

    @NotNull
    private List<OrdreMembreDto> membres;

    @Data
    public static class OrdreMembreDto {
        @NotNull private Long    membreId;
        @NotNull private Integer ordreTour;
    }
}
