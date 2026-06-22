package com.priestess.identity.service;

import com.priestess.identity.dto.PendingMerchantResponse;
import com.priestess.identity.dto.PendingUserResponse;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    void verifyKyc(UUID userId);

    void suspendUser(UUID userId);

    void verifyMerchant(UUID merchantId);

    List<PendingUserResponse> getPendingUsers();

    List<PendingMerchantResponse> getPendingMerchants();
}
