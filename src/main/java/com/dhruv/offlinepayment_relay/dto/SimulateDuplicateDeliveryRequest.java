package com.dhruv.offlinepayment_relay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SimulateDuplicateDeliveryRequest(

        @NotNull
        UUID senderDeviceId,

        @NotNull
        UUID receiverDeviceId,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @Min(2)
        @Max(20)
        Integer concurrentPaths
) {
}
