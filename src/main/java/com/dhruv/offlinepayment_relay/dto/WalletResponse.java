package com.dhruv.offlinepayment_relay.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID deviceId,
        BigDecimal balance
) {
}
