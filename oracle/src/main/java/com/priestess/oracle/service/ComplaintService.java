package com.priestess.oracle.service;

import com.priestess.oracle.dto.SubmitComplaintRequest;
import com.priestess.oracle.entity.ComplaintDocument;

import java.util.List;

/**
 * ComplaintService — Kontrak interface logika bisnis pengaduan.
 *
 * <p>Sesuai SECTION 3 blueprint, interface ini hanya mendefinisikan kontrak.
 * Seluruh implementasi berada di {@link com.priestess.oracle.service.impl.ComplaintServiceImpl}.
 */
public interface ComplaintService {

    /**
     * Menerima keluhan dari pengguna, menyimpannya dengan status {@code OPEN},
     * dan langsung mengembalikan ID keluhan. Proses analisis AI berjalan di background.
     *
     * @param userId  UUID pengguna dari header {@code X-User-Id}
     * @param request data keluhan dari request body
     * @return dokumen keluhan yang baru disimpan
     */
    ComplaintDocument submitComplaint(String userId, SubmitComplaintRequest request);

    /**
     * Mengambil detail satu keluhan berdasarkan {@code complaintId}.
     *
     * @param complaintId ID keluhan (bukan MongoDB _id)
     * @return dokumen keluhan
     */
    ComplaintDocument getComplaintById(String complaintId);

    /**
     * Mengambil semua keluhan milik pengguna tertentu.
     *
     * @param userId UUID pengguna dari header {@code X-User-Id}
     * @return daftar keluhan
     */
    List<ComplaintDocument> getMyComplaints(String userId);

    /**
     * Mengambil semua keluhan berdasarkan status (untuk Admin).
     *
     * @param status nilai status: {@code OPEN}, {@code IN_PROGRESS}, atau {@code RESOLVED}
     * @return daftar keluhan dengan status tersebut
     */
    List<ComplaintDocument> getComplaintsByStatus(String status);

    /**
     * Memperbarui status keluhan (digunakan Admin untuk menandai keluhan selesai).
     *
     * @param complaintId ID keluhan
     * @param newStatus   status baru
     * @return dokumen keluhan yang sudah diperbarui
     */
    ComplaintDocument updateStatus(String complaintId, String newStatus);
}
