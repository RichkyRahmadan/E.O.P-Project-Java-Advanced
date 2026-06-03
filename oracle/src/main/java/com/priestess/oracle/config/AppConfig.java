package com.priestess.oracle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AppConfig — Bean-bean utilitas umum yang digunakan di seluruh Oracle Service.
 */
@Configuration
public class AppConfig {

    /**
     * ObjectMapper untuk parsing JSON respons Gemini API.
     * Dikonfigurasi agar tidak gagal saat property JSON tidak dikenali
     * (untuk toleransi terhadap perubahan API Gemini di masa depan).
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        );
        return mapper;
    }
}
