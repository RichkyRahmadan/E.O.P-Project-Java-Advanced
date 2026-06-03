package com.priestess.oracle.config;

import com.priestess.oracle.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Menangkap semua exception dan mengubahnya menjadi
 * format JSON yang seragam menggunakan {@link ApiResponse}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Menangani error validasi @Valid (field tidak memenuhi constraint). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Validasi input gagal: " + errors));
    }

    /** Menangani ResponseStatusException (404 Not Found, dsb.). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(ex.getStatusCode().value(), ex.getReason()));
    }

    /** Fallback: menangani exception yang tidak terduga. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("[GlobalExceptionHandler] Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "Terjadi kesalahan internal. Silakan coba lagi."));
    }
}
