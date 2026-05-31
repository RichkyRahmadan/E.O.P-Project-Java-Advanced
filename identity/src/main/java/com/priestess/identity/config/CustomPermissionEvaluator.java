package com.priestess.identity.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * CustomPermissionEvaluator — Evaluator hak akses dinamis berbasis nama menu.
 *
 * <p>Mengimplementasikan antarmuka {@link PermissionEvaluator} milik Spring Security
 * untuk mendukung ekspresi {@code @PreAuthorize} yang menggunakan metode
 * {@code hasPermission(...)} di level Controller.
 *
 * <h2>Cara Kerja</h2>
 * <p>Ketika sebuah method Controller didekorasi dengan:
 * <pre>
 *   {@literal @}PreAuthorize("hasPermission(null, 'GENERATE_QRIS_DYNAMIC')")
 * </pre>
 * Spring Security akan memanggil {@link #hasPermission(Authentication, Object, Object)}
 * dari kelas ini. Evaluator kemudian memeriksa apakah daftar {@link GrantedAuthority}
 * yang sudah disuntikkan oleh {@code JWTFilter} ke dalam {@link Authentication} principal
 * mengandung authority yang namanya sama persis dengan string nama menu tersebut.
 *
 * <h2>Integrasi dengan JWTFilter</h2>
 * <p>{@code JWTFilter} memecah klaim {@code permissions} dari JWT (format: string
 * dipisah koma) dan mendaftarkan setiap nama menu sebagai {@link org.springframework.security.core.authority.SimpleGrantedAuthority}.
 * Evaluator ini tinggal mencocokkan nama authority tersebut dengan string permission
 * yang diminta oleh anotasi {@code @PreAuthorize}.
 *
 * <h2>Contoh Penggunaan di Controller</h2>
 * <pre>{@code
 * // Memastikan user memiliki permission 'SCAN_QRIS' sebelum method dieksekusi
 * @PreAuthorize("hasPermission(null, 'SCAN_QRIS')")
 * @PostMapping("/qris/scan")
 * public ResponseEntity<?> scanQris(...) { ... }
 *
 * // Memastikan user memiliki permission 'VERIFY_KYC'
 * @PreAuthorize("hasPermission(null, 'VERIFY_KYC')")
 * @PatchMapping("/admin/kyc/{userId}")
 * public ResponseEntity<?> verifyKyc(...) { ... }
 * }</pre>
 *
 * <p>Evaluator ini didaftarkan ke dalam {@code MethodSecurityExpressionHandler}
 * di kelas {@link SecurityConfig}.
 *
 * @see SecurityConfig
 * @see com.priestess.identity.utility.JWTFilter
 */
@Slf4j
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    // =========================================================================
    // PRIMARY EVALUATION METHOD
    // =========================================================================

    /**
     * Mengevaluasi apakah pengguna yang sedang terautentikasi memiliki
     * hak akses tertentu.
     *
     * <p>Dipanggil oleh Spring Security ketika ekspresi
     * {@code hasPermission(targetDomainObject, permission)} dievaluasi.
     * Dalam konteks E.O.P, parameter {@code targetDomainObject} selalu
     * {@code null} karena otorisasi dilakukan berdasarkan nama menu (string),
     * bukan berdasarkan objek domain tertentu.
     *
     * @param authentication      objek Authentication yang diisi oleh {@code JWTFilter};
     *                            berisi daftar GrantedAuthority dari role dan permissions
     * @param targetDomainObject  tidak digunakan dalam konteks E.O.P (selalu {@code null})
     * @param permission          nama menu yang diperiksa, contoh: {@code "SCAN_QRIS"}
     * @return {@code true} jika pengguna memiliki authority yang namanya cocok dengan
     *         {@code permission}; {@code false} jika tidak ditemukan atau {@code authentication}
     *         bernilai {@code null}
     */
    @Override
    public boolean hasPermission(Authentication authentication,
                                 Object targetDomainObject,
                                 Object permission) {
        if (authentication == null || permission == null) {
            log.warn("[PermissionEvaluator] Evaluasi gagal: authentication atau permission bernilai null.");
            return false;
        }

        String requiredPermission = permission.toString();
        String principalName = authentication.getName();

        boolean granted = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(requiredPermission));

        if (granted) {
            log.debug("[PermissionEvaluator] IZIN DIBERIKAN — principal={}, permission={}",
                    principalName, requiredPermission);
        } else {
            log.warn("[PermissionEvaluator] AKSES DITOLAK — principal={} tidak memiliki permission={}",
                    principalName, requiredPermission);
        }

        return granted;
    }

    // =========================================================================
    // SECONDARY EVALUATION METHOD (TARGET TYPE)
    // =========================================================================

    /**
     * Overload dari evaluasi permission berdasarkan ID dan tipe target domain.
     *
     * <p>Method ini disediakan untuk memenuhi kontrak antarmuka {@link PermissionEvaluator}.
     * Dalam implementasi E.O.P, method ini mendelegasikan evaluasinya ke
     * {@link #hasPermission(Authentication, Object, Object)} dengan {@code targetDomainObject}
     * bernilai {@code null}, karena sistem E.O.P menggunakan otorisasi berbasis nama menu,
     * bukan berbasis objek domain.
     *
     * @param authentication  objek Authentication pengguna yang sedang aktif
     * @param targetId        ID objek domain (tidak digunakan dalam E.O.P)
     * @param targetType      tipe objek domain (tidak digunakan dalam E.O.P)
     * @param permission      nama menu yang diperiksa
     * @return hasil dari delegasi ke {@link #hasPermission(Authentication, Object, Object)}
     */
    @Override
    public boolean hasPermission(Authentication authentication,
                                 Serializable targetId,
                                 String targetType,
                                 Object permission) {
        log.debug("[PermissionEvaluator] hasPermission (targetId overload) dipanggil dengan targetType={}, permission={}",
                targetType, permission);
        return hasPermission(authentication, null, permission);
    }
}
