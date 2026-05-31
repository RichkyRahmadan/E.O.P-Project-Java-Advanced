package com.priestess.identity.controller;

import com.priestess.identity.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — Penanganan error terpusat untuk seluruh REST API Identity Service.
 *
 * <p>Sesuai standar SECTION 8 blueprint E.O.P, kelas ini bertugas sebagai
 * <b>jaring pengaman terpusat</b>: menangkap setiap exception yang merambat keluar
 * dari lapisan Controller atau Service, lalu mengubahnya menjadi format JSON
 * {@link ErrorResponse} yang seragam sebelum dikirimkan ke klien Angular.
 *
 * <p>Dengan pola ini, tidak ada {@code try-catch} yang perlu ditulis di dalam
 * kelas Controller manapun. Controller tetap bersih dan hanya fokus pada
 * pemetaan HTTP.
 *
 * <h2>Hierarki Penanganan Exception</h2>
 * <p>Spring memilih handler berdasarkan tipe exception yang paling spesifik
 * (paling turunan). Urutan efektif dari paling spesifik ke paling umum:
 * <ol>
 *   <li>{@link BadCredentialsException} → HTTP 401 (Unauthorized)</li>
 *   <li>{@link MethodArgumentNotValidException} → HTTP 400 (Bad Request)</li>
 *   <li>{@link RuntimeException} → HTTP 400 (Bad Request)</li>
 *   <li>{@link Exception} → HTTP 500 (Internal Server Error) — jaring terakhir</li>
 * </ol>
 *
 * @see ErrorResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // =========================================================================
    // HANDLER 1: Credential Tidak Valid — HTTP 401
    // =========================================================================

    /**
     * Menangkap {@link BadCredentialsException} yang dilempar oleh
     * {@code AuthenticationManager} saat kombinasi username/password tidak cocok.
     *
     * <p>Pesan error yang dikembalikan sengaja dibuat generik ("username atau password salah")
     * untuk mencegah <b>user enumeration attack</b> — yaitu teknik di mana penyerang
     * dapat mengetahui apakah sebuah username terdaftar atau tidak berdasarkan
     * perbedaan pesan error.
     *
     * @param ex exception yang ditangkap dari proses autentikasi Spring Security
     * @return {@link ResponseEntity} berisi {@link ErrorResponse} dengan HTTP 401
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("[GlobalExceptionHandler] BadCredentialsException: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Username atau password yang Anda masukkan salah!")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // =========================================================================
    // HANDLER 2: Validasi Input Gagal — HTTP 400
    // =========================================================================

    /**
     * Menangkap {@link MethodArgumentNotValidException} yang dilempar oleh
     * Spring ketika {@code @Valid} di parameter Controller menemukan pelanggaran
     * constraint (misalnya: field {@code username} kosong karena anotasi {@code @NotBlank}).
     *
     * <p>Semua pesan error dari semua field yang gagal dikumpulkan dan digabungkan
     * menjadi satu string dengan pemisah {@code "; "} agar klien mendapat informasi
     * lengkap dalam satu respons, tanpa harus submit berulang kali.
     *
     * <p>Contoh output message:
     * {@code "Username tidak boleh kosong.; Password tidak boleh kosong."}
     *
     * @param ex exception yang berisi daftar {@link org.springframework.validation.BindingResult}
     * @return {@link ResponseEntity} berisi {@link ErrorResponse} dengan HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Kumpulkan semua pesan error dari setiap field yang gagal validasi
        String combinedMessages = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("[GlobalExceptionHandler] Validasi gagal: {}", combinedMessages);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(combinedMessages)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =========================================================================
    // HANDLER 3: RuntimeException & Business Rule Violation — HTTP 400
    // =========================================================================

    /**
     * Menangkap {@link RuntimeException} umum yang dilempar secara eksplisit
     * oleh lapisan Service sebagai sinyal pelanggaran aturan bisnis.
     *
     * <p>Contoh utama dalam E.O.P: {@code AuthServiceImpl} melempar
     * {@code new IllegalStateException("Akun Anda telah dibekukan!")} ketika
     * status user adalah {@code SUSPENDED}. Handler ini menangkapnya dan
     * meneruskan pesan yang sama ke klien dengan HTTP 400.
     *
     * <p><b>Catatan penting:</b> {@code BadCredentialsException} adalah turunan dari
     * {@code RuntimeException}, namun Spring Security memilih handler yang paling
     * spesifik terlebih dahulu, sehingga {@link #handleBadCredentials} akan terpicu
     * sebelum handler ini untuk kasus tersebut.
     *
     * @param ex RuntimeException yang dilempar oleh lapisan Service
     * @return {@link ResponseEntity} berisi {@link ErrorResponse} dengan HTTP 400
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.warn("[GlobalExceptionHandler] RuntimeException: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =========================================================================
    // HANDLER 4: Jaring Terakhir — HTTP 500
    // =========================================================================

    /**
     * Menangkap segala jenis {@link Exception} yang tidak tertangkap oleh
     * handler-handler di atas. Bertindak sebagai <b>jaring pengaman terakhir</b>
     * untuk mencegah stack trace mentah terekspos ke klien.
     *
     * <p>Stack trace penuh dicatat di log server menggunakan level {@code ERROR}
     * agar tim developer dapat menginvestigasi penyebab sebenarnya.
     * Namun, pesan yang dikembalikan ke klien tetap aman dan generik.
     *
     * @param ex exception tidak terduga yang lolos dari semua handler lainnya
     * @return {@link ResponseEntity} berisi {@link ErrorResponse} dengan HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Log selengkap mungkin di server untuk debugging
        log.error("[GlobalExceptionHandler] Unhandled Exception: {} — {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Terjadi kesalahan internal pada server. Silakan hubungi administrator.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
