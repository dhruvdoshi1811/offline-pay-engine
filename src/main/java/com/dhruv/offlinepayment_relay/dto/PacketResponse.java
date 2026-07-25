package com.dhruv.offlinepayment_relay.dto;

import java.time.Instant;
import java.util.UUID;

public record PacketResponse(
        UUID id,
        UUID senderDeviceId,
        UUID receiverDeviceId,
        String ciphertextHash,
        Instant packetTimestamp,
        String status
) {
}
