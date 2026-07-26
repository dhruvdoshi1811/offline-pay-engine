package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.crypto.PacketCryptoService;
import com.dhruv.offlinepayment_relay.crypto.PacketPayload;
import com.dhruv.offlinepayment_relay.crypto.ServerKeyService;
import com.dhruv.offlinepayment_relay.dto.PacketResponse;
import com.dhruv.offlinepayment_relay.dto.RejectedPacketsResponse;
import com.dhruv.offlinepayment_relay.dto.RelayPacketRequest;
import com.dhruv.offlinepayment_relay.dto.RelayPacketResponse;
import com.dhruv.offlinepayment_relay.entity.PacketStatus;
import com.dhruv.offlinepayment_relay.entity.PaymentPacket;
import com.dhruv.offlinepayment_relay.exception.DuplicateResourceException;
import com.dhruv.offlinepayment_relay.exception.InsufficientFundsException;
import com.dhruv.offlinepayment_relay.exception.InvalidRequestException;
import com.dhruv.offlinepayment_relay.exception.ResourceNotFoundException;
import com.dhruv.offlinepayment_relay.repository.DeviceRepository;
import com.dhruv.offlinepayment_relay.repository.PaymentPacketRepository;
import com.dhruv.offlinepayment_relay.repository.RelayLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentPacketService {

    private final PaymentPacketRepository packetRepository;
    private final DeviceRepository deviceRepository;
    private final PacketCryptoService cryptoService;
    private final ServerKeyService serverKeyService;
    private final ObjectMapper objectMapper;
    private final PacketClaimService packetClaimService;
    private final SettlementService settlementService;
    private final RelayLogRepository relayLogRepository;
    private final Duration freshnessWindow;

    public PaymentPacketService(
            PaymentPacketRepository packetRepository,
            DeviceRepository deviceRepository,
            PacketCryptoService cryptoService,
            ServerKeyService serverKeyService,
            ObjectMapper objectMapper,
            PacketClaimService packetClaimService,
            SettlementService settlementService,
            RelayLogRepository relayLogRepository,
            @Value("${app.packet.freshness-window-minutes:5}") long freshnessWindowMinutes
    ) {
        this.packetRepository = packetRepository;
        this.deviceRepository = deviceRepository;
        this.cryptoService = cryptoService;
        this.serverKeyService = serverKeyService;
        this.objectMapper = objectMapper;
        this.packetClaimService = packetClaimService;
        this.settlementService = settlementService;
        this.relayLogRepository = relayLogRepository;
        this.freshnessWindow = Duration.ofMinutes(freshnessWindowMinutes);
    }

    public RelayPacketResponse relay(RelayPacketRequest request) {
        requireDevice(request.senderDeviceId());
        requireDevice(request.receiverDeviceId());

        byte[] ciphertextBytes = decodeBase64(request.ciphertext(), "ciphertext");
        byte[] encryptedSessionKeyBytes = decodeBase64(request.encryptedSessionKey(), "encryptedSessionKey");
        byte[] nonceBytes = decodeBase64(request.nonce(), "nonce");

        String ciphertextHash = HexFormat.of().formatHex(sha256(ciphertextBytes));

        PaymentPacket packet;
        try {
            packet = packetClaimService.claim(ciphertextHash, request);
        } catch (DataIntegrityViolationException ex) {
            PaymentPacket existing = packetClaimService.recordDuplicateDelivery(ciphertextHash, request.relayPathId());
            throw new DuplicateResourceException(
                    "packet already claimed (id " + existing.getId() + ", status " + existing.getStatus() + ")");
        }

        if (isStale(request.packetTimestamp())) {
            packetClaimService.rejectExpired(packet.getId());
            throw new InvalidRequestException("packet timestamp outside freshness window");
        }

        PacketPayload payload = decryptPayload(ciphertextBytes, encryptedSessionKeyBytes, nonceBytes);

        try {
            settlementService.settle(
                    packet.getId(), request.senderDeviceId(), request.receiverDeviceId(), payload.amount());
        } catch (InsufficientFundsException ex) {
            packetClaimService.rejectInsufficientFunds(packet.getId());
            throw new InvalidRequestException("sender wallet has insufficient balance");
        }

        return new RelayPacketResponse(
                packet.getId(),
                packet.getSenderDeviceId(),
                packet.getReceiverDeviceId(),
                packet.getCiphertextHash(),
                packet.getPacketTimestamp(),
                PacketStatus.SETTLED.name(),
                payload.amount()
        );
    }

    public PacketResponse getById(UUID id) {
        PaymentPacket packet = packetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("packet not found: " + id));
        return toResponse(packet);
    }

    public List<PacketResponse> listByDevice(UUID deviceId) {
        return packetRepository.findBySenderDeviceIdOrReceiverDeviceId(deviceId, deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public RejectedPacketsResponse getRejectedPackets() {
        List<PacketResponse> expired = packetRepository.findByStatus(PacketStatus.REJECTED_EXPIRED).stream()
                .map(this::toResponse)
                .toList();

        List<UUID> duplicateDeliveryIds = relayLogRepository.findPacketIdsWithMultipleDeliveries();
        List<PacketResponse> duplicateDeliveries = packetRepository.findAllById(duplicateDeliveryIds).stream()
                .map(this::toResponse)
                .toList();

        List<PacketResponse> insufficientFunds =
                packetRepository.findByStatus(PacketStatus.REJECTED_INSUFFICIENT_FUNDS).stream()
                        .map(this::toResponse)
                        .toList();

        return new RejectedPacketsResponse(expired, duplicateDeliveries, insufficientFunds);
    }

    private void requireDevice(UUID deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResourceNotFoundException("device not found: " + deviceId);
        }
    }

    private byte[] decodeBase64(String value, String fieldName) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(fieldName + " must be valid Base64");
        }
    }

    private boolean isStale(Instant packetTimestamp) {
        Duration diff = Duration.between(packetTimestamp, Instant.now()).abs();
        return diff.compareTo(freshnessWindow) > 0;
    }

    private PacketPayload decryptPayload(byte[] ciphertext, byte[] encryptedSessionKey, byte[] nonce) {
        try {
            byte[] plaintext = cryptoService.decrypt(serverKeyService.getPrivateKey(), ciphertext, encryptedSessionKey, nonce);
            PacketPayload payload = objectMapper.readValue(plaintext, PacketPayload.class);
            if (payload.amount() == null || payload.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidRequestException("decrypted amount must be positive");
            }
            return payload;
        } catch (GeneralSecurityException | JacksonException ex) {
            throw new InvalidRequestException("failed to decrypt packet payload");
        }
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private PacketResponse toResponse(PaymentPacket packet) {
        return new PacketResponse(
                packet.getId(),
                packet.getSenderDeviceId(),
                packet.getReceiverDeviceId(),
                packet.getCiphertextHash(),
                packet.getPacketTimestamp(),
                packet.getStatus().name()
        );
    }
}
