package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.RegisterRequest;
import com.dhruv.offlinepayment_relay.support.TestKeys;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerUserAndGetToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123");
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void registerDeviceWithoutTokenReturnsUnauthorized() throws Exception {
        DeviceRequest request = new DeviceRequest("phone-1", TestKeys.randomRsaPublicKeyBase64());

        mockMvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerDeviceCreatesDeviceAndWallet() throws Exception {
        String token = registerUserAndGetToken("device-owner@example.com");
        DeviceRequest request = new DeviceRequest("phone-1", TestKeys.randomRsaPublicKeyBase64());

        mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerName").value("phone-1"))
                .andExpect(jsonPath("$.walletId").isNotEmpty());
    }

    @Test
    void registerDeviceWithInvalidPublicKeyReturnsBadRequest() throws Exception {
        String token = registerUserAndGetToken("bad-key-owner@example.com");
        DeviceRequest request = new DeviceRequest("phone-2", "not-a-valid-key");

        mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDeviceByIdReturnsDevice() throws Exception {
        String token = registerUserAndGetToken("getter@example.com");
        DeviceRequest request = new DeviceRequest("phone-3", TestKeys.randomRsaPublicKeyBase64());

        String response = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        String deviceId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/devices/" + deviceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("phone-3"));
    }

    @Test
    void getUnknownDeviceReturnsNotFound() throws Exception {
        String token = registerUserAndGetToken("notfound@example.com");

        mockMvc.perform(get("/devices/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listMineOnlyReturnsCallersOwnDevices() throws Exception {
        String tokenA = registerUserAndGetToken("owner-a@example.com");
        String tokenB = registerUserAndGetToken("owner-b@example.com");

        DeviceRequest deviceA = new DeviceRequest("device-a", TestKeys.randomRsaPublicKeyBase64());
        String responseA = mockMvc.perform(post("/devices")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceA)))
                .andReturn().getResponse().getContentAsString();
        String deviceAId = objectMapper.readTree(responseA).get("id").asText();

        DeviceRequest deviceB = new DeviceRequest("device-b", TestKeys.randomRsaPublicKeyBase64());
        mockMvc.perform(post("/devices")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deviceB)));

        mockMvc.perform(get("/devices/mine").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deviceAId))
                .andExpect(jsonPath("$[0].ownerName").value("device-a"));
    }
}
