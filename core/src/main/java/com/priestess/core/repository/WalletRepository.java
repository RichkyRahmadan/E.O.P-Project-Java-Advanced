package com.priestess.core.repository;

import com.priestess.core.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    Optional<WalletEntity> findByOwnerId(UUID ownerId);

    Optional<WalletEntity> findByOwnerIdAndOwnerType(UUID ownerId, String ownerType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletEntity w WHERE w.ownerId = :ownerId")
    Optional<WalletEntity> findByOwnerIdWithLock(@Param("ownerId") UUID ownerId);
}
