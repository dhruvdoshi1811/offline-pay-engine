package com.dhruv.offlinepayment_relay.dto;

public record PathOutcome(
        String relayPathId,
        String status,
        String detail
) {
}
