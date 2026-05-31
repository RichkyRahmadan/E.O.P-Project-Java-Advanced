package com.priestess.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ErrorResponse — Format JSON error yang seragam untuk seluruh REST API E.O.P.
 *
 * <p>Setiap exception yang dilempar oleh lapisan Service atau Controller akan
 * ditangkap oleh {@code GlobalExceptionHandler} dan dibungkus ke dalam format
 * objek ini sebelum dikirimkan ke klien Angular.
 *
 * <p>Konsistensi format ini penting agar klien Angular dapat memprogram
 * satu interceptor HTTP tunggal untuk menangani semua jenis error dari semua
 * endpoint tanpa perlu menebak struktur respons.
 *
 * <h3>Contoh JSON Output</h3>
 * <pre>
 * {
 *   "status"    : 401,
 *   "message"   : "Username atau password yang Anda masukkan salah!",
 *   "timestamp" : "2025-06-01T10:30:00.123456"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    /**
     * Kode HTTP status numerik dari error yang terjadi.
     * Contoh: {@code 400}, {@code 401}, {@code 403}, {@code 500}.
     * Disertakan di body agar klien tidak perlu hanya bergantung pada
     * status code HTTP header.
     */
    private int status;

    /**
     * Pesan error yang aman dibaca oleh pengguna akhir.
     * JANGAN menyertakan detail teknis internal (stack trace, nama kelas,
     * dsb.) pada field ini untuk mencegah kebocoran informasi sistem.
     */
    private String message;

    /**
     * Waktu server saat error terjadi (timezone server).
     * Berguna untuk korelasi log antara server dan klien.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
