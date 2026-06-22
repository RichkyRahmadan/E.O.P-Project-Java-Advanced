package com.priestess.core.repository;

import com.priestess.core.entity.TransactionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<TransactionDocument, String> {

    Optional<TransactionDocument> findByInvoiceId(String invoiceId);

    List<TransactionDocument> findBySenderUserId(String senderId);

    List<TransactionDocument> findByRecipientUserId(String recipientId);

    boolean existsByInvoiceId(String invoiceId);

    @Query("{ 'transaction_type': 'QRIS_PAYMENT', 'status': 'PENDING', 'expires_at': { $lt: ?0 } }")
    List<TransactionDocument> findExpiredPendingQris(LocalDateTime now);
}
