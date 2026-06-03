package com.priestess.oracle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ApiResponse — Standar respons JSON untuk seluruh endpoint Oracle Service.
 *
 * <pre>
 * {
 *   "status": 202,
 *   "message": "Keluhan berhasil diterima",
 *   "data": { ... },
 *   "timestamp": "2025-06-03T23:00:00"
 * }
 * </pre>
 *
 * @param <T> tipe generik untuk field {@code data}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Factory method untuk respons sukses singkat tanpa data. */
    public static <T> ApiResponse<T> success(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .status(statusCode)
                .message(message)
                .build();
    }

    /** Factory method untuk respons sukses dengan payload data. */
    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return ApiResponse.<T>builder()
                .status(statusCode)
                .message(message)
                .data(data)
                .build();
    }

    /** Factory method untuk respons error. */
    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .status(statusCode)
                .message(message)
                .build();
    }
}
