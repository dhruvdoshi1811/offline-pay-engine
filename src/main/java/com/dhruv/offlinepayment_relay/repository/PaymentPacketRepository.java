package com.dhruv.offlinepayment_relay.repository;

import com.dhruv.offlinepayment_relay.entity.PacketStatus;
import com.dhruv.offlinepayment_relay.entity.PaymentPacket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPacketRepository extends JpaRepository<PaymentPacket, UUID> {

    List<PaymentPacket> findBySenderDeviceIdOrReceiverDeviceId(UUID senderDeviceId, UUID receiverDeviceId);

    Optional<PaymentPacket> findByCiphertextHash(String ciphertextHash);

    List<PaymentPacket> findByReceiverDeviceId(UUID receiverDeviceId);

    List<PaymentPacket> findByStatus(PacketStatus status);
}
