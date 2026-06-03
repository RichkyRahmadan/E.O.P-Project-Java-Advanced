package com.priestess.oracle.repository;

import com.priestess.oracle.entity.ComplaintDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ComplaintRepository — Repository MongoDB untuk collection {@code complaints}.
 */
@Repository
public interface ComplaintRepository extends MongoRepository<ComplaintDocument, String> {

    /** Cari keluhan berdasarkan complaint_id (bukan _id MongoDB). */
    Optional<ComplaintDocument> findByComplaintId(String complaintId);

    /** Ambil semua keluhan milik satu pengguna, urut dari terbaru. */
    List<ComplaintDocument> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Ambil semua keluhan berdasarkan status (untuk dashboard Admin). */
    List<ComplaintDocument> findByStatusOrderByCreatedAtDesc(String status);
}
