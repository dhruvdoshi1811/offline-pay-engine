package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.crypto.PacketCryptoService;
import com.dhruv.offlinepayment_relay.crypto.PacketPayload;
import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.RegisterRequest;
import com.dhruv.offlinepayment_relay.support.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PacketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PacketCryptoService cryptoService;

    private String registerUserAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private UUID registerDevice(String token, String ownerName) throws Exception {
        DeviceRequest request = new DeviceRequest(ownerName, TestKeys.randomRsaPublicKeyBase64());
        String response = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private PublicKey fetchServerPublicKey() throws Exception {
        String response = mockMvc.perform(get("/crypto/public-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String base64 = objectMapper.readTree(response).get("publicKey").asText();
        return TestKeys.decodeRsaPublicKey(base64);
    }

    private String encryptedRequestJson(UUID senderId, UUID receiverId, BigDecimal amount, PublicKey serverPublicKey,
                                         byte[] ciphertextOverride) throws Exception {
        byte[] plaintext = objectMapper.writeValueAsBytes(new PacketPayload(amount));
        PacketCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(serverPublicKey, plaintext);
        byte[] ciphertext = ciphertextOverride != null ? ciphertextOverride : encrypted.ciphertext();

        ObjectNode node = objectMapper.createObjectNode();
        node.put("senderDeviceId", senderId.toString());
        node.put("receiverDeviceId", receiverId.toString());
        node.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
        node.put("encryptedSessionKey", Base64.getEncoder().encodeToString(encrypted.encryptedSessionKey()));
        node.put("nonce", Base64.getEncoder().encodeToString(encrypted.nonce()));
        node.put("packetTimestamp", Instant.now().toString());
        return objectMapper.writeValueAsString(node);
    }

    @Test
    void relayWithValidPacketDecryptsAndPersists() throws Exception {
        String token = registerUserAndGetToken("packet-sender@example.com");
        UUID senderId = registerDevice(token, "sender-phone");
        UUID receiverId = registerDevice(token, "receiver-phone");
        PublicKey serverPublicKey = fetchServerPublicKey();

        String requestJson = encryptedRequestJson(senderId, receiverId, new BigDecimal("25.50"), serverPublicKey, null);

        String response = mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.decryptedAmount").value(25.50))
                .andExpect(jsonPath("$.ciphertextHash").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String packetId = json.get("id").asText();

        mockMvc.perform(get("/packets/" + packetId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.senderDeviceId").value(senderId.toString()));

        mockMvc.perform(get("/packets").param("deviceId", senderId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(packetId));
    }

    @Test
    void relayWithTamperedCiphertextReturnsBadRequest() throws Exception {
        String token = registerUserAndGetToken("packet-tamper@example.com");
        UUID senderId = registerDevice(token, "sender-phone-2");
        UUID receiverId = registerDevice(token, "receiver-phone-2");
        PublicKey serverPublicKey = fetchServerPublicKey();

        byte[] plaintext = objectMapper.writeValueAsBytes(new PacketPayload(new BigDecimal("10.00")));
        PacketCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(serverPublicKey, plaintext);
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[0] ^= 0x01;

        String requestJson = encryptedRequestJson(senderId, receiverId, new BigDecimal("10.00"), serverPublicKey, tampered);

        mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void relayWithUnknownDeviceReturnsNotFound() throws Exception {
        String token = registerUserAndGetToken("packet-unknown@example.com");
        UUID senderId = registerDevice(token, "sender-phone-3");
        PublicKey serverPublicKey = fetchServerPublicKey();

        String requestJson = encryptedRequestJson(senderId, UUID.randomUUID(), new BigDecimal("5.00"), serverPublicKey, null);

        mockMvc.perform(post("/packets/relay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void relayWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/packets/relay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
