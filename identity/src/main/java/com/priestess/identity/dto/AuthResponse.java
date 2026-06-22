package com.priestess.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    private UserSummary user;

    @Getter
    @Builder
    public static class UserSummary {

        private UUID id;

        private String username;

        private String roleName;

        private String status;
    }
}
