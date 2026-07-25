package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.crypto.PacketCryptoService;
import com.dhruv.offlinepayment_relay.crypto.PacketPayload;
import com.dhruv.offlinepayment_relay.crypto.ServerKeyService;
import com.dhruv.offlinepayment_relay.dto.PathOutcome;
import com.dhruv.offlinepayment_relay.dto.RelayPacketRequest;
import com.dhruv.offlinepayment_relay.dto.RelayPacketResponse;
import com.dhruv.offlinepayment_relay.dto.SimulateDuplicateDeliveryRequest;
import com.dhruv.offlinepayment_relay.dto.SimulateDuplicateDeliveryResponse;
import com.dhruv.offlinepayment_relay.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DuplicateDeliveryDemoService {

    private static final int DEFAULT_CONCURRENT_PATHS = 3;

    private final PacketCryptoService cryptoService;
    private final ServerKeyService serverKeyService;
    private final ObjectMapper objectMapper;
    private final PaymentPacketService packetService;

    public DuplicateDeliveryDemoService(
            PacketCryptoService cryptoService,
            ServerKeyService serverKeyService,
            ObjectMapper objectMapper,
            PaymentPacketService packetService
    ) {
        this.cryptoService = cryptoService;
        this.serverKeyService = serverKeyService;
        this.objectMapper = objectMapper;
        this.packetService = packetService;
    }

    public SimulateDuplicateDeliveryResponse simulate(SimulateDuplicateDeliveryRequest request) {
        int concurrentPaths = request.concurrentPaths() != null ? request.concurrentPaths() : DEFAULT_CONCURRENT_PATHS;

        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(new PacketPayload(request.amount()));
            PacketCryptoService.EncryptedPayload encrypted =
                    cryptoService.encrypt(serverKeyService.getPublicKey(), plaintext);

            String ciphertext = Base64.getEncoder().encodeToString(encrypted.ciphertext());
            String encryptedSessionKey = Base64.getEncoder().encodeToString(encrypted.encryptedSessionKey());
            String nonce = Base64.getEncoder().encodeToString(encrypted.nonce());
            Instant packetTimestamp = Instant.now();

            AtomicReference<UUID> settledPacketId = new AtomicReference<>();
            ExecutorService executor = Executors.newFixedThreadPool(concurrentPaths);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<PathOutcome>> futures = new ArrayList<>();

            for (int i = 1; i <= concurrentPaths; i++) {
                String relayPathId = "sim-path-" + i;
                RelayPacketRequest relayRequest = new RelayPacketRequest(
                        request.senderDeviceId(),
                        request.receiverDeviceId(),
                        ciphertext,
                        encryptedSessionKey,
                        nonce,
                        packetTimestamp,
                        relayPathId
                );

                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        RelayPacketResponse response = packetService.relay(relayRequest);
                        settledPacketId.set(response.id());
                        return new PathOutcome(relayPathId, "SETTLED", response.status());
                    } catch (DuplicateResourceException ex) {
                        return new PathOutcome(relayPathId, "REJECTED_DUPLICATE", ex.getMessage());
                    } catch (Exception ex) {
                        return new PathOutcome(relayPathId, "ERROR", ex.getMessage());
                    }
                }));
            }

            startGate.countDown();

            List<PathOutcome> outcomes = new ArrayList<>();
            int settledCount = 0;
            int rejectedCount = 0;

            for (Future<PathOutcome> future : futures) {
                PathOutcome outcome = future.get();
                outcomes.add(outcome);
                if ("SETTLED".equals(outcome.status())) {
                    settledCount++;
                } else if ("REJECTED_DUPLICATE".equals(outcome.status())) {
                    rejectedCount++;
                }
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            return new SimulateDuplicateDeliveryResponse(
                    settledPacketId.get(), concurrentPaths, settledCount, rejectedCount, outcomes);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to simulate duplicate delivery", ex);
        }
    }
}
