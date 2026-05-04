package com.tontine.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPinRequest {
    @NotBlank
    private String telephone;

    /** "SMS" ou "EMAIL" */
    @NotBlank
    private String canal;
}
