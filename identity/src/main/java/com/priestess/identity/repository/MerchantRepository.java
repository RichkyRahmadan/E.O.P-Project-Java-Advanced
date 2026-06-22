package com.priestess.identity.repository;

import com.priestess.identity.entity.MerchantEntity;
import com.priestess.identity.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {

    Optional<MerchantEntity> findByUser(UserEntity user);

    Optional<MerchantEntity> findByUser_Id(UUID userId);

    boolean existsByUser(UserEntity user);

    List<MerchantEntity> findAllByIsVerified(Boolean isVerified);
}
