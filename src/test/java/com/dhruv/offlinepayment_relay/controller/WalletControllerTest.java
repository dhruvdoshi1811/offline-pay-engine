package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.FundRequest;
import com.dhruv.offlinepayment_relay.dto.RegisterRequest;
import com.dhruv.offlinepayment_relay.entity.Role;
import com.dhruv.offlinepayment_relay.entity.User;
import com.dhruv.offlinepayment_relay.repository.UserRepository;
import com.dhruv.offlinepayment_relay.security.JwtService;
import com.dhruv.offlinepayment_relay.support.TestKeys;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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

    private String createDeviceAndGetWalletId(String userToken) throws Exception {
        DeviceRequest request = new DeviceRequest("wallet-owner-device", TestKeys.randomRsaPublicKeyBase64());
        String response = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("walletId").asText();
    }

    @Test
    void fundAsAdminIncreasesBalance() throws Exception {
        String userToken = registerUserAndGetToken("wallet-user@example.com");
        String walletId = createDeviceAndGetWalletId(userToken);
        String adminToken = adminToken("wallet-admin@example.com");

        FundRequest fundRequest = new FundRequest(new BigDecimal("50.00"));

        mockMvc.perform(post("/wallets/" + walletId + "/fund")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fundRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    void fundAsNonAdminIsForbidden() throws Exception {
        String userToken = registerUserAndGetToken("wallet-user-2@example.com");
        String walletId = createDeviceAndGetWalletId(userToken);

        FundRequest fundRequest = new FundRequest(new BigDecimal("50.00"));

        mockMvc.perform(post("/wallets/" + walletId + "/fund")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fundRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWalletReturnsBalance() throws Exception {
        String userToken = registerUserAndGetToken("wallet-user-3@example.com");
        String walletId = createDeviceAndGetWalletId(userToken);

        mockMvc.perform(get("/wallets/" + walletId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getUnknownWalletReturnsNotFound() throws Exception {
        String userToken = registerUserAndGetToken("wallet-user-4@example.com");

        mockMvc.perform(get("/wallets/" + UUID.randomUUID()).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }
}
