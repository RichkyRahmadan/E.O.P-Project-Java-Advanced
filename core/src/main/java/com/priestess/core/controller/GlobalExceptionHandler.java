package com.priestess.core.controller;

import com.priestess.core.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — Penanganan error terpusat Core Finance Service.
 *
 * <p>Menangani Exception khusus Core Finance termasuk
 * {@link ObjectOptimisticLockingFailureException} yang dilempar saat
 * Optimistic Locking mendeteksi konflik saldo ({@code @Version}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Konflik Optimistic Locking — HTTP 409 Conflict. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("[CoreExceptionHandler] Optimistic Locking conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .message("Konflik transaksi terdeteksi. Silakan coba lagi dalam beberapa saat.")
                .timestamp(LocalDateTime.now()).build());
    }

    /** Validasi input gagal — HTTP 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        log.warn("[CoreExceptionHandler] Validasi gagal: {}", msg);
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value()).message(msg)
                .timestamp(LocalDateTime.now()).build());
    }

    /** Pelanggaran aturan bisnis dari Service layer — HTTP 400. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("[CoreExceptionHandler] Business rule violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value()).message(ex.getMessage())
                .timestamp(LocalDateTime.now()).build());
    }

    /** Jaring terakhir — HTTP 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("[CoreExceptionHandler] Unhandled Exception: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Terjadi kesalahan internal pada server.")
                .timestamp(LocalDateTime.now()).build());
    }
}
