package com.tontine.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpRequest {
    @NotBlank private String telephone;
    @NotBlank private String code;
}
