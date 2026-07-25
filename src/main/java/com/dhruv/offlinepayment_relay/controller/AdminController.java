package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.RejectedPacketsResponse;
import com.dhruv.offlinepayment_relay.service.PaymentPacketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final PaymentPacketService packetService;

    public AdminController(PaymentPacketService packetService) {
        this.packetService = packetService;
    }

    @GetMapping("/rejected-packets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RejectedPacketsResponse> rejectedPackets() {
        return ResponseEntity.ok(packetService.getRejectedPackets());
    }
}
