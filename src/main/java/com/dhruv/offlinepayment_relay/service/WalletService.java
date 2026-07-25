package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.dto.WalletResponse;
import com.dhruv.offlinepayment_relay.entity.Wallet;
import com.dhruv.offlinepayment_relay.exception.ResourceNotFoundException;
import com.dhruv.offlinepayment_relay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public WalletResponse getById(UUID id) {
        Wallet wallet = findOrThrow(id);
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse fund(UUID id, BigDecimal amount) {
        Wallet wallet = findOrThrow(id);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
        return toResponse(wallet);
    }

    private Wallet findOrThrow(UUID id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("wallet not found: " + id));
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getDeviceId(), wallet.getBalance());
    }
}
