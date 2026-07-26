package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.RegisterRequest;
import com.dhruv.offlinepayment_relay.entity.PacketStatus;
import com.dhruv.offlinepayment_relay.entity.PaymentPacket;
import com.dhruv.offlinepayment_relay.entity.RelayLog;
import com.dhruv.offlinepayment_relay.entity.Role;
import com.dhruv.offlinepayment_relay.entity.SettlementLedgerEntry;
import com.dhruv.offlinepayment_relay.entity.User;
import com.dhruv.offlinepayment_relay.entity.Wallet;
import com.dhruv.offlinepayment_relay.repository.PaymentPacketRepository;
import com.dhruv.offlinepayment_relay.repository.RelayLogRepository;
import com.dhruv.offlinepayment_relay.repository.SettlementLedgerEntryRepository;
import com.dhruv.offlinepayment_relay.repository.UserRepository;
import com.dhruv.offlinepayment_relay.repository.WalletRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentPacketRepository packetRepository;

    @Autowired
    private RelayLogRepository relayLogRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private SettlementLedgerEntryRepository ledgerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private record DeviceRegistration(UUID deviceId, UUID walletId) {
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

    private void fundWallet(UUID walletId, String amount) throws Exception {
        String token = adminToken("fund-admin-" + walletId + "@example.com");
        mockMvc.perform(post("/wallets/" + walletId + "/fund")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    private String registerUserAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private DeviceRegistration registerDeviceFull(String token, String ownerName) throws Exception {
        DeviceRequest request = new DeviceRequest(ownerName, TestKeys.randomRsaPublicKeyBase64());
        String response = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new DeviceRegistration(
                UUID.fromString(json.get("id").asText()), UUID.fromString(json.get("walletId").asText()));
    }

    @Test
    void concurrentDuplicateDeliverySettlesExactlyOnce() throws Exception {
        String token = registerUserAndGetToken("demo-sender@example.com");
        DeviceRegistration sender = registerDeviceFull(token, "demo-sender-phone");
        DeviceRegistration receiver = registerDeviceFull(token, "demo-receiver-phone");
        fundWallet(sender.walletId(), "100.00");

        int concurrentPaths = 5;
        String requestJson = String.format(
                "{\"senderDeviceId\":\"%s\",\"receiverDeviceId\":\"%s\",\"amount\":20.00,\"concurrentPaths\":%d}",
                sender.deviceId(), receiver.deviceId(), concurrentPaths);

        String response = mockMvc.perform(post("/demo/simulate-duplicate-delivery")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settledCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(concurrentPaths - 1))
                .andExpect(jsonPath("$.totalPaths").value(concurrentPaths))
                .andExpect(jsonPath("$.outcomes.length()").value(concurrentPaths))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        UUID packetId = UUID.fromString(json.get("packetId").asText());

        PaymentPacket packet = packetRepository.findById(packetId).orElseThrow();
        assertEquals(PacketStatus.SETTLED, packet.getStatus());

        List<RelayLog> logs = relayLogRepository.findByPacketId(packetId);
        assertEquals(concurrentPaths, logs.size());

        Wallet wallet = walletRepository.findById(receiver.walletId()).orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(wallet.getBalance()));

        Wallet senderWallet = walletRepository.findById(sender.walletId()).orElseThrow();
        assertEquals(0, new BigDecimal("80.00").compareTo(senderWallet.getBalance()));

        List<SettlementLedgerEntry> ledgerEntries =
                ledgerRepository.findByPacketIdInOrderBySettledAtAsc(List.of(packetId));
        assertEquals(1, ledgerEntries.size());
    }
}
