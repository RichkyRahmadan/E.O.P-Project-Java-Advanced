package com.priestess.oracle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SubmitComplaintRequest — Request body untuk endpoint {@code POST /api/support/complaints}.
 *
 * <p>Field {@code invoiceId} bersifat opsional. Pengguna dapat mengajukan keluhan
 * umum tanpa mengacu pada transaksi tertentu.
 */
@Data
public class SubmitComplaintRequest {

    /** ID invoice yang dikeluhkan. Boleh kosong/null untuk keluhan umum. */
    private String invoiceId;

    @NotBlank(message = "Pesan keluhan tidak boleh kosong")
    @Size(min = 20, max = 2000, message = "Pesan keluhan harus antara 20 hingga 2000 karakter")
    private String rawMessage;
}
