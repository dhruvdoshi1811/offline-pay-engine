package com.dhruv.offlinepayment_relay.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceRequest(

        @NotBlank
        String ownerName,

        @NotBlank
        String publicKey
) {
}
