package com.AIRevenueRecovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

    private String token;

    private String tokenType;

    private Long userId;

    private String name;

    private String email;

    private String role;
}