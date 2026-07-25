package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.dto.RelayPacketRequest;
import com.dhruv.offlinepayment_relay.entity.PacketStatus;
import com.dhruv.offlinepayment_relay.entity.PaymentPacket;
import com.dhruv.offlinepayment_relay.entity.RelayLog;
import com.dhruv.offlinepayment_relay.exception.ResourceNotFoundException;
import com.dhruv.offlinepayment_relay.repository.PaymentPacketRepository;
import com.dhruv.offlinepayment_relay.repository.RelayLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PacketClaimService {

    private final PaymentPacketRepository packetRepository;
    private final RelayLogRepository relayLogRepository;

    public PacketClaimService(PaymentPacketRepository packetRepository, RelayLogRepository relayLogRepository) {
        this.packetRepository = packetRepository;
        this.relayLogRepository = relayLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentPacket claim(String ciphertextHash, RelayPacketRequest request) {
        PaymentPacket packet = PaymentPacket.builder()
                .id(UUID.randomUUID())
                .senderDeviceId(request.senderDeviceId())
                .receiverDeviceId(request.receiverDeviceId())
                .ciphertext(request.ciphertext())
                .ciphertextHash(ciphertextHash)
                .encryptedSessionKey(request.encryptedSessionKey())
                .nonce(request.nonce())
                .packetTimestamp(request.packetTimestamp())
                .status(PacketStatus.CLAIMED)
                .build();

        packetRepository.saveAndFlush(packet);
        logDelivery(packet.getId(), request.relayPathId());
        return packet;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentPacket recordDuplicateDelivery(String ciphertextHash, String relayPathId) {
        PaymentPacket existing = packetRepository.findByCiphertextHash(ciphertextHash)
                .orElseThrow(() -> new IllegalStateException(
                        "claim conflict but no existing packet found for hash " + ciphertextHash));
        logDelivery(existing.getId(), relayPathId);
        return existing;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectExpired(UUID packetId) {
        PaymentPacket packet = packetRepository.findById(packetId)
                .orElseThrow(() -> new ResourceNotFoundException("packet not found: " + packetId));
        packet.setStatus(PacketStatus.REJECTED_EXPIRED);
        packetRepository.save(packet);
    }

    private void logDelivery(UUID packetId, String relayPathId) {
        relayLogRepository.save(RelayLog.builder()
                .id(UUID.randomUUID())
                .packetId(packetId)
                .relayPathId(relayPathId)
                .receivedAt(Instant.now())
                .build());
    }
}
