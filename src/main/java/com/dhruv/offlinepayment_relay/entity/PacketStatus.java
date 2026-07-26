package com.dhruv.offlinepayment_relay.entity;

public enum PacketStatus {
    RECEIVED,
    CLAIMED,
    SETTLED,
    REJECTED_REPLAY,
    REJECTED_EXPIRED,
    REJECTED_INSUFFICIENT_FUNDS
}
