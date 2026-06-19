package com.tontine.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChoisirRemplacantRequest {
    @NotNull
    private Long membreId;
}
