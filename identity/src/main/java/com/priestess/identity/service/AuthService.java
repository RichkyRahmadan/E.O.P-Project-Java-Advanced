package com.priestess.identity.service;

import com.priestess.identity.dto.AuthResponse;
import com.priestess.identity.dto.LoginRequest;
import com.priestess.identity.dto.RefreshTokenRequest;
import com.priestess.identity.dto.RegisterMerchantByOwnerRequest;
import com.priestess.identity.dto.RegisterMerchantRequest;
import com.priestess.identity.dto.RegisterUserRequest;
import com.priestess.identity.dto.RegisterResponse;
import com.priestess.identity.dto.UserResolutionResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    RegisterResponse registerUser(RegisterUserRequest request);

    RegisterResponse registerMerchant(RegisterMerchantRequest request);

    RegisterResponse registerMerchantByOwner(String ownerUserId, RegisterMerchantByOwnerRequest request);

    void logout(String refreshToken);

    UserResolutionResponse resolveUser(String usernameOrEmail);
}
