package com.tontine.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.slf4j.MDC;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    /** Présent uniquement sur les erreurs — permet au support de retrouver la trace dans les logs. */
    private String correlationId;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .correlationId(MDC.get("correlationId"))
                .build();
    }
}
