package com.priestess.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** DTO error response seragam untuk Core Finance Service. */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ErrorResponse {
    private int           status;
    private String        message;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
