package com.dhruv.offlinepayment_relay.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String ownerName,
        String publicKey,
        Instant registeredAt,
        UUID walletId
) {
}
