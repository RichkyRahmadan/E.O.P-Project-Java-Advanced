package com.priestess.core.repository;

import com.priestess.core.entity.VoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<VoucherEntity, Long> {

    @Query("SELECT v FROM VoucherEntity v WHERE v.code = :code AND v.isRedeemed = false")
    Optional<VoucherEntity> findAvailableByCode(@Param("code") String code);

    long countByIsRedeemed(boolean isRedeemed);

    boolean existsByCode(String code);
}
