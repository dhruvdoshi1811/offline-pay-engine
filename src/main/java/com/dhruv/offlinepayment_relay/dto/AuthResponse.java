package com.dhruv.offlinepayment_relay.dto;

public record AuthResponse(
        String token,
        String email,
        String role
) {
}
