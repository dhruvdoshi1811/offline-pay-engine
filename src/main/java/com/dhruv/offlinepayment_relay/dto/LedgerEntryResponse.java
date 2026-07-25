package com.dhruv.offlinepayment_relay.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID packetId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant settledAt
) {
}
