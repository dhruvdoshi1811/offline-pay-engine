package com.dhruv.offlinepayment_relay.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role
) {
}
