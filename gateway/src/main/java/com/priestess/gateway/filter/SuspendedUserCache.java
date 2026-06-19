package com.priestess.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SuspendedUserCache — In-memory cache pengguna yang sedang dalam status SUSPENDED.
 *
 * <p>Sesuai rules-eop-priestess.md SECTION 6:
 * <blockquote>
 * "E.O.P Gateway atau internal cache service yang bertindak sebagai Consumer
 * akan menangkap pesan tersebut secara real-time dan langsung menandai sesi
 * user tersebut tidak valid saat itu juga di memori, memberikan efek penendangan
 * user secara instan tanpa menunggu token kedaluwarsa 15 menit."
 * </blockquote>
 *
 * <h2>Cara Kerja</h2>
 * <ol>
 *   <li>{@code SuspendedUserConsumer} menambahkan userId ke cache saat event
 *       {@code user.suspended} diterima dari RabbitMQ.</li>
 *   <li>{@code GatewayJwtFilter} memeriksa cache ini setelah validasi JWT berhasil.
 *       Jika userId ada di cache, request langsung ditolak 403 Forbidden.</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>Menggunakan {@link ConcurrentHashMap} sebagai backing store untuk menjamin
 * keamanan akses concurrent dari multiple thread (RabbitMQ listener thread
 * dan HTTP request thread).
 *
 * <h2>Limitasi</h2>
 * <p>Cache ini bersifat in-memory. Jika Gateway di-restart, cache akan kosong
 * dan pengguna yang suspended baru bisa menggunakan token lama hingga kedaluwarsa.
 * Ini adalah trade-off yang dapat diterima untuk menyederhanakan infrastruktur
 * sesuai batasan UAS (tanpa Redis/Memcached).
 */
@Slf4j
@Component
public class SuspendedUserCache {

    /**
     * Set thread-safe berisi UUID (String) pengguna yang sedang SUSPENDED.
     * Menggunakan keySet ConcurrentHashMap untuk menjamin concurrent access safety.
     */
    private final Set<String> suspendedUserIds = ConcurrentHashMap.newKeySet();

    /**
     * Menambahkan userId ke cache suspended.
     * Dipanggil oleh {@code SuspendedUserConsumer} saat event broker diterima.
     *
     * @param userId UUID pengguna yang dibekukan (String format)
     */
    public void addSuspendedUser(String userId) {
        suspendedUserIds.add(userId);
        log.warn("[SuspendedUserCache] User {} ditambahkan ke suspended cache. " +
                "Total suspended: {}", userId, suspendedUserIds.size());
    }

    /**
     * Memeriksa apakah userId ada di cache suspended.
     * Dipanggil oleh {@code GatewayJwtFilter} untuk setiap request yang masuk.
     *
     * @param userId UUID pengguna dari klaim JWT sub
     * @return {@code true} jika user sedang SUSPENDED, {@code false} jika normal
     */
    public boolean isSuspended(String userId) {
        return suspendedUserIds.contains(userId);
    }

    /**
     * Menghapus userId dari cache (opsional — jika akun direaktivasi).
     *
     * @param userId UUID pengguna yang status suspendnya dicabut
     */
    public void removeSuspendedUser(String userId) {
        boolean removed = suspendedUserIds.remove(userId);
        if (removed) {
            log.info("[SuspendedUserCache] User {} dihapus dari suspended cache.", userId);
        }
    }

    /**
     * Mengembalikan jumlah user yang sedang suspended di cache.
     * Digunakan untuk monitoring/logging.
     *
     * @return jumlah user suspended
     */
    public int size() {
        return suspendedUserIds.size();
    }
}
