package com.dhruv.offlinepayment_relay.service;

import com.dhruv.offlinepayment_relay.entity.PacketStatus;
import com.dhruv.offlinepayment_relay.entity.PaymentPacket;
import com.dhruv.offlinepayment_relay.entity.SettlementLedgerEntry;
import com.dhruv.offlinepayment_relay.entity.Wallet;
import com.dhruv.offlinepayment_relay.exception.InsufficientFundsException;
import com.dhruv.offlinepayment_relay.exception.ResourceNotFoundException;
import com.dhruv.offlinepayment_relay.repository.PaymentPacketRepository;
import com.dhruv.offlinepayment_relay.repository.SettlementLedgerEntryRepository;
import com.dhruv.offlinepayment_relay.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class SettlementService {

    private final WalletRepository walletRepository;
    private final SettlementLedgerEntryRepository ledgerRepository;
    private final PaymentPacketRepository packetRepository;

    public SettlementService(
            WalletRepository walletRepository,
            SettlementLedgerEntryRepository ledgerRepository,
            PaymentPacketRepository packetRepository
    ) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.packetRepository = packetRepository;
    }

    @Transactional
    public void settle(UUID packetId, UUID senderDeviceId, UUID receiverDeviceId, BigDecimal amount) {
        Wallet senderWallet = walletRepository.findByDeviceId(senderDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("wallet not found for device: " + senderDeviceId));
        Wallet receiverWallet = walletRepository.findByDeviceId(receiverDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("wallet not found for device: " + receiverDeviceId));

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("sender wallet has insufficient balance");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        walletRepository.save(senderWallet);

        receiverWallet.setBalance(receiverWallet.getBalance().add(amount));
        walletRepository.save(receiverWallet);

        SettlementLedgerEntry entry = SettlementLedgerEntry.builder()
                .id(UUID.randomUUID())
                .packetId(packetId)
                .amount(amount)
                .balanceAfter(receiverWallet.getBalance())
                .settledAt(Instant.now())
                .build();
        ledgerRepository.save(entry);

        PaymentPacket packet = packetRepository.findById(packetId)
                .orElseThrow(() -> new ResourceNotFoundException("packet not found: " + packetId));
        packet.setStatus(PacketStatus.SETTLED);
        packetRepository.save(packet);
    }
}
