package com.dhruv.offlinepayment_relay.dto;

import java.util.List;

public record RejectedPacketsResponse(
        List<PacketResponse> expiredPackets,
        List<PacketResponse> duplicateDeliveryPackets,
        List<PacketResponse> insufficientFundsPackets
) {
}
