package com.dhruv.offlinepayment_relay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RelayPacketResponse(
        UUID id,
        UUID senderDeviceId,
        UUID receiverDeviceId,
        String ciphertextHash,
        Instant packetTimestamp,
        String status,
        BigDecimal decryptedAmount
) {
}
