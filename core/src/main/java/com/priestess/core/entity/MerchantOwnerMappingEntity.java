package com.priestess.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * MerchantOwnerMappingEntity — Menyimpan relasi antara akun user merchant,
 * profil merchant, dan user owner pemilik merchant.
 *
 * <p>Data diisi asinkron via event merchant.registered dari Identity Service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@Entity
@Table(name = "merchant_owner_mappings")
public class MerchantOwnerMappingEntity {

    @Id
    @Column(name = "merchant_user_id", nullable = false)
    private UUID merchantUserId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "owner_phone_number", nullable = false, length = 20)
    private String ownerPhoneNumber;

    @Column(name = "merchant_name", length = 100)
    private String merchantName;
}
