package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.crypto.PacketCryptoService;
import com.dhruv.offlinepayment_relay.crypto.PacketPayload;
import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.RegisterRequest;
import com.dhruv.offlinepayment_relay.entity.Role;
import com.dhruv.offlinepayment_relay.entity.User;
import com.dhruv.offlinepayment_relay.repository.UserRepository;
import com.dhruv.offlinepayment_relay.security.JwtService;
import com.dhruv.offlinepayment_relay.support.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PacketClaimAndSettlementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PacketCryptoService cryptoService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private record DeviceRegistration(UUID deviceId, UUID walletId) {
    }

    private String registerUserAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String adminToken(String email) {
        User admin = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
        return jwtService.issueToken(admin.getEmail(), admin.getRole().name());
    }

    private DeviceRegistration registerDeviceFull(String token, String ownerName) throws Exception {
        DeviceRequest request = new DeviceRequest(ownerName, TestKeys.randomRsaPublicKeyBase64());
        String response = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new DeviceRegistration(UUID.fromString(json.get("id").asText()), UUID.fromString(json.get("walletId").asText()));
    }

    private PublicKey fetchServerPublicKey() throws Exception {
        String response = mockMvc.perform(get("/crypto/public-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String base64 = objectMapper.readTree(response).get("publicKey").asText();
        return TestKeys.decodeRsaPublicKey(base64);
    }

    private String encryptedRequestJson(UUID senderId, UUID receiverId, BigDecimal amount, PublicKey serverPublicKey,
                                         Instant packetTimestamp, String relayPathId) throws Exception {
        byte[] plaintext = objectMapper.writeValueAsBytes(new PacketPayload(amount));
        PacketCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(serverPublicKey, plaintext);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("senderDeviceId", senderId.toString());
        node.put("receiverDeviceId", receiverId.toString());
        node.put("ciphertext", Base64.getEncoder().encodeToString(encrypted.ciphertext()));
        node.put("encryptedSessionKey", Base64.getEncoder().encodeToString(encrypted.encryptedSessionKey()));
        node.put("nonce", Base64.getEncoder().encodeToString(encrypted.nonce()));
        node.put("packetTimestamp", packetTimestamp.toString());
        node.put("relayPathId", relayPathId);
        return objectMapper.writeValueAsString(node);
    }

    @Test
    void duplicateDeliveryViaDifferentPathIsRejectedAndSettlesOnlyOnce() throws Exception {
        String token = registerUserAndGetToken("claim-sender@example.com");
        UUID senderId = registerDeviceFull(token, "claim-sender-phone").deviceId();
        DeviceRegistration receiver = registerDeviceFull(token, "claim-receiver-phone");
        PublicKey serverPublicKey = fetchServerPublicKey();

        String requestJson = encryptedRequestJson(
                senderId, receiver.deviceId(), new BigDecimal("40.00"), serverPublicKey, Instant.now(), "path-A");

        mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        String duplicateJson = requestJson.replace("\"path-A\"", "\"path-B\"");

        mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/wallets/" + receiver.walletId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(40.00));

        String adminToken = adminToken("claim-admin@example.com");
        mockMvc.perform(get("/admin/rejected-packets").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateDeliveryPackets.length()").value(1));
    }

    @Test
    void staleTimestampIsRejectedAsExpired() throws Exception {
        String token = registerUserAndGetToken("claim-stale@example.com");
        UUID senderId = registerDeviceFull(token, "stale-sender-phone").deviceId();
        DeviceRegistration receiver = registerDeviceFull(token, "stale-receiver-phone");
        PublicKey serverPublicKey = fetchServerPublicKey();

        Instant staleTimestamp = Instant.now().minus(1, ChronoUnit.HOURS);
        String requestJson = encryptedRequestJson(
                senderId, receiver.deviceId(), new BigDecimal("15.00"), serverPublicKey, staleTimestamp, "path-A");

        String response = mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/wallets/" + receiver.walletId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));

        String adminToken = adminToken("claim-stale-admin@example.com");
        mockMvc.perform(get("/admin/rejected-packets").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredPackets.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void successfulSettlementAppearsInLedger() throws Exception {
        String token = registerUserAndGetToken("claim-ledger@example.com");
        UUID senderId = registerDeviceFull(token, "ledger-sender-phone").deviceId();
        DeviceRegistration receiver = registerDeviceFull(token, "ledger-receiver-phone");
        PublicKey serverPublicKey = fetchServerPublicKey();

        String requestJson = encryptedRequestJson(
                senderId, receiver.deviceId(), new BigDecimal("60.25"), serverPublicKey, Instant.now(), "path-A");

        mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/wallets/" + receiver.walletId() + "/ledger").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(60.25))
                .andExpect(jsonPath("$[0].balanceAfter").value(60.25));
    }

    @Test
    void rejectedPacketsRequiresAdminRole() throws Exception {
        String token = registerUserAndGetToken("claim-nonadmin@example.com");

        mockMvc.perform(get("/admin/rejected-packets").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
