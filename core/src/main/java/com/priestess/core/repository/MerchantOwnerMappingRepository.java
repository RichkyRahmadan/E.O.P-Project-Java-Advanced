package com.priestess.core.repository;

import com.priestess.core.entity.MerchantOwnerMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * MerchantOwnerMappingRepository — Mengelola akses database untuk mapping owner merchant.
 */
@Repository
public interface MerchantOwnerMappingRepository extends JpaRepository<MerchantOwnerMappingEntity, UUID> {
    Optional<MerchantOwnerMappingEntity> findByMerchantUserId(UUID merchantUserId);
}
