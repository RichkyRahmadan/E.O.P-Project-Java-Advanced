package com.priestess.oracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * OracleApplication — Entry point E.O.P Support & Oracle Service (Port 8083).
 *
 * <h2>Fitur yang Diaktifkan</h2>
 * <ul>
 *   <li>{@code @EnableAsync}  — Mengaktifkan eksekusi method {@code @Async}
 *       pada thread pool yang dikonfigurasi di {@code application.properties}
 *       ({@code spring.task.execution.*}). Digunakan oleh {@code GeminiAiServiceImpl}
 *       agar analisis AI tidak memblokir thread HTTP utama.</li>
 *   <li>{@code @EnableRetry}  — Mengaktifkan mekanisme {@code @Retryable}
 *       dari library {@code spring-retry}. Digunakan pada {@code analyzeComplaint}
 *       untuk retry otomatis (max 3x, jeda 2 detik) jika Gemini API timeout.</li>
 * </ul>
 *
 * <h2>Database</h2>
 * <p>Hanya MongoDB — {@code eop_support_db}. Tidak ada PostgreSQL di service ini.
 */
@SpringBootApplication
@EnableAsync
@EnableRetry
public class OracleApplication {

    public static void main(String[] args) {
        SpringApplication.run(OracleApplication.class, args);
    }
}
