package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.dto.LedgerEntryResponse;
import com.dhruv.offlinepayment_relay.dto.WalletResponse;
import com.dhruv.offlinepayment_relay.entity.PaymentPacket;
import com.dhruv.offlinepayment_relay.entity.SettlementLedgerEntry;
import com.dhruv.offlinepayment_relay.entity.Wallet;
import com.dhruv.offlinepayment_relay.exception.ResourceNotFoundException;
import com.dhruv.offlinepayment_relay.repository.PaymentPacketRepository;
import com.dhruv.offlinepayment_relay.repository.SettlementLedgerEntryRepository;
import com.dhruv.offlinepayment_relay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final PaymentPacketRepository packetRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;

    public WalletService(
            WalletRepository walletRepository,
            PaymentPacketRepository packetRepository,
            SettlementLedgerEntryRepository ledgerRepository
    ) {
        this.walletRepository = walletRepository;
        this.packetRepository = packetRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public WalletResponse getById(UUID id) {
        Wallet wallet = findOrThrow(id);
        return toResponse(wallet);
    }

    public List<LedgerEntryResponse> getLedger(UUID walletId) {
        Wallet wallet = findOrThrow(walletId);

        List<UUID> packetIds = packetRepository.findByReceiverDeviceId(wallet.getDeviceId()).stream()
                .map(PaymentPacket::getId)
                .toList();

        if (packetIds.isEmpty()) {
            return List.of();
        }

        return ledgerRepository.findByPacketIdInOrderBySettledAtAsc(packetIds).stream()
                .map(this::toLedgerResponse)
                .toList();
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

    private LedgerEntryResponse toLedgerResponse(SettlementLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getPacketId(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getSettledAt()
        );
    }
}
