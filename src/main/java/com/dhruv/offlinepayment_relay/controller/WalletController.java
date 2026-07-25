package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.dto.FundRequest;
import com.dhruv.offlinepayment_relay.dto.WalletResponse;
import com.dhruv.offlinepayment_relay.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(walletService.getById(id));
    }

    @PostMapping("/{id}/fund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WalletResponse> fund(@PathVariable UUID id, @Valid @RequestBody FundRequest request) {
        return ResponseEntity.ok(walletService.fund(id, request.amount()));
    }
}
