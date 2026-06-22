package com.priestess.oracle.repository;

import com.priestess.oracle.entity.ComplaintDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends MongoRepository<ComplaintDocument, String> {

    Optional<ComplaintDocument> findByComplaintId(String complaintId);

    List<ComplaintDocument> findByUserIdOrderByCreatedAtDesc(String userId);

    List<ComplaintDocument> findByStatusOrderByCreatedAtDesc(String status);
}
