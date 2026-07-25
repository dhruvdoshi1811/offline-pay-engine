package com.dhruv.offlinepayment_relay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RelayPacketRequest(

        @NotNull
        UUID senderDeviceId,

        @NotNull
        UUID receiverDeviceId,

        @NotBlank
        String ciphertext,

        @NotBlank
        String encryptedSessionKey,

        @NotBlank
        String nonce,

        @NotNull
        Instant packetTimestamp,

        @NotBlank
        String relayPathId
) {
}
