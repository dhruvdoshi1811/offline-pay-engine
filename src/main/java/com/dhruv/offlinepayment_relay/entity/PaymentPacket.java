package com.dhruv.offlinepayment_relay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_packets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PaymentPacket {

    @Id
    private UUID id;

    @Column(name = "sender_device_id", nullable = false)
    private UUID senderDeviceId;

    @Column(name = "receiver_device_id", nullable = false)
    private UUID receiverDeviceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String ciphertext;

    @Column(name = "ciphertext_hash", nullable = false, unique = true, length = 64)
    private String ciphertextHash;

    @Column(name = "encrypted_session_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedSessionKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String nonce;

    @Column(name = "packet_timestamp", nullable = false)
    private Instant packetTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PacketStatus status;
}
