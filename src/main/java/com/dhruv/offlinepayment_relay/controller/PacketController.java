package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.PacketResponse;
import com.dhruv.offlinepayment_relay.dto.RelayPacketRequest;
import com.dhruv.offlinepayment_relay.dto.RelayPacketResponse;
import com.dhruv.offlinepayment_relay.service.PaymentPacketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PacketController {

    private final PaymentPacketService packetService;

    public PacketController(PaymentPacketService packetService) {
        this.packetService = packetService;
    }

    @PostMapping("/packets/relay")
    public ResponseEntity<RelayPacketResponse> relay(@Valid @RequestBody RelayPacketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packetService.relay(request));
    }

    @GetMapping("/packets/{id}")
    public ResponseEntity<PacketResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(packetService.getById(id));
    }

    @GetMapping("/packets")
    public ResponseEntity<List<PacketResponse>> listByDevice(@RequestParam UUID deviceId) {
        return ResponseEntity.ok(packetService.listByDevice(deviceId));
    }
}
