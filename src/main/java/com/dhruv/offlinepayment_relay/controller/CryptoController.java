package com.dhruv.offlinepayment_relay.controller;

import com.dhruv.offlinepayment_relay.crypto.ServerKeyService;
import com.dhruv.offlinepayment_relay.dto.PublicKeyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequestMapping("/crypto")
public class CryptoController {

    private final ServerKeyService serverKeyService;

    public CryptoController(ServerKeyService serverKeyService) {
        this.serverKeyService = serverKeyService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> publicKey() {
        String encoded = Base64.getEncoder().encodeToString(serverKeyService.getPublicKey().getEncoded());
        return ResponseEntity.ok(new PublicKeyResponse(encoded));
    }
}
