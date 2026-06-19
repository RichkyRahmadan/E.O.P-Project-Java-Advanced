package com.priestess.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CoreApplication — Entry point Core Finance Service (Port 8082).
 *
 * <p>Konfigurasi dual-repository eksplisit diperlukan karena service ini
 * menggunakan dua jenis repository secara bersamaan:
 * <ul>
 *   <li><b>JPA Repository</b> → {@code WalletRepository}, {@code VoucherRepository}
 *       (PostgreSQL - eop_finance_db)</li>
 *   <li><b>MongoDB Repository</b> → {@code TransactionRepository}
 *       (MongoDB - eop_transaction_log)</li>
 * </ul>
 *
 * <p>Tanpa anotasi {@code @EnableJpaRepositories} dan {@code @EnableMongoRepositories}
 * dengan {@code basePackages} eksplisit, Spring Boot bisa gagal membedakan
 * repository JPA dan MongoDB saat keduanya berada dalam package yang sama,
 * menyebabkan {@code BeanCreationException} saat startup.
 *
 * <p>{@code @EnableScheduling} diaktifkan sesuai coding-style.md Section 6.2
 * untuk mendukung fitur {@code @Scheduled} pada {@code VoucherScheduler}.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.priestess.core.repository")
@EnableMongoRepositories(basePackages = "com.priestess.core.repository")
@EnableScheduling
public class CoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoreApplication.class, args);
	}
}
