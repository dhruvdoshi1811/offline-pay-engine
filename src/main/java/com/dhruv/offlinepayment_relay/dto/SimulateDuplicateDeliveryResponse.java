package com.dhruv.offlinepayment_relay.dto;

import java.util.List;
import java.util.UUID;

public record SimulateDuplicateDeliveryResponse(
        UUID packetId,
        int totalPaths,
        int settledCount,
        int rejectedCount,
        List<PathOutcome> outcomes
) {
}
