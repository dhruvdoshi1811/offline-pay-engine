package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.DeviceRequest;
import com.dhruv.offlinepayment_relay.dto.DeviceResponse;
import com.dhruv.offlinepayment_relay.security.UserPrincipal;
import com.dhruv.offlinepayment_relay.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/devices")
    public ResponseEntity<DeviceResponse> register(
            @Valid @RequestBody DeviceRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.register(request, principal.getUser().getId()));
    }

    @GetMapping("/devices/mine")
    public ResponseEntity<List<DeviceResponse>> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(deviceService.listMine(principal.getUser().getId()));
    }

    @GetMapping("/devices/{id}")
    public ResponseEntity<DeviceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.getById(id));
    }
}
